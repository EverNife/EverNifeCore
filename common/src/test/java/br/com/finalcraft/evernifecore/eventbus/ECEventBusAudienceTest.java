package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.testing.RecordingAudience;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native-audience seam: who gets the mirror, in which order, and how a bus that mirrors is kept
 * from letting a native consumer hurt the producer.
 */
class ECEventBusAudienceTest {

    @Test
    void aScopedBusNeverCallsItsAudience() {
        ECEventBus bus = ECEventBus.create();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);
        List<String> local = new ArrayList<>();
        bus.subscribe(SampleEvent.class, event -> local.add("local"));

        bus.post(new SampleEvent());

        assertEquals(Collections.singletonList("local"), local, "the local phase still runs");
        assertTrue(audience.getDispatched().isEmpty(), "a bus from create() must never mirror");
        assertEquals(0, audience.getGateChecks(), "a scoped bus must not even ask the audience");
    }

    @Test
    void postLocalNeverCallsTheAudience() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);
        List<String> local = new ArrayList<>();
        bus.subscribe(SampleEvent.class, event -> local.add("local"));

        bus.postLocal(new SampleEvent());

        assertEquals(Collections.singletonList("local"), local);
        assertTrue(audience.getDispatched().isEmpty(), "postLocal() is the escape from the mirror");
        assertEquals(0, audience.getGateChecks());
    }

    @Test
    void anEventThatIsNotAnECEventNeverReachesAnAudience() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);
        List<String> local = new ArrayList<>();
        bus.subscribe(LocalOnlyEvent.class, event -> local.add("local"));

        bus.post(new LocalOnlyEvent());

        assertEquals(Collections.singletonList("local"), local, "the local phase still runs");
        assertEquals(0, audience.getGateChecks(), "the hierarchy decides: no ECEvent, no mirror at all");
        assertTrue(audience.getDispatched().isEmpty());
    }

    @Test
    void theLocalPhaseRunsBeforeTheAudience() {
        ECEventBus bus = EventBuses.mirroring();
        List<String> order = new ArrayList<>();
        bus.addNativeAudience(new RecordingAudience() {
            @Override
            public void dispatch(IECEvent event) {
                order.add("audience");
            }
        });
        bus.subscribe(SampleEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.LAST), event -> order.add("local-last"));

        bus.post(new SampleEvent());

        assertEquals(Arrays.asList("local-last", "audience"), order);
    }

    @Test
    void theSameInstanceIsMirrored() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);

        SampleEvent event = new SampleEvent();
        bus.post(event);

        assertEquals(1, audience.getDispatched().size());
        assertSame(event, audience.getDispatched().get(0));
    }

    @Test
    void anAudienceWithoutListenersIsNotDispatchedTo() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        audience.setHasListeners(false);
        bus.addNativeAudience(audience);

        bus.post(new SampleEvent());

        assertEquals(1, audience.getGateChecks(), "the gate is asked once per post");
        assertTrue(audience.getDispatched().isEmpty(), "a closed gate must skip dispatch() entirely");
    }

    @Test
    void anAudienceThatThrowsDoesNotReachTheProducer() {
        ECEventBus bus = EventBuses.mirroring();
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
        assertEquals(1, after.getDispatched().size(), "the audiences behind the broken one still run");
    }

    @Test
    void audiencesAreCalledInRegistrationOrder() {
        ECEventBus bus = EventBuses.mirroring();
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
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience first = new RecordingAudience("bukkit");
        RecordingAudience replacement = new RecordingAudience("bukkit");
        bus.addNativeAudience(first);
        bus.addNativeAudience(replacement);

        bus.post(new SampleEvent());

        assertEquals(1, bus.getNativeAudiences().size(), "re-registering must not add a second copy");
        assertTrue(first.getDispatched().isEmpty(), "the replaced audience is gone");
        assertEquals(1, replacement.getDispatched().size());
    }

    @Test
    void removingAnAudienceStopsTheMirroring() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience("bukkit");
        bus.addNativeAudience(audience);

        bus.removeNativeAudience("bukkit");
        bus.post(new SampleEvent());

        assertTrue(bus.getNativeAudiences().isEmpty());
        assertTrue(audience.getDispatched().isEmpty());
    }

    // ------------------------------------------------------------------
    //  Asking the audience who listens
    // ------------------------------------------------------------------

    @Test
    void hasListenersSeesAnAudienceThatSaysItHasListeners() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);

        assertTrue(bus.hasListeners(SampleEvent.class), "a native listener counts even with no local subscriber");
    }

    @Test
    void hasListenersIsFalseWhenTheAudienceGateIsClosed() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        audience.setHasListeners(false);
        bus.addNativeAudience(audience);

        assertFalse(bus.hasListeners(SampleEvent.class));
    }

    @Test
    void hasListenersNeverAsksAnAudienceAboutAnEventNoAudienceCanSee() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);
        audience.reset();

        assertFalse(bus.hasListeners(LocalOnlyEvent.class));
        assertEquals(0, audience.getGateChecks(), "the hierarchy decides before the gate does");
    }

    @Test
    void aScopedBusNeverAsksItsAudienceWhetherAnyoneListens() {
        ECEventBus bus = ECEventBus.create();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);
        audience.reset();

        assertFalse(bus.hasListeners(SampleEvent.class), "a bus from create() only ever sees its own subscribers");
        assertEquals(0, audience.getGateChecks());
    }

    @Test
    void postIfListenedBuildsAndMirrorsWhenOnlyTheAudienceListens() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);
        AtomicInteger builds = new AtomicInteger();

        SampleEvent posted = bus.postIfListened(SampleEvent.class, () -> {
            builds.incrementAndGet();
            return new SampleEvent();
        });

        assertNotNull(posted);
        assertEquals(1, builds.get());
        assertEquals(1, audience.getDispatched().size());
        assertSame(posted, audience.getDispatched().get(0));
    }

    @Test
    void postIfListenedNeverBuildsWhenTheOnlyAudienceGateIsClosed() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        audience.setHasListeners(false);
        bus.addNativeAudience(audience);
        AtomicInteger builds = new AtomicInteger();

        SampleEvent posted = bus.postIfListened(SampleEvent.class, () -> {
            builds.incrementAndGet();
            return new SampleEvent();
        });

        assertNull(posted);
        assertEquals(0, builds.get());
        assertTrue(audience.getDispatched().isEmpty());
    }

    // ------------------------------------------------------------------
    //  fixtures
    // ------------------------------------------------------------------

    /** Platform-visible: extending ECEvent is what makes an event reach an audience at all. */
    static class SampleEvent extends ECEvent implements IECEvent {
    }

    /** The local/hot event: it carries the marker and nothing else, so no audience can see it. */
    static class LocalOnlyEvent implements IECEvent {
    }

    /** A recorder that also writes its name into a list shared with the other audiences. */
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
