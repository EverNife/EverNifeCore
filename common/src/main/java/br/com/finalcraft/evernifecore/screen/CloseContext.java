package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Function;

/** What a close handler gets: who, why, and which element - if any - asked for the close. */
public final class CloseContext<L extends Layout> {

    private final ScreenView view;
    private final FPlayer viewer;
    private final CloseReason reason;

    CloseContext(ScreenView view, @Nullable FPlayer viewer, CloseReason reason) {
        this.view = view;
        this.viewer = viewer;
        this.reason = reason;
    }

    @Nullable
    public FPlayer getViewer() {
        return viewer;
    }

    @Nonnull
    public Screen<?> getScreen() {
        return view.getScreen();
    }

    @Nonnull
    public ScreenView getView() {
        return view;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public L getLayout() {
        return (L) view.getScreen().getLayout();
    }

    @Nonnull
    public CloseReason getReason() {
        return reason;
    }

    /**
     * Whether the close came from the handler of the layout icon {@code selector} names - a "save and close" button,
     * as opposed to the escape key.
     */
    public boolean wasClosedBy(@Nonnull Function<L, Icon> selector) {
        Icon closedBy = view.getClosedBy();
        if (closedBy == null) {
            return false;
        }
        L layout = getLayout();
        if (layout == null) {
            throw new IllegalStateException("This screen has no layout, so the icon that closed it cannot be selected by "
                    + "field. Keep the icon in a field of the screen and ask wasClosedBy(thatIcon) instead.");
        }
        return isSameIcon(closedBy, selector.apply(layout));
    }

    public boolean wasClosedBy(@Nullable Icon icon) {
        return icon != null && isSameIcon(view.getClosedBy(), icon);
    }

    private static boolean isSameIcon(@Nullable Icon closedBy, @Nonnull Icon wanted) {
        if (closedBy == null) {
            return false;
        }
        if (closedBy == wanted) {
            return true;
        }
        String name = closedBy.getName();
        if (name != null && wanted.getName() != null) {
            return name.equals(wanted.getName());
        }
        //an icon declared on a raw slot has no layout key: the copy the screen drew answers to the one it was taken from
        return closedBy.getOrigin() == wanted.getOrigin();
    }

}
