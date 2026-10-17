package br.com.finalcraft.evernifecore.conformance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickTimesTest {

    private static TickTimes of(long... millis) {
        TickTimes times = new TickTimes();
        for (long tick : millis) times.add(tick * 1_000_000L);
        return times;
    }

    @Test
    void summarizesTheTicks() {
        TickTimes times = of(4, 1, 3, 2);

        assertEquals(4, times.getTicks());
        assertEquals(2.5, times.getMedianMillis(), 1e-9);
        assertEquals(4.0, times.getMaxMillis(), 1e-9);
        assertEquals(10.0, times.getWorkMillis(), 1e-9);
        assertEquals("median=2.500ms max=4.000ms ticks=4 work=10.0ms wall=200ms", times.describe(200));
    }

    @Test
    void theMeanOfTheLastTicksIgnoresTheFirstOnes() {
        TickTimes times = of(100, 2, 4);

        assertEquals(3.0, times.getMeanOfLastMillis(2), 1e-9);
        assertEquals(106 / 3.0, times.getMeanOfLastMillis(10), 1e-9);
    }

    @Test
    void noTickAnswersNotANumber() {
        TickTimes times = new TickTimes();

        assertTrue(Double.isNaN(times.getMedianMillis()));
        assertTrue(Double.isNaN(times.getMaxMillis()));
        assertEquals(0, times.getTicks());
    }
}
