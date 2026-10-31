package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.screen.Grid;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Screen;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * The container the chest renderer asks the server for. A screen picks one by its grid: rows of nine are a
 * {@link #CHEST}, and {@code ChestKind.HOPPER.newScreen()} is a screen over a hopper's window.
 *
 * <p>Every constant maps to a plain vanilla container type, so all of them work from 1.7.10 up without a line of NMS.</p>
 *
 * <p>The size here is the nominal one; the window the server creates is the authority - {@link #BREWING} grew a fuel
 * slot in 1.9, and the slot past the grid is simply never drawn.</p>
 */
public enum ChestKind {

    /** 1 to 6 rows of 9. The only kind whose size is chosen by the caller. */
    CHEST(9, 54),
    /** A single row of 5. */
    HOPPER(5, 5),
    /** A 3x3 grid. */
    DISPENSER(3, 9),
    /** 4 slots on 1.7-1.8, 5 from 1.9 on (fuel). */
    BREWING(4, 4),
    /**
     * The crafting result plus a 3x3 grid, in that order: slot 0 is the RESULT, so row 1 starts on it and the grid is
     * {@code Slots.at(1, 2)} through {@code Slots.at(4, 1)}.
     */
    WORKBENCH(3, 10);

    private final int width;
    private final int defaultSize;

    ChestKind(int width, int defaultSize) {
        this.width = width;
        this.defaultSize = defaultSize;
    }

    /** The kind whose window is {@code grid}, or {@code null} when no container has that shape. */
    @Nullable
    public static ChestKind of(@Nonnull Grid grid) {
        if (grid.getWidth() == Grid.CHEST_WIDTH) {
            return grid.getSize() % Grid.CHEST_WIDTH == 0 && grid.getRows() >= Grid.MIN_ROWS
                    && grid.getRows() <= Grid.MAX_ROWS ? CHEST : null;
        }
        for (ChestKind kind : values()) {
            if (kind != CHEST && kind.getGrid().equals(grid)) {
                return kind;
            }
        }
        return null;
    }

    /** Every kind's name, comma separated - what a message offering the roster is built from. */
    @Nonnull
    public static String names() {
        StringBuilder roster = new StringBuilder();
        for (ChestKind kind : values()) {
            if (roster.length() > 0) {
                roster.append(", ");
            }
            roster.append(kind.name());
        }
        return roster.toString();
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

    /** The grid of this kind; a {@link #CHEST} answers its largest, six rows. */
    @Nonnull
    public Grid getGrid() {
        return Grid.of(width, defaultSize);
    }

    /** The grid of {@code rows} rows - only a {@link #CHEST} varies, every other kind answers {@link #getGrid()}. */
    @Nonnull
    public Grid getGrid(int rows) {
        return isChest() ? Grid.rows(rows) : getGrid();
    }

    /** A screen over this kind's window; a {@link #CHEST} is three rows - {@code Screen.of(rows)} sizes one by hand. */
    @Nonnull
    public Screen<Layout> newScreen() {
        return Screen.of(getGrid(3));
    }

}
