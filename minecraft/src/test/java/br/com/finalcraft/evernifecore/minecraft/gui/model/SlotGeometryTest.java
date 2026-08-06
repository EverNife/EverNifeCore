package br.com.finalcraft.evernifecore.minecraft.gui.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two counting bases of the slot vocabulary, and the shapes that only mean something once a
 * window has been measured.
 *
 * <p>The whole risk this pins down is the ambiguity that would come from one method accepting both
 * bases: {@code Slots.of(1)} is the second slot of the window and {@code Slots.at(1, 1)} is the
 * first. Every assertion here that names a number names it in one base on purpose.</p>
 */
class SlotGeometryTest {

    private static final GuiGeometry CHEST_3 = new GuiGeometry(GuiType.CHEST, 27);
    private static final GuiGeometry CHEST_6 = new GuiGeometry(GuiType.CHEST, 54);
    private static final GuiGeometry HOPPER = new GuiGeometry(GuiType.HOPPER, 5);
    private static final GuiGeometry DISPENSER = new GuiGeometry(GuiType.DISPENSER, 9);

    private static int[] resolve(SlotSet set, GuiGeometry geometry) {
        return set.resolve(geometry).toArray();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The two bases
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void atIsTheOnlyOneBasedForm() {
        assertArrayEquals(new int[]{0}, resolve(Slots.at(1, 1), CHEST_3),
                "row 1 column 1 is the first slot, which is raw slot 0");
        assertArrayEquals(new int[]{1}, Slots.of(1).toArray(),
                "of() speaks raw slots, so 1 is the SECOND slot - the ambiguity at() exists to avoid");
    }

    @Test
    void atWalksTheGridRowMajor() {
        assertArrayEquals(new int[]{13}, resolve(Slots.at(2, 5), CHEST_3));
        assertArrayEquals(new int[]{26}, resolve(Slots.at(3, 9), CHEST_3));
    }

    @Test
    void aSlotOutsideTheWindowIsRefusedWhereItIsWritten() {
        assertThrows(IllegalArgumentException.class, () -> resolve(Slots.at(0, 1), CHEST_3),
                "rows start at 1");
        assertThrows(IllegalArgumentException.class, () -> resolve(Slots.at(1, 10), CHEST_3),
                "a chest row stops at column 9");
        assertThrows(IllegalArgumentException.class, () -> resolve(Slots.at(4, 1), CHEST_3),
                "a 3-row chest has no fourth row");
    }

    @Test
    void aRawSlotIsNeverNegative() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Slots.of(-1));
        assertTrue(error.getMessage().contains("Slots.at(row, column)"),
                "the message points at the 1-based form, which is what the caller probably meant: "
                        + error.getMessage());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Shapes, counted exactly
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void rowsAndColumnsAreMeasuredAgainstTheWindow() {
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, resolve(Slots.row(1), CHEST_3));
        assertArrayEquals(new int[]{18, 19, 20, 21, 22, 23, 24, 25, 26}, resolve(Slots.row(3), CHEST_3));
        assertArrayEquals(new int[]{0, 9, 18}, resolve(Slots.column(1), CHEST_3));
        assertArrayEquals(new int[]{8, 17, 26}, resolve(Slots.column(9), CHEST_3));

        assertEquals(6, resolve(Slots.column(5), CHEST_6).length, "a 6-row chest column is 6 slots deep");
    }

    @Test
    void boxIsTheInclusiveRectangle() {
        assertArrayEquals(new int[]{10, 11, 12}, resolve(Slots.box(2, 2, 2, 4), CHEST_3));
        assertEquals(28, resolve(Slots.box(2, 2, 5, 8), CHEST_6).length, "4 rows by 7 columns");
        assertArrayEquals(resolve(Slots.box(2, 2, 5, 8), CHEST_6), resolve(Slots.box(5, 8, 2, 2), CHEST_6),
                "the corners may be given in any order");
    }

    @Test
    void borderAndAllHaveExactCountsPerWindow() {
        assertEquals(27, resolve(Slots.all(), CHEST_3).length);
        assertEquals(54, resolve(Slots.all(), CHEST_6).length);

        //3 rows: both full rows plus the two ends of the middle one
        assertEquals(20, resolve(Slots.border(), CHEST_3).length);
        //6 rows: both full rows plus the two ends of each of the four middle ones
        assertEquals(26, resolve(Slots.border(), CHEST_6).length);
    }

    @Test
    void aSingleRowWindowIsEntirelyBorder() {
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, resolve(Slots.border(), HOPPER));
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, resolve(Slots.all(), HOPPER));
    }

    @Test
    void theSameShapeMeansDifferentSlotsInADifferentWindow() {
        //9 slots is a one-row chest OR a dispenser, and the two disagree on everything but the count
        assertArrayEquals(new int[]{4}, resolve(Slots.at(2, 2), DISPENSER));
        assertArrayEquals(new int[]{0, 1, 2, 3, 5, 6, 7, 8}, resolve(Slots.border(), DISPENSER),
                "the 3x3 grid is all border except its centre");
        assertArrayEquals(new int[]{2}, resolve(Slots.at(1, 3), HOPPER));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The set itself
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aSetKeepsDeclarationOrderAndDropsRepeats() {
        assertArrayEquals(new int[]{5, 2, 8}, Slots.of(5, 2, 8, 5, 2).toArray());
    }

    @Test
    void aShapeRefusesToBeReadBeforeItIsMeasured() {
        SlotSet shape = Slots.border();
        assertTrue(shape.isRelative());
        IllegalStateException error = assertThrows(IllegalStateException.class, shape::toArray);
        assertTrue(error.getMessage().contains("resolve(GuiGeometry)"), error.getMessage());

        assertFalse(Slots.of(1, 2).isRelative());
        assertEquals(Slots.of(1, 2), Slots.of(1, 2).resolve(CHEST_3), "resolving a fixed set is the identity");
    }

    @Test
    void aChestSizeOutsideOneToSixIsRefusedWithTheWayOut() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> GuiType.CHEST.sizeOf(7));
        assertTrue(error.getMessage().contains("HOPPER"),
                "the message names the smaller windows, which is what a caller asking for 7 rows needs: "
                        + error.getMessage());
        assertEquals(9, GuiType.CHEST.sizeOf(1));
        assertEquals(54, GuiType.CHEST.sizeOf(6));
        assertEquals(5, GuiType.HOPPER.sizeOf(6), "only a chest reads the row count");
    }

}
