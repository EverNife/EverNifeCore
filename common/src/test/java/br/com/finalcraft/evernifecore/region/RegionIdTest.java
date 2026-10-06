package br.com.finalcraft.evernifecore.region;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionIdTest {

    @Test
    void aValidIdRoundTripsThroughItsTextForm() {
        RegionId id = RegionId.parse("theduel:arena_1-b");

        assertEquals("theduel", id.getOwner());
        assertEquals("arena_1-b", id.getName());
        assertEquals("theduel:arena_1-b", id.toString());
        assertEquals(id, RegionId.of("theduel", "arena_1-b"));
    }

    @Test
    void upperCaseIsNormalizedAndComparesEqual() {
        RegionId mixed = RegionId.parse("TheDuel:Dragon");

        assertEquals("theduel:dragon", mixed.toString());
        assertEquals(RegionId.of("theduel", "dragon"), mixed);
        assertEquals(RegionId.of("theduel", "dragon").hashCode(), mixed.hashCode());
        assertEquals(0, mixed.compareTo(RegionId.parse("theduel:dragon")));
    }

    @Test
    void anInvalidCharacterIsRefusedWithTheRuleAndAnExample() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> RegionId.of("theduel", "dragon lair"));

        assertMessageTeaches(refused, "dragon lair");
    }

    @Test
    void aNameOf65CharactersIsRefused() {
        char[] chars = new char[65];
        Arrays.fill(chars, 'a');
        String tooLong = new String(chars);

        assertEquals(64, RegionId.of("theduel", tooLong.substring(1)).getName().length());
        assertMessageTeaches(assertThrows(IllegalArgumentException.class, () -> RegionId.of("theduel", tooLong)), tooLong);
    }

    @Test
    void textWithoutAColonIsRefused() {
        assertMessageTeaches(assertThrows(IllegalArgumentException.class, () -> RegionId.parse("dragon")), "dragon");
    }

    @Test
    void textWithTwoColonsIsRefused() {
        assertMessageTeaches(assertThrows(IllegalArgumentException.class, () -> RegionId.parse("theduel:dragon:2")), "theduel:dragon:2");
    }

    @Test
    void idsSortByTheirTextForm() {
        List<RegionId> ids = Arrays.asList(RegionId.parse("b:a"), RegionId.parse("a:b"), RegionId.parse("a:a"));
        ids.sort(null);

        assertEquals("[a:a, a:b, b:a]", ids.toString());
    }

    private static void assertMessageTeaches(IllegalArgumentException refused, String received) {
        String message = refused.getMessage();
        assertTrue(message.contains("'" + received + "'"), "the message must quote what it got: " + message);
        assertTrue(message.contains("[a-z0-9_-]{1,64}"), "the message must state the rule: " + message);
        assertTrue(message.contains("e.g. '"), "the message must show a valid example: " + message);
    }
}
