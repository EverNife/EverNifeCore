package br.com.finalcraft.evernifecore.screen;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * The window one {@link ScreenView} draws through - the only thing a {@link ScreenRenderer} implements per window.
 * {@link #apply(List)} is the single point of writing: one call per tick, with every slot that changed. A grid renderer
 * extends {@link GridSurface}; a renderer that draws by element implements this directly and groups the patches by id.
 *
 * <p>Every method runs on the renderer's own thread ({@link ScreenRenderer#getScheduler()}).</p>
 */
public interface ScreenSurface {

    /** Everything that changed since the last call, never empty. Renderer thread. */
    void apply(@Nonnull List<ElementPatch> patch);

    /**
     * Shows {@code title} on the open window. Asked only of a renderer with {@link RendererCapability#TITLE_CHANGE};
     * a chest swaps the container (one frame of flicker) and redraws what it held. Renderer thread.
     */
    void setTitle(@Nonnull String title);

    /**
     * Takes the window away. The renderer reports the window gone through {@link Screens#windowClosed} - now or when
     * the platform confirms it. Renderer thread.
     */
    void close(@Nonnull CloseReason reason);

    /**
     * The view drawn here was set aside - a prompt asks, or a screen opened on top of it: the window goes, the view
     * stays, and comes back on a new surface. Nothing reports this window closed; the renderer lets it go quietly (a
     * chest closes its inventory unless something opened over it). Renderer thread.
     */
    default void suspended() {

    }

    /** Whether {@code nativeWindow} - the renderer's own window object - is this surface. {@code null} never is. */
    boolean isBackedBy(@Nullable Object nativeWindow);

    /**
     * The view drawn through this window is being torn down, for {@code reason}. A window can hold what the view does
     * not - the items of an editable area, the stack on a cursor - and this is where it gives them back. Runs before the
     * screen's {@code onClose}, so the handler reads stores that already agree with the window. Renderer thread.
     */
    default void released(@Nonnull CloseReason reason) {

    }

}
