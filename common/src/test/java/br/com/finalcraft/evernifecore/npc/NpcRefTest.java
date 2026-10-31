package br.com.finalcraft.evernifecore.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code provider:id}: read, written back, lowercased, and a malformed one refused with the shape it expects. */
class NpcRefTest {

    @Test
    void parseAndToStringGoRoundTrip() {
        NpcRef ref = NpcRef.parse("citizens:12");
        assertEquals("citizens", ref.getProvider());
        assertEquals("12", ref.getId());
        assertEquals(ref, NpcRef.parse(ref.toString()));
        assertEquals(NpcRef.of("citizens", "12"), ref);
    }

    @Test
    void bothHalvesAreLowercasedSoCaseNeverSplitsOneNpcInTwo() {
        NpcRef ref = NpcRef.parse("ZNPCsPlus:Merchant");
        assertEquals("znpcsplus:merchant", ref.toString());
        assertEquals(NpcRef.of("znpcsplus", "merchant"), ref);
        assertEquals(ref.hashCode(), NpcRef.of("ZNPCSPLUS", "MERCHANT").hashCode());
    }

    @Test
    void anIdKeepsEveryColonAfterTheFirst() {
        assertEquals("a:b", NpcRef.parse("pixelmon:a:b").getId());
    }

    @Test
    void aMalformedTextIsRefusedNamingTheShapeExpected() {
        for (String bad : new String[]{"citizens", ":12", "citizens:", "bad provider:1", "citizens:1 2"}) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> NpcRef.parse(bad), bad);
            assertTrue(refused.getMessage().contains("provider:id"), refused.getMessage());
        }
    }
}
