package br.com.finalcraft.evernifecore.screen;

/**
 * The measurements a {@link SlotSet} needs to turn a shape ("column 3", "the border") into raw slot
 * indexes: how many slots the window has and how many of them sit on one row.
 *
 * <p>Width cannot be derived from size - a 9-slot window is a one-row chest (width 9) or a dispenser
 * (width 3) - which is why the shape resolves against this pair instead of a bare int.</p>
 */
public final class Grid {

    /** Slots in one row of a chest. */
    public static final int CHEST_WIDTH = 9;
    public static final int MIN_ROWS = 1;
    public static final int MAX_ROWS = 6;

    private final int width;
    private final int size;

    private Grid(int width, int size) {
        if (width <= 0) {
            throw new IllegalArgumentException("A screen grid cannot be [" + width + "] slots wide.");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("A screen cannot have [" + size + "] slots.");
        }
        this.width = width;
        this.size = size;
    }

    public static Grid of(int width, int size) {
        return new Grid(width, size);
    }

    /** A grid of {@code rows} rows of nine. */
    public static Grid rows(int rows) {
        if (rows < MIN_ROWS || rows > MAX_ROWS) {
            throw new IllegalArgumentException("A screen has " + MIN_ROWS + " to " + MAX_ROWS + " rows of nine, not ["
                    + rows + "]. Screen.of(3) is the usual size.");
        }
        return new Grid(CHEST_WIDTH, rows * CHEST_WIDTH);
    }

    public int getSize() {
        return size;
    }

    public int getWidth() {
        return width;
    }

    public int getRows() {
        return (size + width - 1) / width;
    }

    public boolean isInside(int slot) {
        return slot >= 0 && slot < size;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Grid && ((Grid) o).width == width && ((Grid) o).size == size;
    }

    @Override
    public int hashCode() {
        return 31 * width + size;
    }

    @Override
    public String toString() {
        return "Grid{size=" + size + ", width=" + width + "}";
    }

}
