package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.prompt.Prompt;
import br.com.finalcraft.evernifecore.screen.prompt.PromptResult;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A question asked on a page: the screen set aside, the answer from the field, and every way it can end. */
class HyPagePromptTest {

    @TempDir
    Path tempDir;

    @Test
    void thePageRendererAsksThroughThePagePrompt() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            assertInstanceOf(HyPagePrompt.class, pages.renderer.getPromptChannel());
        }
    }

    @Test
    void theAnswerIsTheFieldTextSentAsValueAndTheScreenComesBack() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            ScreenView view = pages.screens.open(Screen.of(1), steve);
            CompletableFuture<PromptResult<Integer>> asked = ask(pages, view, Prompt.of("How many?").parse(Integer::parseInt));

            assertTrue(view.isSuspended());
            assertInstanceOf(HyPagePrompt.Page.class, pages.transport.pageOf(steve), "the question took the page");
            pages.transport.answer(steve, " 42 ");

            assertEquals(42, asked.join().getValue());
            assertFalse(view.isSuspended());
            assertSame(view.getSurface(), pages.transport.pageOf(steve), "the screen's own page is back");
            close(pages, view);
        }
    }

    @Test
    void aRefusedAnswerShowsTheParsersTextAndKeepsAsking() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            ScreenView view = pages.screens.open(Screen.of(1), steve);
            CompletableFuture<PromptResult<Integer>> asked = ask(pages, view, Prompt.of("How many?").parse(text -> {
                if (!text.matches("\\d+")) {
                    throw new IllegalArgumentException("A whole number, please");
                }
                return Integer.parseInt(text);
            }));
            pages.transport.getUpdates().clear();

            pages.transport.answer(steve, "lots");

            assertFalse(asked.isDone());
            assertEquals(1, pages.transport.getUpdates().size());
            boolean shown = false;
            for (CustomUICommand command : pages.transport.getUpdates().get(0)) {
                shown |= "#Error.Text".equals(command.selector) && command.data.contains("A whole number, please");
            }
            assertTrue(shown, "the parser's text is under the field");

            pages.transport.answer(steve, "3");
            assertEquals(3, asked.join().getValue());
            close(pages, view);
        }
    }

    @Test
    void cancelEscapeAndTheCancelWordEndItAsCancelled() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            ScreenView view = pages.screens.open(Screen.of(1), steve);

            CompletableFuture<PromptResult<String>> byButton = ask(pages, view, Prompt.of("Name?"));
            pages.transport.pageOf(steve).handleDataEvent(null, null, new HyPageEvent(null, HyPageLayout.CANCEL, null));
            assertEquals(PromptResult.Kind.CANCELLED, byButton.join().getKind());

            CompletableFuture<PromptResult<String>> byEscape = ask(pages, view, Prompt.of("Name?"));
            pages.transport.dismiss(steve);
            assertEquals(PromptResult.Kind.CANCELLED, byEscape.join().getKind());

            CompletableFuture<PromptResult<String>> byWord = ask(pages, view, Prompt.of("Name?").cancelWord("cancel"));
            pages.transport.answer(steve, "CANCEL");
            assertEquals(PromptResult.Kind.CANCELLED, byWord.join().getKind());

            assertFalse(view.isSuspended(), "every cancel gives the screen back");
            assertSame(view, Screens.getOpenView(steve));
            close(pages, view);
        }
    }

    @Test
    void noAnswerInTimeEndsItAsTimeoutAndGivesTheScreenBack() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            ScreenView view = pages.screens.open(Screen.of(1), steve);
            CompletableFuture<PromptResult<String>> asked = ask(pages, view, Prompt.of("Name?").timeout(Duration.ofSeconds(1)));

            pages.screens.advanceTicks(31); //one second at the default world's 30 ticks per second

            assertEquals(PromptResult.Kind.TIMEOUT, asked.join().getKind());
            assertFalse(view.isSuspended());
            close(pages, view);
        }
    }

    @Test
    void aSecondQuestionCallsTheFirstOff() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            ScreenView view = pages.screens.open(Screen.of(1), steve);
            CompletableFuture<PromptResult<String>> first = ask(pages, view, Prompt.of("First?"));
            CompletableFuture<PromptResult<String>> second = ask(pages, view, Prompt.of("Second?"));

            assertEquals(PromptResult.Kind.CANCELLED, first.join().getKind());
            assertTrue(view.isSuspended(), "the screen stays aside for the second question");
            pages.transport.answer(steve, "two");
            assertEquals("two", second.join().getValue());
            close(pages, view);
        }
    }

    @Test
    void leavingEndsItAsQuitAndNothingComesBack() {
        try (HyPageWorld pages = HyPageWorld.open(tempDir)) {
            TestFPlayerSender steve = pages.screens.viewer("Steve");
            ScreenView view = pages.screens.open(Screen.of(1), steve);
            CompletableFuture<PromptResult<String>> asked = ask(pages, view, Prompt.of("Name?"));

            pages.renderer.onDisconnect(new PlayerDisconnectEvent(new PlayerRef(null, steve.getUniqueId(), "Steve", "en_us", null, null)));

            assertEquals(PromptResult.Kind.QUIT, asked.join().getKind());
            assertTrue(view.isClosed());
            assertNull(Screens.getOpenView(steve));
        }
    }

    private static <T> CompletableFuture<PromptResult<T>> ask(HyPageWorld pages, ScreenView view, Prompt<T> prompt) {
        return pages.renderer.getPromptChannel().ask(view, prompt);
    }

    private static void close(HyPageWorld pages, ScreenView view) {
        view.close();
        pages.screens.advanceTicks(1);
    }

}
