package br.com.finalcraft.evernifecore.hytale.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A tick lasts what the world's own clock says, and 30 a second when there is no world to ask. */
class HyFCSchedulerTicksTest {

    @Test
    void aSecondOfTicksIsASecondAtAnyRate() {
        assertEquals(1000L, HyFCScheduler.ticksToMillis(30, 30));
        assertEquals(1000L, HyFCScheduler.ticksToMillis(20, 20));
        assertEquals(500L, HyFCScheduler.ticksToMillis(30, 60));
    }

    @Test
    void withNoWorldTheServerDefaultOfThirtyAnswers() {
        assertEquals(30, HyFCScheduler.tpsOf(null));
    }
}
