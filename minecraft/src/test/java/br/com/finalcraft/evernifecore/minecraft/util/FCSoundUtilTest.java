package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Exercises only the pure era selector; it never touches FCSound/MCVersion.getCurrent(), which read
// the running server's version and do not resolve headless.
public class FCSoundUtilTest {

    @Test
    public void picksPre1_9EraBelow1_9() {
        assertEquals("a", FCSoundUtil.pickByEra(MCDetailedVersion.v1_7_R4, "a", "b", "c"));
        assertEquals("a", FCSoundUtil.pickByEra(MCDetailedVersion.v1_8_R3, "a", "b", "c"));
    }

    @Test
    public void picks1_9To1_12EraUpTo1_12() {
        assertEquals("b", FCSoundUtil.pickByEra(MCDetailedVersion.v1_9_R1, "a", "b", "c"));
        assertEquals("b", FCSoundUtil.pickByEra(MCDetailedVersion.v1_12_R1, "a", "b", "c"));
    }

    @Test
    public void picksFlat1_13EraFrom1_13Onwards() {
        assertEquals("c", FCSoundUtil.pickByEra(MCDetailedVersion.v1_13_R1, "a", "b", "c"));
        assertEquals("c", FCSoundUtil.pickByEra(MCDetailedVersion.v1_21_R7, "a", "b", "c"));
        assertEquals("c", FCSoundUtil.pickByEra(MCDetailedVersion.v26_2, "a", "b", "c"));
    }
}
