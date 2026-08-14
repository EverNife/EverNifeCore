package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Bukkit end of the mirror: an event posted on the bus arriving at a listener registered with the
 * server, and the one case where it deliberately does not - a producer whose thread disagrees with
 * the flag its event carries.
 */
class McBukkitAudienceTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void anECEventPostedOnTheGlobalBusReachesABukkitListener() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            List<SampleEvent> heard = new ArrayList<>();
            world.listen(SampleEvent.class, heard::add);

            SampleEvent posted = ECEventBus.global().post(new SampleEvent());

            assertEquals(1, heard.size(), "the audience carried the post all the way into the server");
            assertSame(posted, heard.get(0), "the listener gets the instance the producer posted, not a copy");
        }
    }

    @Test
    void theGateOpensForTheWholeFamilyBecauseTheHandlerListIsShared() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            SampleEvent event = new SampleEvent();

            assertFalse(world.getAudience().hasListeners(event), "nothing is registered with this server yet");

            world.listen(SiblingEvent.class, sibling -> {
            });

            assertTrue(world.getAudience().hasListeners(event), "every EC event answers to one HandlerList, "
                    + "so a listener for a sibling is enough to open the gate - the executor filters by type later");
        }
    }

    @Test
    void anAsyncEventPostedFromTheMainThreadSkipsOnlyTheNativePhase() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            List<String> heard = new ArrayList<>();
            world.subscribe(AlwaysAsyncEvent.class, event -> heard.add("bus"));
            world.listen(AlwaysAsyncEvent.class, event -> heard.add("bukkit"));

            //the real plugin manager refuses an asynchronous event fired from the main thread; the
            //guard is what keeps that refusal from reaching the producer
            List<String> logged = Logs.capture(() -> ECEventBus.global().post(new AlwaysAsyncEvent()));

            assertEquals(Collections.singletonList("bus"), heard, "the local phase ran, the native one was skipped");
            assertTrue(logged.stream().anyMatch(line ->
                            line.contains("was built asynchronous but posted on the main thread")),
                    "the skip is logged, never silent: " + logged);
            assertTrue(logged.stream().anyMatch(line -> line.contains("McBukkitAudience")),
                    "the stack that comes with it is what points at the producer: " + logged);
        }
    }

    @Test
    void aSyncEventPostedOffTheMainThreadSkipsOnlyTheNativePhase() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            List<String> heard = new CopyOnWriteArrayList<>();
            world.subscribe(SampleEvent.class, event -> heard.add("bus"));
            world.listen(SampleEvent.class, event -> heard.add("bukkit"));

            //built where isPrimaryThread() answers true, so it carries the synchronous flag, and then
            //handed to a worker to post - the hazard the guard exists for
            SampleEvent event = new SampleEvent();
            List<String> logged = Logs.capture(() -> world.offTheMainThread(() -> ECEventBus.global().post(event)));

            assertEquals(Collections.singletonList("bus"), new ArrayList<>(heard),
                    "the local phase ran on the worker, the native one was skipped");
            assertTrue(logged.stream().anyMatch(line ->
                            line.contains("was built synchronous but posted off the main thread")),
                    "the skip is logged, never silent: " + logged);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  fixtures
    // -----------------------------------------------------------------------------------------------------------------

    /** Platform-visible, and with no getHandlerList of its own - the shape an event declared in common has. */
    static class SampleEvent extends ECEvent {
    }

    /** Another one, to read the shared HandlerList through. */
    static class SiblingEvent extends ECEvent {
    }

    /** An event that is asynchronous by nature and says so, the way a producer off the main thread does. */
    static class AlwaysAsyncEvent extends ECEvent {
        AlwaysAsyncEvent() {
            super(true);
        }
    }

}
