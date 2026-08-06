package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen as it was at the instant it went away, and why.
 *
 * <p>The container is still readable here, which is what makes this the place to give back whatever
 * the player left inside an editable area.</p>
 */
public final class CloseContext {

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
    public Gui getGui() {
        return view.getGui();
    }

    @Nonnull
    public GuiView getView() {
        return view;
    }

    @Nonnull
    public CloseReason getReason() {
        return reason;
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

}
