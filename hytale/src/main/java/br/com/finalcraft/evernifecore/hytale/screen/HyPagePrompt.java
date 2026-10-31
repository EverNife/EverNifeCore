package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.hytale.scheduler.HyFCScheduler;
import br.com.finalcraft.evernifecore.screen.Cancellable;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.ScreenNavigation;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.prompt.PromptChannel;
import br.com.finalcraft.evernifecore.screen.prompt.PromptResult;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The page channel: the screen is set aside and a page with the question, a text field, OK and Cancel takes its place.
 * OK sends the field's text; the spec's parser reads it, and a refusal empties the field and shows the parser's text
 * under it, without costing the player time. Cancel, the escape key and the cancel word end it as
 * {@link PromptResult.Kind#CANCELLED}; the timeout as {@code TIMEOUT}; leaving as {@code QUIT}. Every ending but the
 * last gives the screen back.
 *
 * <p>Everything here runs on the screen thread, the future included; the page's own callbacks hop to it.</p>
 */
public final class HyPagePrompt implements PromptChannel {

    private final HyPageRenderer renderer;
    /** The question each screen has out - a second one calls the first off. Screen thread only. */
    private final Map<ScreenView, Asking<?>> asking = new HashMap<>();

    HyPagePrompt(@Nonnull HyPageRenderer renderer) {
        this.renderer = renderer;
    }

    @Nonnull
    @Override
    public <T> CompletableFuture<PromptResult<T>> ask(@Nonnull ScreenView view, @Nonnull PromptSpec<T> spec) {
        CompletableFuture<PromptResult<T>> answer = new CompletableFuture<>();
        renderer.getScheduler().run(() -> arm(view, spec, answer));
        return answer;
    }

    private <T> void arm(ScreenView view, PromptSpec<T> spec, CompletableFuture<PromptResult<T>> answer) {
        FPlayer viewer = view.getViewer();
        String world = viewer == null || view.isClosed() ? null : renderer.getTransport().worldOf(viewer);
        if (world == null) {
            answer.complete(PromptResult.<T>quit());
            return;
        }
        //the question called off is the one that set this screen aside, so this one takes the screen over as it is
        Asking<?> previous = asking.remove(view);
        if (previous != null) {
            abandon(previous);
        } else if (!ScreenNavigation.suspend(view)) {
            answer.complete(PromptResult.<T>quit());
            return;
        }
        Asking<T> current = new Asking<>(view, viewer, world, spec, answer,
                new Page(renderer.getTransport().refOf(viewer), spec.getQuestion()));
        current.page.asking = current;
        asking.put(view, current);
        current.timeout = renderer.getScheduler().later(ticksOf(spec.getTimeoutMillis()),
                () -> settle(current, PromptResult.<T>timeout()));
        renderer.show(viewer, world, current.page, () -> settle(current, PromptResult.<T>quit()));
    }

    /** The player left: every question they had out ends as {@code QUIT}, and nothing comes back. Screen thread. */
    void viewerGone(@Nonnull UUID viewerId) {
        List<Asking<?>> gone = new ArrayList<>();
        for (Asking<?> each : asking.values()) {
            if (each.viewer.getUniqueId().equals(viewerId)) {
                gone.add(each);
            }
        }
        for (Asking<?> each : gone) {
            settleQuit(each);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Endings - screen thread
    // -----------------------------------------------------------------------------------------------------------------

    private <T> void submitted(Asking<T> current, @Nullable String typed) {
        if (current.answer.isDone()) {
            return;
        }
        String text = typed == null ? "" : typed.trim();
        String cancelWord = current.spec.getCancelWord();
        if (!cancelWord.isEmpty() && text.equalsIgnoreCase(cancelWord)) {
            settle(current, PromptResult.<T>cancelled());
            return;
        }
        T parsed;
        try {
            parsed = current.spec.getParser().parse(text);
        } catch (Throwable refused) {
            UICommandBuilder ui = new UICommandBuilder();
            HyPageLayout.promptError(ui, refused.getMessage());
            renderer.onWorldThread(current.world, () -> renderer.getTransport().update(current.page, ui), () -> { });
            return;
        }
        settle(current, PromptResult.value(parsed));
    }

    private <T> void settle(Asking<T> current, PromptResult<T> result) {
        if (current.answer.isDone()) {
            return;
        }
        asking.remove(current.view, current);
        current.end();
        if (result.getKind() == PromptResult.Kind.QUIT) {
            if (current.spec.getOnQuit() != null) {
                current.spec.getOnQuit().run();
            }
            current.answer.complete(result);
            return;   //there is nobody left to show the screen to
        }
        if (result.getKind() == PromptResult.Kind.TIMEOUT && current.spec.getOnTimeout() != null) {
            current.spec.getOnTimeout().accept(current.view);
        }
        //the screen's own page takes the place of the question's
        if (!ScreenNavigation.resume(current.view)) {
            ScreenNavigation.discard(current.view.getViewerId());
            current.page.markClosing(CloseReason.REQUESTED);
            renderer.onWorldThread(current.world, () -> renderer.getTransport().close(current.viewer, current.page), () -> { });
        }
        current.answer.complete(result);
    }

    private <T> void settleQuit(Asking<T> current) {
        settle(current, PromptResult.<T>quit());
    }

    private static <T> void abandon(Asking<T> previous) {
        previous.end();
        previous.answer.complete(PromptResult.<T>cancelled());
    }

    /** Milliseconds as ticks of the screen clock, which runs at the default world's rate. */
    private static long ticksOf(long millis) {
        return Math.max(1L, millis * HyFCScheduler.INSTANCE.getMainWorldTps() / 1000L);
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static final class Asking<T> {

        private final ScreenView view;
        private final FPlayer viewer;
        private final String world;
        private final PromptSpec<T> spec;
        private final CompletableFuture<PromptResult<T>> answer;
        private final Page page;
        private Cancellable timeout;

        private Asking(ScreenView view, FPlayer viewer, String world, PromptSpec<T> spec,
                       CompletableFuture<PromptResult<T>> answer, Page page) {
            this.view = view;
            this.viewer = viewer;
            this.world = world;
            this.spec = spec;
            this.answer = answer;
            this.page = page;
        }

        private void end() {
            if (timeout != null) {
                timeout.cancel();
            }
        }

    }

    /** The question as a page. Its callbacks arrive on the world thread and are handed to the screen thread. */
    final class Page extends HyPage {

        private final String question;
        private Asking<?> asking;

        private Page(@Nullable PlayerRef playerRef, @Nonnull String question) {
            super(playerRef, CustomPageLifetime.CanDismiss);
            this.question = question;
        }

        @Override
        public void build(@Nullable Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
                          @Nullable Store<EntityStore> store) {
            HyPageLayout.drawPrompt(ui, events, question);
        }

        @Override
        public void handleDataEvent(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store, @Nonnull HyPageEvent event) {
            Asking<?> current = asking;
            renderer.getScheduler().run(() -> {
                if (HyPageLayout.SUBMIT.equals(event.getKind())) {
                    submitted(current, event.getValue());
                } else if (HyPageLayout.CANCEL.equals(event.getKind())) {
                    cancel(current);
                }
            });
        }

        @Override
        public void onDismiss(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store) {
            if (dismissedAs() != null) {
                return; //the core took it away: the screen came back, or another question replaced it
            }
            Asking<?> current = asking;
            renderer.getScheduler().run(() -> cancel(current));
        }

        private <T> void cancel(Asking<T> current) {
            settle(current, PromptResult.<T>cancelled());
        }

    }

}
