package br.com.finalcraft.evernifecore.item;

import br.com.finalcraft.evernifecore.logic.delivery.PendingDestination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The data value of an item - a pre-1.13 wool's colour, a tool's damage - as a spec carries it and a matcher compares it. */
class ItemMatcherDataTest {

    private static final ItemSpec WHITE_WOOL = ItemSpec.of("minecraft:wool", 1).withData(0);
    private static final ItemSpec RED_WOOL = ItemSpec.of("minecraft:wool", 1).withData(14);

    @Test
    void aMatcherWithDataMatchesOnlyThatDataValue() {
        ItemMatcher red = ItemMatcher.of("minecraft:wool", 14);

        assertTrue(red.matches(RED_WOOL));
        assertFalse(red.matches(WHITE_WOOL), "white wool is data 0, not the red 14");
    }

    @Test
    void anUnspecifiedDataOnEitherSideMatchesAnyData() {
        assertTrue(ItemMatcher.of("minecraft:wool").matches(RED_WOOL), "a matcher without data takes every colour");
        assertTrue(ItemMatcher.of("minecraft:wool", -1).matches(WHITE_WOOL));
        assertTrue(ItemMatcher.of("minecraft:wool", 14).matches(ItemSpec.of("minecraft:wool", 1)),
                "an item of a platform without data values (-1) is not refused for it");
    }

    @Test
    void theDataIsPartOfTheSpecAndTravelsWithEveryCopy() {
        assertEquals(-1, ItemSpec.of("minecraft:wool", 1).getData());
        assertEquals(14, RED_WOOL.withAmount(5).withText("Red", java.util.Collections.<String>emptyList()).getData());
        assertNotEquals(WHITE_WOOL, RED_WOOL);
        assertNotEquals(ItemSpec.of("minecraft:wool", 1), WHITE_WOOL, "unspecified is not data 0");
        assertEquals(RED_WOOL, ItemSpec.of("minecraft:wool", 1).withData(14));
        assertEquals(RED_WOOL.hashCode(), ItemSpec.of("minecraft:wool", 1).withData(14).hashCode());
    }

    @Test
    void anExactMatcherAndTheItemItHandsBackKeepTheData() {
        ItemMatcher exact = ItemMatcher.exact(RED_WOOL);

        assertFalse(exact.matches(WHITE_WOOL));
        assertEquals(14, exact.toItem(3).getData(), "what is handed back after a refund is the red wool taken");
    }

    @Test
    void aPendingItemKeepsItsData() {
        assertEquals(14, PendingDestination.giveItemNode(RED_WOOL, null).get("data").asInt());
        assertFalse(PendingDestination.giveItemNode(ItemSpec.of("minecraft:wool", 1), null).has("data"), "unspecified writes nothing");
    }

    @Test
    void aDataBelowMinusOneIsRefused() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> RED_WOOL.withData(-2));
        assertTrue(refused.getMessage().contains("-1 for none"), refused.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ItemMatcher.of("minecraft:wool", -2));
    }
}
