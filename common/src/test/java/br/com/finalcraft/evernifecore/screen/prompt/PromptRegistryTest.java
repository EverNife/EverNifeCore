package br.com.finalcraft.evernifecore.screen.prompt;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenNavigation;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.testing.FakePromptChannels.FakePromptChannel;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one registry every prompt channel shares: the question kept per screen, the screen set aside and given back, the
 * ways a question ends, and which channel asks.
 *
 * <p>The channels here are the engine's fakes: they show nothing and record what the registry made them do, so every
 * assertion is about the registry - a channel that kept its own pending map would have nothing left to keep.</p>
 */
class PromptRegistryTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender steve;
    private AtomicInteger closes;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        steve = world.viewer("Steve");
        closes = new AtomicInteger();
    }

    @AfterEach
    void teardown() {
        world.close();
    }

    private ScreenView openScreen() {
        Screen<Layout> screen = Screen.of(1).onClose(ctx -> closes.incrementAndGet());
        return world.open(screen, steve);
    }

    /** Asks and lets the tick pass that the registry arms the question on. */
    private <T> CompletableFuture<PromptResult<T>> ask(ScreenView view, PromptSpec<T> spec) {
        CompletableFuture<PromptResult<T>> result = PromptRegistry.get().ask(view, spec);
        world.advanceTicks(1);
        return result;
    }

    private FakePromptChannel chat() {
        return world.prompts().get(PromptKind.CHAT);
    }

    private void closeScreen(ScreenView view) {
        view.close();
        world.advanceTicks(1);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  How a question ends
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theAnswerEndsItWithTheValueAndTheSameScreenBack() {
        ScreenView view = openScreen();
        int windows = world.getRenderer().getSurfaces().size();
        CompletableFuture<PromptResult<Integer>> asked = ask(view, PromptSpec.of("How many?").parse(Integer::parseInt));

        assertTrue(view.isSuspended(), "the screen is set aside while the question is out");
        assertEquals(PromptKind.CHAT, PromptRegistry.get().getPending(view));
        assertEquals(PromptReply.accepted(), world.prompts().type(" 42 "));
        world.advanceTicks(1);

        assertEquals(42, asked.getNow(null).getValue());
        assertFalse(view.isSuspended());
        assertSame(view, Screens.getOpenView(steve), "the very screen that asked, revived");
        assertEquals(windows + 1, world.getRenderer().getSurfaces().size(), "on a window of its own again");
        assertNull(PromptRegistry.get().getPending(view));
        assertEquals(1, chat().getDismissed().size(), "and the channel was told to take its window away");
        closeScreen(view);
    }

    @Test
    void aRefusedAnswerKeepsTheQuestionOpenWithoutRestartingTheWait() {
        ScreenView view = openScreen();
        CompletableFuture<PromptResult<Integer>> asked = ask(view, PromptSpec.of("How many?")
                .timeout(Duration.ofSeconds(1))
                .parse(text -> {
                    if (!text.matches("\\d+")) {
                        throw new IllegalArgumentException("Not a number: '" + text + "'. Type digits only, like 250.");
                    }
                    return Integer.parseInt(text);
                }));
        world.advanceTicks(10);

        PromptReply reply = world.prompts().type("abc");

        assertFalse(reply.isAccepted());
        assertEquals("Not a number: 'abc'. Type digits only, like 250.", reply.getMessage(), "the parser's own text");
        assertTrue(chat().getOpened().get(0).isOpen(), "the question is still out");
        world.advanceTicks(9);
        assertFalse(asked.isDone(), "one tick before the deadline set when it was asked");
        world.advanceTicks(1);
        assertEquals(PromptResult.Kind.TIMEOUT, asked.getNow(null).getKind(), "the refusal bought no time");
        closeScreen(view);
    }

    @Test
    void aParserThatThrowsWithoutAMessageStillRefusesWithText() {
        ScreenView view = openScreen();
        CompletableFuture<PromptResult<Integer>> asked = ask(view, PromptSpec.of("How many?").parse(Integer::valueOf));

        PromptReply reply = world.prompts().type("");

        assertFalse(reply.isAccepted());
        assertFalse(reply.getMessage().trim().isEmpty(), "a blank refusal looks like a frozen window");
        world.prompts().closeWindow();
        world.advanceTicks(1);
        assertEquals(PromptResult.Kind.CANCELLED, asked.getNow(null).getKind());
        closeScreen(view);
    }

    @Test
    void theCancelWordEndsItCancelledAndGivesTheScreenBack() {
        ScreenView view = openScreen();
        CompletableFuture<PromptResult<String>> asked = ask(view, PromptSpec.of("Name?").cancelWord("cancel"));

        world.prompts().type("CANCEL");
        world.advanceTicks(1);

        assertEquals(PromptResult.Kind.CANCELLED, asked.getNow(null).getKind(), "the word matches whatever the case");
        assertSame(view, Screens.getOpenView(steve));
        closeScreen(view);
    }

    @Test
    void closingTheChannelsWindowEndsItCancelledAndTheScreenComesBackWithoutAnOnClose() {
        ScreenView view = openScreen();
        CompletableFuture<PromptResult<String>> asked = ask(view, PromptSpec.of("Name?"));

        world.prompts().closeWindow();
        world.advanceTicks(1);

        assertEquals(PromptResult.Kind.CANCELLED, asked.getNow(null).getKind());
        assertFalse(view.isSuspended());
        assertSame(view, Screens.getOpenView(steve));
        assertEquals(0, closes.get(), "leaving the question is not leaving the screen");
        closeScreen(view);
        assertEquals(1, closes.get());
    }

    @Test
    void noAnswerInTimeEndsItAsTimeoutTellsTheHandlerAndGivesTheScreenBack() {
        ScreenView view = openScreen();
        List<ScreenView> timedOutOn = new ArrayList<>();
        CompletableFuture<PromptResult<String>> asked = ask(view, PromptSpec.of("Name?")
                .timeout(Duration.ofMillis(100))
                .onTimeout(timedOutOn::add));

        world.advanceTicks(2);

        assertEquals(PromptResult.Kind.TIMEOUT, asked.getNow(null).getKind());
        assertEquals(1, timedOutOn.size());
        assertSame(view, timedOutOn.get(0), "the handler is told which screen ran out");
        assertSame(view, Screens.getOpenView(steve));
        closeScreen(view);
    }

    @Test
    void aQuestionThatEndedLeavesNoTimeoutBehind() {
        ScreenView view = openScreen();
        int tasksBefore = world.getTicks().getActiveTaskCount();
        ask(view, PromptSpec.of("Name?"));
        assertEquals(tasksBefore + 1, world.getTicks().getActiveTaskCount(), "the timeout is armed");

        world.prompts().type("Alex");
        world.advanceTicks(1);

        assertEquals(tasksBefore, world.getTicks().getActiveTaskCount(), "and cancelled with the answer");
        closeScreen(view);
    }

    @Test
    void aTimeoutOnAScreenThatCannotComeBackTearsTheScreenDown() {
        ScreenView view = openScreen();
        CompletableFuture<PromptResult<String>> asked = ask(view, PromptSpec.of("Name?").timeout(Duration.ofMillis(50)));
        world.getRenderer().refuseOpens(true);

        world.advanceTicks(1);

        assertEquals(PromptResult.Kind.TIMEOUT, asked.getNow(null).getKind());
        assertTrue(view.isClosed(), "a screen that cannot be given back is not left set aside forever");
        assertEquals(0, Screens.getOpenCount());
        world.getRenderer().refuseOpens(false);
    }

    @Test
    void aViewerWhoLeavesEndsItAsQuitAndNothingComesBack() {
        ScreenView view = openScreen();
        AtomicInteger quit = new AtomicInteger();
        int windows = world.getRenderer().getSurfaces().size();
        CompletableFuture<PromptResult<String>> asked = ask(view, PromptSpec.of("Name?").onQuit(quit::incrementAndGet));

        Screens.viewerGone(steve.getUniqueId(), CloseReason.DISCONNECTED);

        assertEquals(PromptResult.Kind.QUIT, asked.getNow(null).getKind());
        assertEquals(1, quit.get());
        assertTrue(view.isClosed());
        assertEquals(windows, world.getRenderer().getSurfaces().size(), "there is nobody to show a screen to");
        assertEquals(1, chat().getDismissed().size(), "the channel took its window away");
    }

    @Test
    void releasingTheScreenMidQuestionEndsItAsQuitBeforeTheScreensOnClose() {
        List<PromptKind> pendingSeenByOnClose = new ArrayList<>();
        Screen<Layout> screen = Screen.of(1);
        screen.onClose(ctx -> pendingSeenByOnClose.add(PromptRegistry.get().getPending(ctx.getView())));
        ScreenView view = world.open(screen, steve);
        CompletableFuture<PromptResult<String>> asked = ask(view, PromptSpec.of("Name?"));

        ScreenNavigation.discard(steve.getUniqueId());

        assertEquals(PromptResult.Kind.QUIT, asked.getNow(null).getKind());
        assertEquals(1, chat().getDismissed().size());
        assertEquals(1, pendingSeenByOnClose.size());
        assertNull(pendingSeenByOnClose.get(0), "onClose found no question left over from the released screen");
    }

    @Test
    void aSecondQuestionCallsTheFirstOffAndTakesTheScreenOverAsItIs() {
        ScreenView view = openScreen();
        int windows = world.getRenderer().getSurfaces().size();
        CompletableFuture<PromptResult<String>> first = PromptRegistry.get().ask(view, PromptSpec.of("First?"));
        CompletableFuture<PromptResult<String>> second = PromptRegistry.get().ask(view, PromptSpec.of("Second?"));
        world.advanceTicks(1);

        assertEquals(PromptResult.Kind.CANCELLED, first.getNow(null).getKind());
        assertFalse(second.isDone());
        assertTrue(view.isSuspended(), "never given back in between");
        assertEquals(windows, world.getRenderer().getSurfaces().size());
        assertEquals(1, chat().getDismissed().size(), "the first one's window went");

        world.prompts().type("two");
        world.advanceTicks(1);
        assertEquals("two", second.getNow(null).getValue());
        assertEquals(windows + 1, world.getRenderer().getSurfaces().size(), "given back once, not once per question");
        closeScreen(view);
    }

    @Test
    void anEndedSessionIsDeafToWhateverItsChannelReportsAfterwards() {
        ScreenView view = openScreen();
        CompletableFuture<PromptResult<String>> asked = ask(view, PromptSpec.of("Name?"));
        PromptSession<?> session = world.prompts().lastOpened();

        session.answer("Alex");
        assertFalse(session.isOpen(), "decided at once, ended on the next tick");
        session.cancel();
        assertEquals(PromptReply.accepted(), session.answer("Bob"));
        world.advanceTicks(1);
        session.cancel(); //what a window closing after its dismiss reports

        assertEquals("Alex", asked.getNow(null).getValue());
        assertEquals(1, chat().getDismissed().size(), "one ending, one dismiss");
        world.advanceTicks(2);
        assertSame(view, Screens.getOpenView(steve));
        closeScreen(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Which channel asks
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void withoutAPreferenceTheRichestChannelThatServesThePlayerAsks() {
        world.prompts().register(PromptKind.ANVIL, true);
        world.prompts().register(PromptKind.PAGE, false);

        assertEquals(PromptKind.ANVIL, PromptRegistry.get().resolve(steve, null), "PAGE does not serve them, ANVIL beats CHAT");
        world.prompts().get(PromptKind.PAGE).setSupported(true);
        assertEquals(PromptKind.PAGE, PromptRegistry.get().resolve(steve, null));

        ScreenView view = openScreen();
        ask(view, PromptSpec.of("Name?"));
        assertEquals(PromptKind.PAGE, PromptRegistry.get().getPending(view));
        assertEquals(1, world.prompts().get(PromptKind.PAGE).getOpened().size());
        world.prompts().closeWindow();
        world.advanceTicks(1);
        closeScreen(view);
    }

    @Test
    void thePreferredKindAsksWhenItServesThePlayerAndTheOrderOtherwise() {
        world.prompts().register(PromptKind.ANVIL, false);
        world.prompts().register(PromptKind.PAGE, true);

        assertEquals(PromptKind.CHAT, PromptRegistry.get().resolve(steve, PromptKind.CHAT), "preferred and serving");
        assertEquals(PromptKind.PAGE, PromptRegistry.get().resolve(steve, PromptKind.ANVIL),
                "the preferred one does not serve them: the order picks");

        ScreenView view = openScreen();
        ask(view, PromptSpec.of("Name?").prefer(PromptKind.CHAT));
        assertEquals(PromptKind.CHAT, PromptRegistry.get().getPending(view));
        world.prompts().closeWindow();
        world.advanceTicks(1);
        closeScreen(view);
    }

    @Test
    void withNoChannelForThePlayerItEndsCancelledAndSaysHowToWireOne() {
        world.prompts().clear();
        ScreenView view = openScreen();

        List<CompletableFuture<PromptResult<String>>> asked = new ArrayList<>();
        List<String> logged = Logs.capture(() -> asked.add(ask(view, PromptSpec.of("Name?"))));

        assertEquals(PromptResult.Kind.CANCELLED, asked.get(0).getNow(null).getKind(), "a wiring error is not the player leaving");
        assertFalse(view.isSuspended(), "the screen was never set aside");
        assertTrue(logged.stream().anyMatch(line -> line.contains("No PromptChannel can ask [Steve]")
                && line.contains("none is registered")), logged.toString());
        closeScreen(view);
    }

    @Test
    void aSecondChannelForATakenKindIsRefusedNamingBoth() {
        PromptChannel mine = new MyAnvil();
        ECPluginData other = Plugins.fakePluginData("MyPlugin", tempDir.resolve("MyPlugin").toFile());
        try {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> PromptRegistry.get().register(other, mine));

            assertEquals("A PromptChannel for CHAT is already registered by [test] (FakePromptChannel); [MyPlugin] tried "
                    + "to add [MyAnvil]. One channel per kind: unregister the first, or give yours another PromptKind.",
                    refused.getMessage());
            assertSame(chat(), PromptRegistry.get().getChannel(PromptKind.CHAT), "the first one stays");
        } finally {
            Plugins.forget("MyPlugin");
        }
    }

    /** A channel of another plugin for a kind the world's fake chat already took. */
    private static final class MyAnvil implements PromptChannel {
        @Override
        public PromptKind getKind() {
            return PromptKind.CHAT;
        }

        @Override
        public boolean isSupported(FPlayer player) {
            return true;
        }

        @Override
        public void open(PromptSession<?> session) {
        }

        @Override
        public void dismiss(PromptSession<?> session) {
        }
    }

    @Test
    void theScreenWorldRefusesToCloseWithAQuestionStillOut() {
        ScreenView view = openScreen();
        ask(view, PromptSpec.of("Name?"));

        AssertionError leak = assertThrows(AssertionError.class, world::close);
        assertTrue(leak.getMessage().contains("1 question(s) still out"), leak.getMessage());
    }

}
