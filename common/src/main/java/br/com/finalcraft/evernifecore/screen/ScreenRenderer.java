package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Set;

/**
 * Who draws a screen for a player. A platform ships one (the chest on Bukkit), a client mod or another platform's page
 * registers another through {@link Screens#registerRenderer}; per open, the highest {@link #getPriority()} among the
 * renderers that {@link #supports} the viewer and cover the screen's capabilities draws it.
 *
 * <p>The contract a renderer gets is closed on purpose: stable element ids, text already resolved for the viewer, icons
 * as {@code ItemSpec}, a click as an intention ({@link ScreenView#click}), changes as patches by id. No logic runs on the
 * client side, and nothing here names a transport, a mod or a test framework - a renderer that needs one carries it.</p>
 */
public interface ScreenRenderer {

    /** {@code owner:name}, e.g. {@code evernifecore:chest}; the key it is registered under. */
    @Nonnull
    String getId();

    /** Highest wins among the renderers that serve the player; the chest is 0. */
    int getPriority();

    @Nonnull
    Set<RendererCapability> getCapabilities();

    /** Whether this renderer can draw {@code screen} for {@code viewer} right now (has the mod, is online...). Never throws. */
    boolean supports(@Nonnull FPlayer viewer, @Nonnull Screen<?> screen);

    /**
     * Creates the window for {@code view} - titled {@link ScreenView#getCurrentTitle()}, sized
     * {@link ScreenView#getGrid()} - and answers the surface the view writes through, or {@code null} when the platform
     * refused to show it (the player is leaving, another plugin cancelled the open). Renderer thread.
     */
    @Nullable
    ScreenSurface open(@Nonnull ScreenView view);

    /** Ticks on the thread this renderer draws on. */
    @Nonnull
    ScreenScheduler getScheduler();

}
