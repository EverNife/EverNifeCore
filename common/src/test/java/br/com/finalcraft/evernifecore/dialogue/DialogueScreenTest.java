package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.audit.GateCheckEvent;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ScreenKey;
import br.com.finalcraft.evernifecore.testing.DialogueTrees;
import br.com.finalcraft.evernifecore.testing.DialogueTrees.DrawnChoice;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The core's default dialogue screen: one choice per visible option, greyed with every reason, clicked into the session. */
class DialogueScreenTest {

    @TempDirNobodyCleans
    Path tempDir;

    private DialogueFixture world;

    @BeforeEach
    void open() {
        world = DialogueFixture.open(tempDir);
    }

    @AfterEach
    void close() {
        world.close();
    }

    private Dialogue shop(Gate buy, DialogueOption.Builder... extra) {
        DialogueNode.Builder start = DialogueNode.builder("start").text("What will it be?")
                .option(DialogueOption.builder("buy").text("The sword").gate(buy).goTo("sold"))
                .option(DialogueOption.builder("leave").text("Nothing").end());
        for (DialogueOption.Builder option : extra) {
            start.option(option);
        }
        return Dialogue.builder(world.owner(), "shop").speaker("Smith")
                .node(start)
                .node(DialogueNode.builder("sold").text("Enjoy it.").option(DialogueOption.builder("bye").text("Bye").end()))
                .build();
    }

    private static List<String> ids(List<DrawnChoice> drawn) {
        return drawn.stream().map(DrawnChoice::getOptionId).collect(Collectors.toList());
    }

    @Test
    void itDrawsOneChoicePerOptionUnderTheSpeakersTitle() {
        List<DrawnChoice> drawn = DialogueTrees.openDialogueScreen(world.screens, world.alex, shop(Gate.open()));

        assertEquals(Arrays.asList("buy", "leave"), ids(drawn));
        assertTrue(drawn.get(0).isEnabled());
        assertEquals("§aThe sword", drawn.get(0).getName());
        assertEquals("Smith", world.view().getCurrentTitle());
        assertEquals(ScreenKey.DIALOGUE, world.view().getScreen().getKey());
    }

    @Test
    void anOptionWhoseGateFailsIsGreyedCarryingEveryFailure() {
        Gate twoReasons = Gate.of(Arrays.<Condition>asList(new DialogueFixture.Toggle(false, "Level 10 needed"),
                new DialogueFixture.Toggle(false, "Talk to the guard first")), Collections.<Cost>emptyList());

        DrawnChoice buy = DialogueTrees.openDialogueScreen(world.screens, world.alex, shop(twoReasons)).get(0);

        assertFalse(buy.isEnabled());
        assertEquals("§7The sword", buy.getName());
        assertEquals(Arrays.asList("Level 10 needed", "Talk to the guard first"), Arrays.asList(buy.getReason().split("\n")),
                "every reason, one per line, in the reason the renderer draws - never baked into the lore twice");
        assertTrue(buy.getLore().isEmpty(), buy.getLore().toString());
    }

    @Test
    void aHiddenOptionIsNotDrawnWhileItsGateFails() {
        DialogueFixture.Toggle secret = new DialogueFixture.Toggle(false, "You do not know the password");
        Dialogue shop = shop(Gate.open(), DialogueOption.builder("password").text("Swordfish").hidden()
                .gate(Gate.of(Collections.<Condition>singletonList(secret), Collections.<Cost>emptyList())).end());

        assertEquals(Arrays.asList("buy", "leave"), ids(DialogueTrees.openDialogueScreen(world.screens, world.alex, shop)));

        secret.passes = true;
        Dialogues.sessionOf(world.alex).refresh();
        world.screens.advanceTicks(1);
        assertEquals(Arrays.asList("buy", "leave", "password"), ids(DialogueTrees.drawn(world.screens, world.alex)));
    }

    @Test
    void eachRedrawAsksEachOptionsGateOnceHiddenOrNot() {
        DialogueFixture.Toggle secret = new DialogueFixture.Toggle(false, "You do not know the password");
        Dialogue shop = shop(Gate.open(), DialogueOption.builder("password").text("Swordfish").hidden()
                .gate(Gate.of(Collections.<Condition>singletonList(secret), Collections.<Cost>emptyList())).end());
        List<GateCheckEvent> checks = world.colonies.recorded(GateCheckEvent.class);

        DialogueTrees.openDialogueScreen(world.screens, world.alex, shop);
        int opened = checks.size();
        Dialogues.sessionOf(world.alex).refresh();
        world.screens.advanceTicks(1);

        assertEquals(3, opened, "three options, three checks - the hidden one too, which the check hides: " + checks);
        assertEquals(3, checks.size() - opened, "and a redraw asks each of them once again: " + checks);
    }

    @Test
    void closingTheScreenEndsTheSessionAsClosed() {
        DialogueTrees.openDialogueScreen(world.screens, world.alex, shop(Gate.open()));
        DialogueSession session = Dialogues.sessionOf(world.alex);

        world.surface().playerCloses();

        assertEquals(DialogueEndCause.CLOSED, session.getEndCause());
    }

    @Test
    void clickingAChoicePaysItOnceAndShowsTheNextNode() {
        DialogueFixture.Purse purse = new DialogueFixture.Purse(10, 4);
        DialogueTrees.openDialogueScreen(world.screens, world.alex, shop(Gate.of(Collections.<Condition>emptyList(),
                Collections.<Cost>singletonList(purse))));
        DialogueSession session = Dialogues.sessionOf(world.alex);

        world.screens.click(world.view(), "option:buy", ClickGesture.PRIMARY);
        world.screens.advanceTicks(1);

        assertEquals(1, purse.takes, "the session pays the gate; the choice itself does not pay it again");
        assertEquals("sold", session.getNode().getId());
        assertTrue(session.isOpen(), "the screen of the old node going away is not the player closing the talk");
        assertEquals(Collections.singletonList("bye"), ids(DialogueTrees.drawn(world.screens, world.alex)));
    }
}
