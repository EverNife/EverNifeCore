package br.com.finalcraft.evernifecore.minecraft.gui.model;

/**
 * The measurements a {@link SlotSet} needs to turn a shape ("column 3", "the border") into raw slot
 * indexes: how many slots the window has and how many of them sit on one row.
 *
 * <p>Width cannot be derived from size - a 9-slot window is a one-row chest (width 9) or a dispenser
 * (width 3) - which is why the shape resolves against this pair instead of a bare int.</p>
 */
public final class GuiGeometry {

    private final GuiType type;
    private final int size;

    public GuiGeometry(GuiType type, int size) {
        if (type == null) {
            throw new IllegalArgumentException("GuiGeometry needs a GuiType.");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("A gui cannot have [" + size + "] slots.");
        }
        this.type = type;
        this.size = size;
    }

    public GuiType getType() {
        return type;
    }

    public int getSize() {
        return size;
    }

    public int getWidth() {
        return type.getWidth();
    }

    /** Rows, rounded up: the last row of a window whose size is not a multiple of the width is partial. */
    public int getRows() {
        int width = getWidth();
        return (size + width - 1) / width;
    }

    public boolean isInside(int slot) {
        return slot >= 0 && slot < size;
    }

    @Override
    public String toString() {
        return "GuiGeometry{" + type + ", size=" + size + ", width=" + getWidth() + "}";
    }

}
