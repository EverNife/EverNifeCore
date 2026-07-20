package br.com.finalcraft.evernifecore.minecraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Exercises only the pure era selector; it never touches FCSound/MCVersion/MCDetailedVersion, which
// load the running server's version package and do not resolve headless.
public class FCSoundUtilTest {

    @Test
    public void picksPre1_9EraForShortValues17And18() {
        assertEquals("a", FCSoundUtil.pickByEra(17, "a", "b", "c"));
        assertEquals("a", FCSoundUtil.pickByEra(18, "a", "b", "c"));
    }

    @Test
    public void picks1_9To1_12EraForShortValues19Through112() {
        assertEquals("b", FCSoundUtil.pickByEra(19, "a", "b", "c"));
        assertEquals("b", FCSoundUtil.pickByEra(112, "a", "b", "c"));
    }

    @Test
    public void picksFlat1_13EraForShortValues113AndAbove() {
        assertEquals("c", FCSoundUtil.pickByEra(113, "a", "b", "c"));
        assertEquals("c", FCSoundUtil.pickByEra(121, "a", "b", "c"));
    }
}
