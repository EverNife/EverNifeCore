package br.com.finalcraft.evernifecore.screen;

import jakarta.annotation.Nonnull;

/**
 * What a component's render function draws with: icons onto slots, on the component's own layer. Everything written
 * here lands in the view's buffer; the surface sees only the slots that changed, once per tick.
 */
public final class SlotWriter {

    private final ScreenView view;
    private final int layer;

    public SlotWriter(@Nonnull ScreenView view, int layer) {
        this.view = view;
        this.layer = layer;
    }

    /** Paints a decoration - an icon with no behaviour - on one raw slot. */
    @Nonnull
    public SlotWriter icon(int slot, @Nonnull Icon icon) {
        return icon(Slots.of(slot), icon);
    }

    /** Paints a decoration on every slot of {@code slots}; its id is the icon's layout name, or {@code slot#<n>}. */
    @Nonnull
    public SlotWriter icon(@Nonnull SlotSet slots, @Nonnull Icon icon) {
        view.paint(layer, slots, icon, null);
        return this;
    }

    /** Paints {@code icon} as {@code paint} says - the element that owns these slots, and what a click on them does. */
    @Nonnull
    public SlotWriter paint(@Nonnull Paint paint, @Nonnull SlotSet slots, @Nonnull Icon icon) {
        view.paint(layer, slots, icon, paint);
        return this;
    }

    @Nonnull
    public Grid getGrid() {
        return view.getGrid();
    }

    @Nonnull
    public ScreenView getView() {
        return view;
    }

}
