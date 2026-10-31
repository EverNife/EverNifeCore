package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/** A screen opened for a player: the window is up and drawn. Posted after the fact, only when somebody listens. */
public class ScreenOpenEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(ScreenOpenEvent.class);
    }

    private final ScreenView view;

    public ScreenOpenEvent(@Nonnull ScreenView view) {
        this.view = view;
    }

    @Nonnull
    public ScreenView getView() {
        return view;
    }

    @Nullable
    public FPlayer getViewer() {
        return view.getViewer();
    }

    /** The key of the screen, or {@code null} for an ad-hoc one. */
    @Nullable
    public ScreenKey getKey() {
        return view.getScreen().getKey();
    }

    /** The renderer that drew it. */
    @Nonnull
    public ScreenRenderer getRenderer() {
        return view.getRenderer();
    }

}
