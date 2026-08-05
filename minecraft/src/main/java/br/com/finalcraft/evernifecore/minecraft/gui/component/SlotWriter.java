package br.com.finalcraft.evernifecore.minecraft.gui.component;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiGeometry;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import jakarta.annotation.Nonnull;

/**
 * What a component's render function writes into. Everything written here lands on that component's
 * own layer, so re-rendering it erases only what it drew and uncovers whatever is underneath.
 *
 * <p>It writes into the virtual buffer, never into the container: the commit at the end of the tick
 * is what decides which slots are actually worth a write.</p>
 */
public final class SlotWriter {

    private final GuiView view;
    private final int layer;

    public SlotWriter(@Nonnull GuiView view, int layer) {
        this.view = view;
        this.layer = layer;
    }

    @Nonnull
    public SlotWriter icon(int slot, @Nonnull Icon icon) {
        return icon(Slots.of(slot), icon);
    }

    @Nonnull
    public SlotWriter icon(@Nonnull SlotSet slots, @Nonnull Icon icon) {
        view.paint(layer, slots, icon);
        return this;
    }

    /** The measurements of the window being drawn - how many slots, how wide. */
    @Nonnull
    public GuiGeometry getGeometry() {
        return view.getGeometry();
    }

    @Nonnull
    public GuiView getView() {
        return view;
    }

}
