package br.com.finalcraft.evernifecore.minecraft.gui.model;

/**
 * The window a {@link br.com.finalcraft.evernifecore.minecraft.gui.Gui} asks the server for.
 *
 * <p>Every constant maps to a plain vanilla container type, so all of them work from 1.7.10 up
 * without a single line of NMS. Anvil, enchanting table and cartography are deliberately absent:
 * they would need a per-version NMS matrix that the 1.7.10 floor makes unpayable. Free text input
 * goes through a chat prompt instead.</p>
 *
 * <p>{@link #getDefaultSize()} is the nominal size used before a window exists. The authoritative
 * size is always the one the created window reports - {@link #BREWING} in particular grew a fuel
 * slot in 1.9.</p>
 */
public enum GuiType {

    /** 1 to 6 rows of 9. The only type whose size is chosen by the caller. */
    CHEST(9, 54),
    /** A single row of 5. */
    HOPPER(5, 5),
    /** A 3x3 grid. */
    DISPENSER(3, 9),
    /** 4 slots on 1.7-1.8, 5 from 1.9 on (fuel). */
    BREWING(4, 4),
    /**
     * The crafting result plus a 3x3 grid, in that order: slot 0 is the RESULT, so row 1 starts on it
     * and the grid is {@code Slots.at(1, 2)} through {@code Slots.at(4, 1)}. Row 4 holds one slot -
     * {@code Slots.row(4)} answers that one and nothing else.
     */
    WORKBENCH(3, 10);

    public static final int MIN_CHEST_ROWS = 1;
    public static final int MAX_CHEST_ROWS = 6;

    private final int width;
    private final int defaultSize;

    GuiType(int width, int defaultSize) {
        this.width = width;
        this.defaultSize = defaultSize;
    }

    /** Slots per row, which is what {@code Slots.at(row, column)} counts with. */
    public int getWidth() {
        return width;
    }

    public int getDefaultSize() {
        return defaultSize;
    }

    public boolean isChest() {
        return this == CHEST;
    }

    /**
     * The slot count this type occupies with {@code rows} rows. Only {@link #CHEST} varies; every
     * other type ignores the argument and answers {@link #getDefaultSize()}.
     *
     * @throws IllegalArgumentException when a chest is asked for a row count outside 1..6
     */
    public int sizeOf(int rows) {
        if (!isChest()) {
            return defaultSize;
        }
        if (rows < MIN_CHEST_ROWS || rows > MAX_CHEST_ROWS) {
            throw new IllegalArgumentException("A CHEST gui has " + MIN_CHEST_ROWS + " to " + MAX_CHEST_ROWS
                    + " rows, got [" + rows + "]. Pick a row count in range, or use another GuiType "
                    + "(HOPPER, DISPENSER, BREWING, WORKBENCH) for the smaller windows.");
        }
        return rows * width;
    }

}
