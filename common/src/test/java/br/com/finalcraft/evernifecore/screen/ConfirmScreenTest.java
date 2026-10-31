package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The core's yes/no screen answers through the chain, seeds its layout, takes a shift-click when dangerous and pays a gate. */
class ConfirmScreenTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender alex;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        world.close();
    }

    private ScreenView openView() {
        ScreenView view = Screens.getOpenView(alex);
        assertNotNull(view, "the confirmation is open");
        return view;
    }

    private Cost gold(boolean affordable) {
        return new Cost() {
            @Override
            public ConditionResult test(ExecutionContext ctx) {
                return affordable ? ConditionResult.pass() : ConditionResult.fail(FancyText.of("not enough gold"));
            }

            @Override
            public boolean take(ExecutionContext ctx) {
                log.add("take gold");
                return true;
            }

            @Override
            public void refund(ExecutionContext ctx) {
                log.add("refund gold");
            }

            @Override
            public FancyText describe(ExecutionContext ctx) {
                return FancyText.of("50 gold");
            }
        };
    }

    @Test
    void confirmingAnswersTrueAndTheWindowGoes() {
        CompletableFuture<Boolean> answer = Screens.confirm(alex, ConfirmRequest.of("Sell it?"));
        ScreenView view = openView();
        assertEquals("Sell it?", view.getCurrentTitle());

        world.click(view, "CONFIRM", ClickGesture.PRIMARY);
        world.advanceTicks(2);

        assertEquals(Boolean.TRUE, answer.getNow(null));
        assertEquals(0, Screens.getOpenCount());
    }

    @Test
    void denyingAnswersFalse() {
        CompletableFuture<Boolean> answer = Screens.confirm(alex, ConfirmRequest.of("Sell it?"));

        world.click(openView(), "DENY", ClickGesture.PRIMARY);
        world.advanceTicks(2);

        assertEquals(Boolean.FALSE, answer.getNow(null));
    }

    @Test
    void walkingAwayCancelsTheAnswer() {
        CompletableFuture<Boolean> answer = Screens.confirm(alex, ConfirmRequest.of("Sell it?"));
        ScreenView view = openView();

        world.getRenderer().surfaceOf(view).playerCloses();
        world.advanceTicks(2);

        assertTrue(answer.isCancelled(), "nobody answered, so nothing may run on an answer: " + answer);
    }

    @Test
    void openedFromAScreenTheTypedAnswerComesBackAndAWalkAwayStaysCancelled() {
        List<CompletableFuture<Boolean>> answers = new ArrayList<>();
        Screen<?> shop = Screen.of(3).debounce(0);
        shop.icon(13, Icon.of("minecraft:emerald")).id("sell")
                .onClick(click -> answers.add(click.open(ConfirmScreen.of(ConfirmRequest.of("Sell it?")), Boolean.class)));
        ScreenView view = world.open(shop, alex);

        world.click(view, "sell", ClickGesture.PRIMARY);
        world.advanceTicks(2);
        world.click(openView(), "CONFIRM", ClickGesture.PRIMARY);
        world.advanceTicks(2);
        assertEquals(Boolean.TRUE, answers.get(0).getNow(null));
        assertTrue(Screens.getOpenView(alex) == view, "the shop is back");

        world.click(view, "sell", ClickGesture.PRIMARY);
        world.advanceTicks(2);
        world.getRenderer().surfaceOf(openView()).playerCloses();
        world.advanceTicks(2);
        assertTrue(answers.get(1).isCancelled(), "nobody answered, which is not an error: " + answers.get(1));
    }

    @Test
    void aDangerousOneTakesAShiftClick() {
        //no debounce: the test clicks twice within the 200 ms a human double click is swallowed in
        ScreenView view = world.open(ConfirmScreen.of(ConfirmRequest.of("Delete the island?").dangerous()).debounce(0), alex);
        CompletableFuture<Boolean> answer = Screens.answerOf(CompletableFuture.completedFuture(view), Boolean.class);

        world.click(view, "CONFIRM", ClickGesture.PRIMARY);
        world.advanceTicks(2);
        assertFalse(answer.isDone(), "a plain click is not enough");

        world.click(view, "CONFIRM", ClickGesture.SHIFT_PRIMARY);
        world.advanceTicks(2);
        assertEquals(Boolean.TRUE, answer.getNow(null));
    }

    @Test
    void theLayoutIsSeededAndTheDisplayItemSitsInTheMiddle() {
        CompletableFuture<Boolean> answer = Screens.confirm(alex, ConfirmRequest.of("Sell it?").display(ItemSpec.of("minecraft:diamond", 1)));
        ScreenView view = openView();

        assertTrue(new File(world.getOwner().getMetaInfo().getDataFolder(), "guis/ConfirmLayout.yml").isFile());
        assertEquals("minecraft:diamond", world.getRenderer().surfaceOf(view).getSlot(13).getItem().getMaterialId());
        view.close();
        world.advanceTicks(2);
        assertTrue(answer.isCancelled());
    }

    @Test
    void aGatedConfirmationPaysOnConfirmAndItsLoreReadsTheCost() {
        Gate gate = Gate.builder().cost(gold(true)).build();
        CompletableFuture<Boolean> answer = Screens.confirm(alex, ConfirmRequest.of("Buy it?", gate, ctx -> log.add("bought")));
        ScreenView view = openView();
        //the lore is the admin's to write; the screen answers {cost} wherever it appears
        assertEquals("§7Costs 50 gold", view.getScreen().getReplacer().apply("§7Costs {cost}"));

        world.click(view, "CONFIRM", ClickGesture.PRIMARY);
        world.advanceTicks(2);

        assertEquals("[take gold, bought]", log.toString());
        assertEquals(Boolean.TRUE, answer.getNow(null));
    }

    @Test
    void aGateNotMetKeepsTheScreenOpenAndChargesNothing() {
        Gate gate = Gate.builder().cost(gold(false)).build();
        CompletableFuture<Boolean> answer = Screens.confirm(alex, ConfirmRequest.of("Buy it?", gate, ctx -> log.add("bought")));
        ScreenView view = openView();

        world.click(view, "CONFIRM", ClickGesture.PRIMARY);
        world.advanceTicks(2);

        assertTrue(log.isEmpty(), log.toString());
        assertFalse(answer.isDone());
        assertTrue(Screens.getOpenView(alex) == view, "the screen stays");
        view.close();
        world.advanceTicks(2);
    }

}
