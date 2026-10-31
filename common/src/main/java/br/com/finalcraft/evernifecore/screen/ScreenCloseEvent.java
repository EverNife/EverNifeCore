package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/** A screen went away - closed, left, replaced, shut down. Posted after its close handler ran, only when somebody listens. */
public class ScreenCloseEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(ScreenCloseEvent.class);
    }

    private final ScreenView view;
    private final FPlayer viewer;
    private final CloseReason reason;

    public ScreenCloseEvent(@Nonnull ScreenView view, @Nullable FPlayer viewer, @Nonnull CloseReason reason) {
        this.view = view;
        this.viewer = viewer;
        this.reason = reason;
    }

    @Nonnull
    public ScreenView getView() {
        return view;
    }

    /** Who was looking - the view itself has already let go of them. */
    @Nullable
    public FPlayer getViewer() {
        return viewer;
    }

    @Nullable
    public ScreenKey getKey() {
        return view.getScreen().getKey();
    }

    @Nonnull
    public CloseReason getReason() {
        return reason;
    }

}
