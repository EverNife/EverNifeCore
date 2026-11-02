package br.com.finalcraft.evernifecore.minecraft.screen.prompt;

import br.com.finalcraft.evernifecore.minecraft.chat.ChatExpectationListener;
import br.com.finalcraft.evernifecore.minecraft.chat.ExpectedChat;
import br.com.finalcraft.evernifecore.minecraft.screen.ChestItems;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.ClickSimulator;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.PlayerDouble;
import br.com.finalcraft.evernifecore.testing.minecraft.screen.SurfaceDouble;
import br.com.finalcraft.evernifecore.screen.ClickContext;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.MutableState;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenNavigation;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.prompt.PromptRegistry;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import br.com.finalcraft.evernifecore.screen.prompt.PromptResult;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asking the player to type an answer, and each of the four ways that ends.
 *
 * <p>Every test here opens a chain - a menu, and a screen opened from it - and asks the question on the
 * deeper one. That is deliberate: what a question owes is not only the value, it is the screen given
 * back afterwards with the chain, the page and the state it had. A prompt that reopened the screen
 * instead of reviving it would answer correctly and still lose the player's place, so the count in slot
 * 0 and the step back to the menu are asserted alongside the answer.</p>
 *
 * <p>The chat message arrives off the main thread, because that is where a server raises it - see
 * {@code ScreenEventBus.typeInChat}. Anything the framework then does to a screen has to hop back first,
 * and {@link #theAnswerArrivesOnTheMainThreadWithTheScreenBackAsItWas()} asserts the hop rather than
 * assuming it: the value alone would read the same either way.</p>
 */
class ChatPromptTest {

    @TempDirNobodyCleans
    Path tempDir;

    /** One tick of the screen's clock - the shortest wait there is. */
    private static final Duration SOON = Duration.ofMillis(50L);

    private BukkitScreenWorld world;
    private PlayerDouble player;
    private ClickSimulator clicks;

    /** The deeper screen's own counter, drawn in slot 0 as the size of the stack. */
    private MutableState<Integer> counter;
    /** What the menu is waiting for the deeper screen to answer with. */
    private CompletableFuture<Object> stepIntoTheDeeperScreen;

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.install(tempDir);
        player = world.newPlayer("Steve");
        clicks = world.getClicks();
    }

    @AfterEach
    void teardown() {
        //a chat wait left behind would hold the Player into the next test
        for (ExpectedChat outstanding : waitsOn()) {
            outstanding.cancel();
        }
        if (world != null) {
            world.close();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The screen a question is asked from
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Opens a menu, steps into a second screen from it, and hands back that second screen - the one
     * {@code ask} will be asked from, on slot 1. Slot 2 of it steps back out to the menu.
     */
    private ScreenView openTheScreenThatAsks(Consumer<ClickContext> ask) {
        Screen<Layout> deeper = Screen.of(3).title("Auction").debounce(0);
        deeper.addComponent(component -> {
            counter = component.remember(1);
            component.render(slots -> slots.icon(0, iconOf(new ItemStack(Material.IRON_INGOT, counter.get()))));
        });
        deeper.icon(1, iconOf(new ItemStack(Material.ARROW))).onClick(ask);
        deeper.icon(2, iconOf(new ItemStack(Material.BARRIER))).onClick(context -> context.back("left the auction"));

        Screen<Layout> menu = Screen.of(3).title("Menu").debounce(0);
        menu.icon(1, iconOf(new ItemStack(Material.ARROW)))
                .onClick(context -> stepIntoTheDeeperScreen = context.open(deeper));
        world.open(menu, player);
        clicks.leftClick(player, 1);
        world.advanceTicks(1); //the step into the deeper screen lands one tick after the click
        return Screens.getOpenView(player.getUniqueId());
    }

    private static Icon iconOf(ItemStack stack) {
        return Icon.of(ChestItems.specOf(stack));
    }

    /** Clicks the asking button and lets the tick pass that the question is armed on. */
    private void askTheQuestion() {
        clicks.leftClick(player, 1);
        world.advanceTicks(1);
    }

    private List<ExpectedChat> waitsOn() {
        return new ArrayList<>(ChatExpectationListener.get().getChatListeners().get(player.getUniqueId()));
    }

    /** Every container the framework has asked the server for, which a screen coming back adds one to. */
    private int containersOpened() {
        return world.getCreatedSurfaces().size();
    }

    private ItemStack onScreen() {
        return world.getSurface().getItem(0);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The answer
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aScreenSetAsideClosesItsChestByItselfAndAScreenOpenedOnTopKeepsItsWindow() {
        Screen<Layout> alone = Screen.of(3).title("Alone");
        ScreenView view = world.open(alone, player);
        assertNotNull(player.getOpenView());

        assertTrue(ScreenNavigation.suspend(view));
        assertTrue(clicks.leftClick(player, 0).isCancelled(), "for the tick the chest outlives its view, nothing in it is loot");
        world.advanceTicks(1);

        assertNull(player.getOpenView(), "the surface closes the chest of a view set aside - no channel had to");

        ScreenView deeper = openTheScreenThatAsks(context -> { });
        assertNotNull(player.getOpenView(), "a screen opened on top of a set-aside one keeps the window it took");
        world.advanceTicks(2);
        assertSame(deeper, Screens.getOpenView(player.getUniqueId()));
        assertNotNull(player.getOpenView(), "the set-aside one's close is only for a window still showing it");
    }

    @Test
    void theAnswerArrivesOnTheMainThreadWithTheScreenBackAsItWas() {
        List<Double> prices = new ArrayList<>();
        AtomicReference<Thread> answeredOn = new AtomicReference<>();
        List<Boolean> answeredAsMainThread = new ArrayList<>();
        ScreenView view = openTheScreenThatAsks(context -> context
                .ask(PromptSpec.of("§eType the new price:").parse(Double::parseDouble))
                .thenAccept(price -> {
                    answeredOn.set(Thread.currentThread());
                    answeredAsMainThread.add(Bukkit.getServer().isPrimaryThread());
                    prices.add(price);
                }));
        counter.set(7);
        world.advanceTicks(1);
        SurfaceDouble setAside = world.getSurface();

        askTheQuestion();

        assertTrue(view.isSuspended(), "the screen is set aside while the question is out");
        assertTrue(player.getMessages().contains("§eType the new price:"), player.getMessages().toString());
        assertEquals(1, waitsOn().size());

        AsyncPlayerChatEvent typed = world.getEvents().typeInChat(player.asPlayer(), "12.5");
        world.advanceTicks(1);

        assertEquals(Arrays.asList(12.5D), prices);
        assertSame(Thread.currentThread(), answeredOn.get(), "the chat event was raised on a thread of its "
                + "own; a value completed there would run everything downstream of it off the main thread");
        assertEquals(Arrays.asList(Boolean.TRUE), answeredAsMainThread);
        assertTrue(typed.isCancelled(), "the answer was aimed at the question, so it never reaches chat");
        assertTrue(waitsOn().isEmpty(), "and nothing is waiting on chat any more");

        assertFalse(view.isSuspended(), "the screen comes back");
        assertSame(view, Screens.getOpenView(player.getUniqueId()), "the very screen that asked, not a new one");
        assertNotSame(setAside, world.getSurface());
        assertEquals(Material.IRON_INGOT, onScreen().getType());
        assertEquals(7, onScreen().getAmount(), "on the count it was left on");

        clicks.leftClick(player, 2);
        world.advanceTicks(1); //the step back out is a one-tick hop too
        assertEquals("left the auction", stepIntoTheDeeperScreen.getNow(null),
                "and the chain survived the question, so the way out still leads to the menu");
    }

    @Test
    void anAnswerThatDoesNotParseIsPutAgainWithoutReachingPublicChat() {
        List<Integer> amounts = new ArrayList<>();
        ScreenView view = openTheScreenThatAsks(context -> context
                .ask(PromptSpec.of("§eHow many?").parse(input -> {
                    if (!input.matches("[0-9]+")) {
                        throw new IllegalArgumentException("§cThat is not a number.");
                    }
                    return Integer.valueOf(input);
                }))
                .thenAccept(amounts::add));
        askTheQuestion();
        ExpectedChat waiting = waitsOn().get(0);
        int containersBefore = containersOpened();

        AsyncPlayerChatEvent refused = world.getEvents().typeInChat(player.asPlayer(), "banana");
        world.advanceTicks(1);

        assertTrue(refused.isCancelled(), "an attempt at the question is not small talk, so it is swallowed");
        assertTrue(amounts.isEmpty(), "and it answered nothing");
        assertEquals(Arrays.asList("§eHow many?", "§cThat is not a number.", "§eHow many?"),
                player.getMessages(), "the player reads why, and then the question again");
        assertTrue(view.isSuspended(), "the screen stays set aside - the question is still out");
        assertEquals(containersBefore, containersOpened(), "so nothing was given back yet");
        assertEquals(Arrays.asList(waiting), waitsOn(), "the same wait, not a second one: asking again must "
                + "not restart the clock, or a player typing nonsense would never time out");

        world.getEvents().typeInChat(player.asPlayer(), "5");
        world.advanceTicks(1);

        assertEquals(Arrays.asList(Integer.valueOf(5)), amounts);
        assertEquals(containersBefore + 1, containersOpened(), "and now the screen is back");

        AsyncPlayerChatEvent smallTalk = world.getEvents().typeInChat(player.asPlayer(), "hello everyone");

        assertFalse(smallTalk.isCancelled(), "with no question out, what the player types is the server's "
                + "business - which is what makes the two swallowed lines above real refusals rather than a "
                + "listener that eats every message");
    }

    @Test
    void theCancelWordCallsTheQuestionOffAndGivesTheScreenBack() {
        AtomicInteger timedOut = new AtomicInteger();
        List<CompletableFuture<PromptResult<String>>> outcomes = new ArrayList<>();
        ScreenView view = openTheScreenThatAsks(context -> outcomes.add(PromptRegistry.get()
                .ask(context.getView(), PromptSpec.of("§eName it, or 'cancel':")
                        .cancelWord("cancel")
                        .onTimeout(screen -> timedOut.incrementAndGet()))));
        counter.set(4);
        world.advanceTicks(1);
        askTheQuestion();
        int containersBefore = containersOpened();

        AsyncPlayerChatEvent said = world.getEvents().typeInChat(player.asPlayer(), "CANCEL");
        world.advanceTicks(1);

        PromptResult<String> outcome = outcomes.get(0).getNow(null);
        assertEquals(PromptResult.Kind.CANCELLED, outcome.getKind(), "the word matches whatever the case");
        assertEquals(0, timedOut.get(), "calling a question off is not running out of time");
        assertTrue(said.isCancelled(), "the cancel word belongs to the question, not to chat");
        assertTrue(waitsOn().isEmpty());
        assertFalse(view.isSuspended());
        assertEquals(containersBefore + 1, containersOpened(), "the screen is given back all the same");
        assertEquals(4, onScreen().getAmount(), "with everything it was holding");
    }

    @Test
    void aQuestionNobodyAnsweredRunsOutAndGivesTheScreenBack() {
        List<ScreenView> timedOutOn = new ArrayList<>();
        List<CompletableFuture<PromptResult<String>>> outcomes = new ArrayList<>();
        ScreenView view = openTheScreenThatAsks(context -> outcomes.add(PromptRegistry.get()
                .ask(context.getView(), PromptSpec.of("§eName it:")
                        .timeout(SOON)
                        .onTimeout(timedOutOn::add))));
        counter.set(3);
        world.advanceTicks(1);
        askTheQuestion();
        int containersBefore = containersOpened();
        assertEquals(1, waitsOn().size(), "there is a question outstanding for the clock to run out on");

        //the registry times the question on the screen's clock: one tick for SOON
        world.advanceTicks(1);
        PromptResult<String> outcome = outcomes.get(0).getNow(null);

        assertEquals(PromptResult.Kind.TIMEOUT, outcome.getKind());
        assertEquals(Arrays.asList(view), timedOutOn, "the handler is told which screen ran out");
        assertFalse(view.isSuspended());
        assertEquals(containersBefore + 1, containersOpened());
        assertEquals(3, onScreen().getAmount(), "a screen nobody answered for is still the screen they left");
    }

    @Test
    void aQuestionThatRanOutStopsTheCoreWaitingForAnAnswer() {
        List<CompletableFuture<PromptResult<String>>> outcomes = new ArrayList<>();
        openTheScreenThatAsks(context -> outcomes.add(PromptRegistry.get()
                .ask(context.getView(), PromptSpec.of("§eName it:").timeout(SOON))));
        askTheQuestion();

        world.advanceTicks(1);
        assertEquals(PromptResult.Kind.TIMEOUT, outcomes.get(0).getNow(null).getKind());

        assertFalse(ChatExpectationListener.get().hasAnyExpectedChat(player.asPlayer()),
                "a wait the clock ended and nobody told the listener about would hold the Player and "
                        + "swallow the next thing they type");
        assertTrue(waitsOn().isEmpty());

        AsyncPlayerChatEvent smallTalk = world.getEvents().typeInChat(player.asPlayer(), "hello everyone");

        assertFalse(smallTalk.isCancelled(), "so what they say next is the server's business again");
    }

    @Test
    void aQuestionThatRanOutOnAScreenNobodyWillReopenLeavesNothingRunning() {
        List<CompletableFuture<PromptResult<String>>> outcomes = new ArrayList<>();
        Screen<Layout> screen = Screen.of(3).title("Auction").debounce(0);
        //a screen that redraws once a tick, so there is something running for a suspended view to keep alive
        screen.addComponent(component -> {
            component.every(1);
            component.render(slots -> slots.icon(0, iconOf(new ItemStack(Material.IRON_INGOT))));
        });
        screen.icon(1, iconOf(new ItemStack(Material.ARROW))).onClick(context -> outcomes.add(
                PromptRegistry.get().ask(context.getView(), PromptSpec.of("§eName it:").timeout(SOON))));
        ScreenView view = world.open(screen, player);
        assertEquals(1, world.getScheduler().getPeriodicTaskCount());

        clicks.leftClick(player, 1);
        world.advanceTicks(1);
        assertTrue(view.isSuspended(), "the screen is set aside while the question is out");
        //from here on the server will not open a window, so giving the screen back is going to fail
        player.refuseOpens(true);

        world.advanceTicks(1);
        PromptResult<String> outcome = outcomes.get(0).getNow(null);

        assertEquals(PromptResult.Kind.TIMEOUT, outcome.getKind());
        assertTrue(view.isClosed(), "a screen that cannot be given back is torn down, not left set aside "
                + "forever holding its state and the player");
        assertEquals(0, Screens.getOpenCount());
        assertEquals(0, world.getScheduler().getPeriodicTaskCount(),
                "and its once-a-tick task died with it, instead of running forever against no window");
    }

    @Test
    void aPlayerWhoLeavesMidQuestionGetsNothingBack() {
        AtomicInteger quit = new AtomicInteger();
        AtomicInteger timedOut = new AtomicInteger();
        List<CompletableFuture<PromptResult<String>>> outcomes = new ArrayList<>();
        ScreenView view = openTheScreenThatAsks(context -> outcomes.add(PromptRegistry.get()
                .ask(context.getView(), PromptSpec.of("§eName it:")
                        .onQuit(quit::incrementAndGet)
                        .onTimeout(screen -> timedOut.incrementAndGet()))));
        askTheQuestion();
        int containersBefore = containersOpened();

        //still online while the event runs, exactly as a server raises it - so "nothing came back" is the
        //framework's decision and not an unreachable player
        world.getEvents().fireQuit(player.asPlayer());

        PromptResult<String> outcome = outcomes.get(0).getNow(null);
        assertEquals(PromptResult.Kind.QUIT, outcome.getKind());
        assertEquals(1, quit.get());
        assertEquals(0, timedOut.get());
        assertEquals(containersBefore, containersOpened(), "there is nobody left to show a screen to");
        assertTrue(waitsOn().isEmpty(), "and the wait is gone, not left holding the screen and the Player");
        assertTrue(view.isClosed());
        assertEquals(0, Screens.getOpenCount());
    }

    @Test
    void askingASecondQuestionCallsTheFirstOneOffInsteadOfStackingOnIt() {
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        List<CompletableFuture<String>> asked = new ArrayList<>();
        ScreenView view = openTheScreenThatAsks(context -> {
            CompletableFuture<String> calledOff = context.ask(PromptSpec.of("§eFirst question:"));
            calledOff.thenAccept(first::add);
            asked.add(calledOff);
            CompletableFuture<String> live = context.ask(PromptSpec.of("§eSecond question:"));
            live.thenAccept(second::add);
            asked.add(live);
        });

        //both questions are asked from the same click, which is the only moment a screen can ask twice:
        //by the next tick it has no window left to be clicked through
        askTheQuestion();
        int containersBefore = containersOpened();

        assertEquals(1, waitsOn().size(), "one question is out, not two - a wait left behind would swallow "
                + "the answer meant for this one and never be noticed");
        assertTrue(view.isSuspended());
        assertTrue(asked.get(0).isCancelled(), "the question that was called off answers nobody");
        assertFalse(asked.get(1).isDone(), "and the one that replaced it is the live one");

        world.getEvents().typeInChat(player.asPlayer(), "an answer");
        world.advanceTicks(1);

        assertTrue(first.isEmpty(), "the abandoned question gets no answer, not even the wrong one");
        assertEquals(Arrays.asList("an answer"), second);
        assertFalse(view.isSuspended(), "and the screen comes back");
        assertSame(view, Screens.getOpenView(player.getUniqueId()));
        assertEquals(containersBefore + 1, containersOpened(), "once, not once per question asked");
    }

}
