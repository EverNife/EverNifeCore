package br.com.finalcraft.evernifecore.minecraft.gui.model;

/**
 * The geometry vocabulary of a gui.
 *
 * <p>Two counting bases live here and they never share a method name: {@link #of(int...)} takes raw
 * slots, which are 0-based, and every {@code row}/{@code column} argument is 1-based, which is what
 * the method names say. That is the whole reason {@link #at(int, int)} is not an overload of
 * {@code of}.</p>
 *
 * <p>Everything except {@link #of(int...)} is relative: it becomes indexes only once the window is
 * measured, so the same {@code Slots.border()} means the border of a 3-row chest and of a hopper.</p>
 */
public final class Slots {

    private Slots() {

    }

    /** Raw, 0-based slots, in the order given. */
    public static SlotSet of(int... slots) {
        return SlotSet.of(slots);
    }

    /** One slot, addressed as row and column, both 1-based. */
    public static SlotSet at(int row, int column) {
        return SlotSet.relative(geometry -> new int[]{indexOf(geometry, row, column)});
    }

    /** Every slot of the rectangle whose corners are the two 1-based row/column pairs, inclusive. */
    public static SlotSet box(int firstRow, int firstColumn, int lastRow, int lastColumn) {
        return SlotSet.relative(geometry -> {
            int rowStart = Math.min(firstRow, lastRow);
            int rowEnd = Math.max(firstRow, lastRow);
            int columnStart = Math.min(firstColumn, lastColumn);
            int columnEnd = Math.max(firstColumn, lastColumn);

            int[] result = new int[(rowEnd - rowStart + 1) * (columnEnd - columnStart + 1)];
            int index = 0;
            for (int row = rowStart; row <= rowEnd; row++) {
                for (int column = columnStart; column <= columnEnd; column++) {
                    result[index++] = indexOf(geometry, row, column);
                }
            }
            return result;
        });
    }

    /** A whole row, 1-based. */
    public static SlotSet row(int row) {
        return SlotSet.relative(geometry -> {
            int width = geometry.getWidth();
            int[] result = new int[width];
            for (int column = 1; column <= width; column++) {
                result[column - 1] = indexOf(geometry, row, column);
            }
            return result;
        });
    }

    /** A whole column, 1-based. */
    public static SlotSet column(int column) {
        return SlotSet.relative(geometry -> {
            int rows = geometry.getRows();
            int[] result = new int[rows];
            for (int row = 1; row <= rows; row++) {
                result[row - 1] = indexOf(geometry, row, column);
            }
            return result;
        });
    }

    /** The outermost ring of the window. A single-row window is entirely border. */
    public static SlotSet border() {
        return SlotSet.relative(geometry -> {
            int rows = geometry.getRows();
            int width = geometry.getWidth();
            int[] buffer = new int[geometry.getSize()];
            int index = 0;
            for (int row = 1; row <= rows; row++) {
                for (int column = 1; column <= width; column++) {
                    if (row == 1 || row == rows || column == 1 || column == width) {
                        int slot = (row - 1) * width + (column - 1);
                        if (geometry.isInside(slot)) {
                            buffer[index++] = slot;
                        }
                    }
                }
            }
            int[] result = new int[index];
            System.arraycopy(buffer, 0, result, 0, index);
            return result;
        });
    }

    /** Every slot of the window. */
    public static SlotSet all() {
        return SlotSet.relative(geometry -> {
            int[] result = new int[geometry.getSize()];
            for (int slot = 0; slot < result.length; slot++) {
                result[slot] = slot;
            }
            return result;
        });
    }

    private static int indexOf(GuiGeometry geometry, int row, int column) {
        int width = geometry.getWidth();
        if (row < 1 || column < 1 || column > width) {
            throw new IllegalArgumentException("Row " + row + " column " + column + " is outside a "
                    + geometry + ". Rows and columns start at 1 and the column stops at " + width + ".");
        }
        int slot = (row - 1) * width + (column - 1);
        if (!geometry.isInside(slot)) {
            throw new IllegalArgumentException("Row " + row + " column " + column + " lands on slot "
                    + slot + ", outside a " + geometry + ".");
        }
        return slot;
    }

}
