package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.screen.RendererCapability;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenNavigation;
import br.com.finalcraft.evernifecore.screen.ScreenRenderer;
import br.com.finalcraft.evernifecore.screen.ScreenScheduler;
import br.com.finalcraft.evernifecore.screen.ScreenSurface;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.prompt.PromptChannel;
import br.com.finalcraft.evernifecore.screen.prompt.PromptResult;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

/**
 * A {@link ScreenRenderer} that draws into memory: {@code test:recording}, priority and capabilities configurable, one
 * {@link RecordingSurface} per window it opened, and a prompt channel the test answers ({@link #answer(String)}).
 * It is what {@link ScreenWorld} installs, and it passes {@link RendererConformance}.
 */
public class RecordingRenderer implements ScreenRenderer {

    public static final String ID = "test:recording";

    private final String id;
    private final int priority;
    private final Set<RendererCapability> capabilities;
    private final TickScheduler scheduler;
    private final List<RecordingSurface> surfaces = new ArrayList<>();
    private final Channel channel = new Channel();
    private BiPredicate<FPlayer, Screen<?>> supports = (viewer, screen) -> viewer.isOnline();
    private boolean refuseOpens;
    private boolean withPrompts = true;

    public RecordingRenderer(@Nonnull TickScheduler scheduler, @Nonnull RendererCapability... capabilities) {
        this(ID, 0, scheduler, capabilities);
    }

    public RecordingRenderer(@Nonnull String id, int priority, @Nonnull TickScheduler scheduler,
                             @Nonnull RendererCapability... capabilities) {
        this.id = id;
        this.priority = priority;
        this.scheduler = scheduler;
        this.capabilities = capabilities.length == 0 ? EnumSet.noneOf(RendererCapability.class)
                : EnumSet.of(capabilities[0], capabilities);
    }

    /** Which players and screens it serves; online players and every screen by default. */
    @Nonnull
    public RecordingRenderer supporting(@Nonnull BiPredicate<FPlayer, Screen<?>> supports) {
        this.supports = supports;
        return this;
    }

    /** Makes every open answer no window, as a platform refusing it would. */
    @Nonnull
    public RecordingRenderer refuseOpens(boolean refuse) {
        this.refuseOpens = refuse;
        return this;
    }

    /** Removes the prompt channel: {@link #getPromptChannel()} answers {@code null}. */
    @Nonnull
    public RecordingRenderer withoutPrompts() {
        this.withPrompts = false;
        return this;
    }

    @Nonnull
    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Nonnull
    @Override
    public Set<RendererCapability> getCapabilities() {
        return Collections.unmodifiableSet(capabilities);
    }

    @Override
    public boolean supports(@Nonnull FPlayer viewer, @Nonnull Screen<?> screen) {
        return supports.test(viewer, screen);
    }

    @Nullable
    @Override
    public ScreenSurface open(@Nonnull ScreenView view) {
        if (refuseOpens) {
            return null;
        }
        RecordingSurface surface = newSurface(view);
        surfaces.add(surface);
        return surface;
    }

    /** The surface a window gets - a test subclass hands back a sabotaged one to see a guard go red. */
    @Nonnull
    protected RecordingSurface newSurface(@Nonnull ScreenView view) {
        return new RecordingSurface(view);
    }

    @Nonnull
    @Override
    public ScreenScheduler getScheduler() {
        return scheduler;
    }

    @Nullable
    @Override
    public PromptChannel getPromptChannel() {
        return withPrompts ? channel : null;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What a test reads and does
    // -----------------------------------------------------------------------------------------------------------------

    /** Every window it opened, oldest first. */
    @Nonnull
    public List<RecordingSurface> getSurfaces() {
        return Collections.unmodifiableList(surfaces);
    }

    /** The window opened last. */
    @Nonnull
    public RecordingSurface getSurface() {
        if (surfaces.isEmpty()) {
            throw new IllegalStateException("The recording renderer has opened no window yet. Open a screen first.");
        }
        return surfaces.get(surfaces.size() - 1);
    }

    /** The surface drawn for {@code view}, the newest when it had several. */
    @Nonnull
    public RecordingSurface surfaceOf(@Nonnull ScreenView view) {
        for (int i = surfaces.size() - 1; i >= 0; i--) {
            if (surfaces.get(i).getView() == view) {
                return surfaces.get(i);
            }
        }
        throw new IllegalStateException("The recording renderer never opened a window for " + view + ".");
    }

    /** Whether a prompt is waiting for an answer. */
    public boolean isAsking() {
        return channel.pending != null;
    }

    /** Answers the waiting prompt as if the viewer had typed {@code text}: parsed, then the screen comes back. */
    public void answer(@Nonnull String text) {
        channel.answer(text);
    }

    /** The question the waiting prompt asked, or {@code null}. */
    @Nullable
    public String getAskedQuestion() {
        return channel.pending == null ? null : channel.pending.spec.getQuestion();
    }

    private final class Channel implements PromptChannel {

        private Pending<?> pending;

        @Nonnull
        @Override
        public <T> CompletableFuture<PromptResult<T>> ask(@Nonnull ScreenView view, @Nonnull PromptSpec<T> spec) {
            CompletableFuture<PromptResult<T>> answer = new CompletableFuture<>();
            RecordingSurface surface = surfaceOf(view);
            if (!ScreenNavigation.suspend(view)) {
                answer.complete(PromptResult.<T>cancelled());
                return answer;
            }
            surface.dropSilently();
            pending = new Pending<>(view, spec, answer);
            return answer;
        }

        private void answer(String text) {
            Pending<?> asked = pending;
            if (asked == null) {
                throw new IllegalStateException("No prompt is waiting for an answer. Click the field that asks first.");
            }
            pending = null;
            asked.complete(text);
        }

    }

    private static final class Pending<T> {

        private final ScreenView view;
        private final PromptSpec<T> spec;
        private final CompletableFuture<PromptResult<T>> answer;

        private Pending(ScreenView view, PromptSpec<T> spec, CompletableFuture<PromptResult<T>> answer) {
            this.view = view;
            this.spec = spec;
            this.answer = answer;
        }

        private void complete(String text) {
            ScreenNavigation.resume(view);
            if (text.equalsIgnoreCase(spec.getCancelWord())) {
                answer.complete(PromptResult.<T>cancelled());
                return;
            }
            try {
                answer.complete(PromptResult.value(spec.getParser().parse(text)));
            } catch (Exception unreadable) {
                answer.complete(PromptResult.<T>cancelled());
            }
        }

    }

}
