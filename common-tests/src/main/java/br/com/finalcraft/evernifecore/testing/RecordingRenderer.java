package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.screen.RendererCapability;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenRenderer;
import br.com.finalcraft.evernifecore.screen.ScreenScheduler;
import br.com.finalcraft.evernifecore.screen.ScreenSurface;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * A {@link ScreenRenderer} that draws into memory: {@code test:recording}, priority and capabilities configurable, one
 * {@link RecordingSurface} per window it opened. Questions go through {@link ScreenWorld#prompts()}. It is what {@link ScreenWorld} installs, and it passes {@link RendererConformance}.
 */
public class RecordingRenderer implements ScreenRenderer {

    public static final String ID = "test:recording";

    private final String id;
    private final int priority;
    private final Set<RendererCapability> capabilities;
    private final TickScheduler scheduler;
    private final List<RecordingSurface> surfaces = new ArrayList<>();
    private BiPredicate<FPlayer, Screen<?>> supports = (viewer, screen) -> viewer.isOnline();
    private boolean refuseOpens;
    private boolean refuseNextOpen;

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

    /**
     * Makes only the next open answer no window and the ones after it open again - a screen refused on top, whose
     * way back to the screen below must still open.
     */
    @Nonnull
    public RecordingRenderer refuseNextOpen() {
        this.refuseNextOpen = true;
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
        if (refuseNextOpen) {
            refuseNextOpen = false;
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

}
