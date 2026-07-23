package br.com.finalcraft.evernifecore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FCArrayUtilTest {

    // ------------------------------------------------------------------
    // merge(T, T...) - first value plus varargs, in declaration order
    // ------------------------------------------------------------------

    @Test
    void merge_withNoExtraValues_returnsSingleElementArray() {
        String[] merged = FCArrayUtil.merge("a");

        assertArrayEquals(new String[]{"a"}, merged);
    }

    @Test
    void merge_withOneExtraValue_appendsItAfterTheFirst() {
        String[] merged = FCArrayUtil.merge("a", "b");

        assertArrayEquals(new String[]{"a", "b"}, merged);
    }

    @Test
    void merge_withSeveralExtraValues_preservesOrderAndLength() {
        String[] merged = FCArrayUtil.merge("a", "b", "c", "d");

        assertEquals(4, merged.length);
        assertArrayEquals(new String[]{"a", "b", "c", "d"}, merged);
    }

    // ------------------------------------------------------------------
    // mergeArray(T[], T...) - base array plus varargs, in declaration order.
    // The result must be a plain T[] (one dimension), never T[][].
    // ------------------------------------------------------------------

    @Test
    void mergeArray_withNoExtraValues_copiesTheOriginal() {
        String[] merged = FCArrayUtil.mergeArray(new String[]{"a", "b"});

        assertArrayEquals(new String[]{"a", "b"}, merged);
        assertEquals(String.class, merged.getClass().getComponentType());
    }

    @Test
    void mergeArray_withOneExtraValue_appendsIt() {
        String[] merged = FCArrayUtil.mergeArray(new String[]{"a"}, "b");

        assertArrayEquals(new String[]{"a", "b"}, merged);
    }

    @Test
    void mergeArray_withSeveralExtraValues_preservesOrderAndLength() {
        String[] merged = FCArrayUtil.mergeArray(new String[]{"a", "b"}, "c", "d");

        assertEquals(4, merged.length);
        assertArrayEquals(new String[]{"a", "b", "c", "d"}, merged);
    }

    @Test
    void mergeArray_withEmptyBaseArray_stillProducesAFlatTypedArray() {
        String[] merged = FCArrayUtil.mergeArray(new String[]{}, "a", "b");

        assertArrayEquals(new String[]{"a", "b"}, merged);
        assertEquals(String.class, merged.getClass().getComponentType());
    }
}
