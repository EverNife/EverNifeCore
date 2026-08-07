package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The screen as it was at the instant it went away, and why.
 *
 * <p>The container is still readable here, which is what makes this the place to give back whatever the
 * player left inside an editable area.</p>
 */
public final class CloseContext<L extends LayoutBase> {

    private final GuiView view;
    private final GuiSurface surface;
    private final Player viewer;
    private final CloseReason reason;

    CloseContext(GuiView view, GuiSurface surface, Player viewer, CloseReason reason) {
        this.view = view;
        this.surface = surface;
        this.viewer = viewer;
        this.reason = reason;
    }

    @Nullable
    public Player getViewer() {
        return viewer;
    }

    @Nonnull
    public Gui<?> getGui() {
        return view.getGui();
    }

    @Nonnull
    public GuiView getView() {
        return view;
    }

    /** The layout this screen was built from, or {@code null} when it was sized by hand. */
    @Nullable
    @SuppressWarnings("unchecked")
    public L getLayout() {
        return (L) view.getGui().getLayout();
    }

    @Nonnull
    public CloseReason getReason() {
        return reason;
    }

    /**
     * Whether the icon {@code selector} names is the one whose handler closed this screen - which is how
     * "saved" is told apart from "walked away".
     *
     * <p>{@link CloseReason} cannot answer this: a button that closes and the escape key both produce the
     * same {@code PLAYER_CLOSED}. What is compared is the icon the click came from, so an editor that
     * discards unless it was saved reads {@code if (!ctx.wasClosedBy(l -> l.SAVE))} and nothing else.</p>
     *
     * @throws IllegalStateException when the screen has no layout to select an icon from
     */
    public boolean wasClosedBy(@Nonnull Function<L, Icon> selector) {
        Icon closedBy = view.getClosedBy();
        if (closedBy == null) {
            return false;
        }
        L layout = getLayout();
        if (layout == null) {
            throw new IllegalStateException("This screen has no layout, so the icon that closed it cannot be "
                    + "selected by field. Keep the icon in a field of the screen and ask "
                    + "wasClosedBy(thatIcon) instead.");
        }
        return isSameIcon(closedBy, selector.apply(layout));
    }

    /** {@link #wasClosedBy(Function)} against an icon already in hand - what a screen with no layout has. */
    public boolean wasClosedBy(@Nullable Icon icon) {
        return icon != null && isSameIcon(view.getClosedBy(), icon);
    }

    /** What the container holds at those slots right now, empty slots included as {@code null}. */
    @Nonnull
    public List<ItemStack> getContents(@Nonnull SlotSet slots) {
        SlotSet resolved = slots.resolve(view.getGeometry());
        List<ItemStack> contents = new ArrayList<>(resolved.size());
        for (int slot : resolved.toArray()) {
            contents.add(surface.getItem(slot));
        }
        return contents;
    }

    /** What the whole container holds right now. */
    @Nonnull
    public List<ItemStack> getContents() {
        List<ItemStack> contents = new ArrayList<>(surface.getSize());
        for (int slot = 0; slot < surface.getSize(); slot++) {
            contents.add(surface.getItem(slot));
        }
        return contents;
    }

    /**
     * Two icons are the same one when they answer to the same layout key: a view draws its own copy of
     * every icon, so the copy that was clicked is never the instance the layout field holds.
     */
    private static boolean isSameIcon(@Nullable Icon closedBy, @Nonnull Icon wanted) {
        if (closedBy == null) {
            return false;
        }
        if (closedBy == wanted) {
            return true;
        }
        String name = closedBy.getName();
        return name != null && name.equals(wanted.getName());
    }

}
