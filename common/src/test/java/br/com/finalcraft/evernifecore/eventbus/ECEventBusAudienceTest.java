package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native-audience seam: who gets the mirror, in which order, and how a bus that mirrors is kept
 * from letting a native consumer hurt the producer.
 */
class ECEventBusAudienceTest {

    private static Level previousLogLevel;

    @BeforeAll
    static void muteTheBusLogger() {
        Logger logger = Logger.getLogger("ECEventBus");
        previousLogLevel = logger.getLevel();
        //the isolation test breaks an audience on purpose: the SEVERE it logs would read as a real
        //failure in the build output
        logger.setLevel(Level.OFF);
    }

    @AfterAll
    static void unmuteTheBusLogger() {
        Logger.getLogger("ECEventBus").setLevel(previousLogLevel);
    }

    @Test
    void aScopedBusNeverCallsItsAudience() {
        ECEventBus bus = ECEventBus.create();
        RecordingAudience audience = new RecordingAudience("recording");
        bus.addNativeAudience(audience);
        List<String> local = new ArrayList<>();
        bus.subscribe(SampleEvent.class, event -> local.add("local"));

        bus.post(new SampleEvent());

        assertEquals(Collections.singletonList("local"), local, "the local phase still runs");
        assertTrue(audience.dispatched.isEmpty(), "a bus from create() must never mirror");
        assertEquals(0, audience.gateChecks, "a scoped bus must not even ask the audience");
    }

    @Test
    void postLocalNeverCallsTheAudience() {
        ECEventBus bus = mirroringBus();
        RecordingAudience audience = new RecordingAudience("recording");
        bus.addNativeAudience(audience);
        List<String> local = new ArrayList<>();
        bus.subscribe(SampleEvent.class, event -> local.add("local"));

        bus.postLocal(new SampleEvent());

        assertEquals(Collections.singletonList("local"), local);
        assertTrue(audience.dispatched.isEmpty(), "postLocal() is the escape from the mirror");
        assertEquals(0, audience.gateChecks);
    }

    @Test
    void theLocalPhaseRunsBeforeTheAudience() {
        ECEventBus bus = mirroringBus();
        List<String> order = new ArrayList<>();
        bus.addNativeAudience(new RecordingAudience("recording") {
            @Override
            public void dispatch(IECEvent event) {
                order.add("audience");
            }
        });
        bus.subscribe(SampleEvent.class, ECEventPriority.LAST, event -> order.add("local-last"));

        bus.post(new SampleEvent());

        assertEquals(Arrays.asList("local-last", "audience"), order);
    }

    @Test
    void theSameInstanceIsMirrored() {
        ECEventBus bus = mirroringBus();
        RecordingAudience audience = new RecordingAudience("recording");
        bus.addNativeAudience(audience);

        SampleEvent event = new SampleEvent();
        bus.post(event);

        assertEquals(1, audience.dispatched.size());
        assertSame(event, audience.dispatched.get(0));
    }

    @Test
    void anAudienceWithoutListenersIsNotDispatchedTo() {
        ECEventBus bus = mirroringBus();
        RecordingAudience audience = new RecordingAudience("recording");
        audience.hasListeners = false;
        bus.addNativeAudience(audience);

        bus.post(new SampleEvent());

        assertEquals(1, audience.gateChecks, "the gate is asked once per post");
        assertTrue(audience.dispatched.isEmpty(), "a closed gate must skip dispatch() entirely");
    }

    @Test
    void anAudienceThatThrowsDoesNotReachTheProducer() {
        ECEventBus bus = mirroringBus();
        bus.addNativeAudience(new RecordingAudience("broken") {
            @Override
            public void dispatch(IECEvent event) {
                throw new IllegalStateException("this audience is broken on purpose");
            }
        });
        RecordingAudience after = new RecordingAudience("after");
        bus.addNativeAudience(after);

        SampleEvent event = new SampleEvent();
        assertSame(event, bus.post(event), "post() returns normally even when an audience blows up");
        assertEquals(1, after.dispatched.size(), "the audiences behind the broken one still run");
    }

    @Test
    void audiencesAreCalledInRegistrationOrder() {
        ECEventBus bus = mirroringBus();
        List<String> order = new ArrayList<>();
        bus.addNativeAudience(new OrderRecordingAudience("bukkit", order));
        bus.addNativeAudience(new OrderRecordingAudience("hytale", order));
        //a third audience, registered by a project that is not this one - it hears after the two
        //that were already there
        bus.addNativeAudience(new OrderRecordingAudience("forge", order));

        bus.post(new SampleEvent());

        assertEquals(Arrays.asList("bukkit", "hytale", "forge"), order);
    }

    @Test
    void readdingAnAudienceReplacesTheOneAnsweringToTheSameName() {
        ECEventBus bus = mirroringBus();
        RecordingAudience first = new RecordingAudience("bukkit");
        RecordingAudience replacement = new RecordingAudience("bukkit");
        bus.addNativeAudience(first);
        bus.addNativeAudience(replacement);

        bus.post(new SampleEvent());

        assertEquals(1, bus.getNativeAudiences().size(), "re-registering must not add a second copy");
        assertTrue(first.dispatched.isEmpty(), "the replaced audience is gone");
        assertEquals(1, replacement.dispatched.size());
    }

    @Test
    void removingAnAudienceStopsTheMirroring() {
        ECEventBus bus = mirroringBus();
        RecordingAudience audience = new RecordingAudience("bukkit");
        bus.addNativeAudience(audience);

        bus.removeNativeAudience("bukkit");
        bus.post(new SampleEvent());

        assertTrue(bus.getNativeAudiences().isEmpty());
        assertTrue(audience.dispatched.isEmpty());
    }

    // ------------------------------------------------------------------
    //  fixtures
    // ------------------------------------------------------------------

    /** A bus that mirrors without being the global one, so a test never mutates process-wide state. */
    private static ECEventBus mirroringBus() {
        return new ECEventBus(true);
    }

    static class SampleEvent implements IECEvent {
    }

    static class RecordingAudience implements ECNativeAudience {
        final List<IECEvent> dispatched = new ArrayList<>();
        final String name;
        boolean hasListeners = true;
        int gateChecks = 0;

        RecordingAudience(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean hasListeners(IECEvent event) {
            gateChecks++;
            return hasListeners;
        }

        @Override
        public void dispatch(IECEvent event) {
            dispatched.add(event);
        }
    }

    static final class OrderRecordingAudience extends RecordingAudience {
        private final List<String> order;

        OrderRecordingAudience(String name, List<String> order) {
            super(name);
            this.order = order;
        }

        @Override
        public void dispatch(IECEvent event) {
            super.dispatch(event);
            order.add(name());
        }
    }

}
