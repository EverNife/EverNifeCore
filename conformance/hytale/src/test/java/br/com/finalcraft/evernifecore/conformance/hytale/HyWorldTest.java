package br.com.finalcraft.evernifecore.conformance.hytale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HyWorldTest {

    @Test
    void theMedianTickIsTakenOverTheLastSamplesOnlyInMillis() {
        long[] nanos = {9_000_000, 1_000_000, 3_000_000, 2_000_000};
        assertEquals(2.0, HyWorld.medianMillis(nanos, 3));      //last three: 1, 3, 2 ms
        assertEquals(2.5, HyWorld.medianMillis(nanos, 10));     //all four: 1, 2, 3, 9 ms
        assertTrue(Double.isNaN(HyWorld.medianMillis(new long[0], 5)));
    }
}
