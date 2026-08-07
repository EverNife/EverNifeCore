package br.com.finalcraft.evernifecore.minecraft.gui.view.prompt;

import br.com.finalcraft.evernifecore.minecraft.chat.ChatExpectationListener;
import br.com.finalcraft.evernifecore.minecraft.chat.ExpectedChat;
import br.com.finalcraft.evernifecore.minecraft.chat.IChatAction;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiNavigation;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiViews;
import jakarta.annotation.Nonnull;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * The chat channel: the screen is set aside, the question goes to the player, and the next thing they
 * type is the answer.
 *
 * <p>It is a bridge over the core's own {@code ChatExpectationListener}, and everything it hears
 * arrives somewhere other than the main thread - the answer on the async chat event, the timeout on a
 * scheduled thread. Every one of those paths hops to the main thread before it touches a screen or
 * completes a future, so what a caller writes after {@code askOnChat(...)} runs where it may draw.</p>
 */
public final class ChatPromptChannel implements PromptChannel {

    private static final ChatPromptChannel INSTANCE = new ChatPromptChannel();

    /** What each screen has outstanding, so a second question calls the first one off. */
    private final Map<GuiView, Outstanding> outstanding = new WeakHashMap<>();

    private ChatPromptChannel() {

    }

    @Nonnull
    public static ChatPromptChannel get() {
        return INSTANCE;
    }

    @Override
    @Nonnull
    public <T> CompletableFuture<PromptResult<T>> ask(@Nonnull GuiView view, @Nonnull PromptSpec<T> spec) {
        CompletableFuture<PromptResult<T>> answer = new CompletableFuture<>();
        Player player = view.getViewer();
        if (player == null || !player.isOnline() || view.isClosed()) {
            answer.complete(PromptResult.<T>quit());
            return answer;
        }
        //a click is still being answered when this runs, and closing a container from inside its own
        //event is what the framework never does
        view.getScheduler().later(1L, () -> arm(view, spec, answer));
        return answer;
    }

    private <T> void arm(GuiView view, PromptSpec<T> spec, CompletableFuture<PromptResult<T>> answer) {
        Player player = view.getViewer();
        if (player == null || !player.isOnline() || view.isClosed()) {
            answer.complete(PromptResult.<T>quit());
            return;
        }

        abandonOutstanding(view);
        if (!GuiNavigation.suspend(view)) {
            answer.complete(PromptResult.<T>quit());
            return;
        }
        player.closeInventory();
        player.sendMessage(spec.getQuestion());

        IChatAction action = message -> onChat(view, spec, answer, message);
        ExpectedChat expectation = ChatExpectationListener.get().expectPlayerChat(player, action,
                spec.getTimeoutMillis(),
                () -> settle(view, spec, answer, PromptResult.<T>timeout()),
                () -> settle(view, spec, answer, PromptResult.<T>quit()));
        outstanding.put(view, new Outstanding(expectation,
                () -> answer.complete(PromptResult.<T>cancelled())));
    }

    /** Runs on the async chat thread; everything it decides is handed to the main one. */
    private <T> IChatAction.ActionResult onChat(GuiView view, PromptSpec<T> spec,
                                                CompletableFuture<PromptResult<T>> answer, String message) {
        String typed = message == null ? "" : message.trim();
        String cancelWord = spec.getCancelWord();
        if (!cancelWord.isEmpty() && typed.equalsIgnoreCase(cancelWord)) {
            settle(view, spec, answer, PromptResult.<T>cancelled());
            return IChatAction.ActionResult.SUCCESS_AND_CONSUME;
        }

        T parsed;
        try {
            parsed = spec.getParser().parse(typed);
        } catch (Throwable refused) {
            askAgain(view, spec, refused);
            return IChatAction.ActionResult.CONSUME_AND_CONTINUE;
        }
        settle(view, spec, answer, PromptResult.value(parsed));
        return IChatAction.ActionResult.SUCCESS_AND_CONSUME;
    }

    /** The refused attempt never reaches public chat, and it does not buy the player more time. */
    private void askAgain(GuiView view, PromptSpec<?> spec, Throwable refused) {
        String why = refused.getMessage();
        GuiViews.onMainThread(() -> {
            Player player = view.getViewer();
            if (player == null || !player.isOnline()) {
                return;
            }
            if (why != null && !why.isEmpty()) {
                player.sendMessage(why);
            }
            player.sendMessage(spec.getQuestion());
        });
    }

    private <T> void settle(GuiView view, PromptSpec<T> spec, CompletableFuture<PromptResult<T>> answer,
                            PromptResult<T> result) {
        GuiViews.onMainThread(() -> {
            if (answer.isDone()) {
                return;
            }
            outstanding.remove(view);
            if (result.getKind() == PromptResult.Kind.QUIT) {
                if (spec.getOnQuit() != null) {
                    spec.getOnQuit().run();
                }
                answer.complete(result);
                return;   //there is nobody left to show the screen to
            }
            if (result.getKind() == PromptResult.Kind.TIMEOUT && spec.getOnTimeout() != null) {
                spec.getOnTimeout().accept(view);
            }
            GuiNavigation.resume(view);
            answer.complete(result);
        });
    }

    /** Calls off the question a screen already had out, so questions never stack on one screen. */
    private void abandonOutstanding(GuiView view) {
        Outstanding previous = outstanding.remove(view);
        if (previous == null) {
            return;
        }
        previous.expectation.cancel();
        previous.abandon.run();
    }

    private static final class Outstanding {

        private final ExpectedChat expectation;
        private final Runnable abandon;

        private Outstanding(ExpectedChat expectation, Runnable abandon) {
            this.expectation = expectation;
            this.abandon = abandon;
        }

    }

}
