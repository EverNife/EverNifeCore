package br.com.finalcraft.evernifecore.hytale.scheduler;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

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

    /**
     * A world whose clock runs at {@code tps}, built without running it: the constructor wires a whole
     * universe, and the only thing asked of it here is its configured rate.
     */
    private static World worldTickingAt(int tps) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        World world = (World) ((Unsafe) unsafeField.get(null)).allocateInstance(World.class);
        Field rate = TickingThread.class.getDeclaredField("tps");
        rate.setAccessible(true);
        rate.setInt(world, tps);
        return world;
    }

    @Test
    void aWorldIsTimedByItsOwnConfiguredRate() throws Exception {
        World fast = worldTickingAt(60);

        assertEquals(60, HyFCScheduler.tpsOf(fast));
        assertEquals(500L, HyFCScheduler.ticksToMillis(30, HyFCScheduler.tpsOf(fast)),
                "the delay scheduleSyncInTicks sleeps for 30 ticks of this world");
    }
}
