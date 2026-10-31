package br.com.finalcraft.evernifecore.screen;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * The slot arithmetic every grid renderer shares: a patch is a slot and what it shows, so applying it is writing that
 * slot. The diff already happened in the view ({@link SlotBuffer}); a grid renderer only says how one slot is written
 * and read.
 */
public abstract class GridSurface implements ScreenSurface {

    private final Grid grid;

    protected GridSurface(@Nonnull Grid grid) {
        this.grid = grid;
    }

    @Nonnull
    public final Grid getGrid() {
        return grid;
    }

    /** Writes one slot; {@code null} empties it. Renderer thread. */
    protected abstract void writeSlot(int slot, @Nullable RenderedIcon icon);

    /** What the slot shows right now, as the renderer reads it back - for a resync after somebody else wrote it. */
    @Nullable
    protected abstract RenderedIcon readSlot(int slot);

    @Override
    public void apply(@Nonnull List<ElementPatch> patch) {
        for (ElementPatch each : patch) {
            if (grid.isInside(each.getSlot())) {
                writeSlot(each.getSlot(), each.getIcon());
            }
        }
    }

    /** {@link #readSlot(int)} for the view's resync. */
    @Nullable
    public final RenderedIcon read(int slot) {
        return readSlot(slot);
    }

}
