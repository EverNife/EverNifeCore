package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IECPluginBootstrap;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The local phase of the bus: who is subscribed, in which order they hear, and what happens to the
 * queue when one of them breaks or when the subscriber goes away.
 */
@ECoreTest
class ECEventBusTest {

    @TempDir
    Path tempDir;

    private final List<String> createdPlugins = new ArrayList<>();

    @AfterEach
    void forgetTheFakePlugins() {
        for (String pluginName : createdPlugins) {
            ECPluginManager.removePluginData(pluginName);
        }
        createdPlugins.clear();
    }

    // ------------------------------------------------------------------
    //  Delivery
    // ------------------------------------------------------------------

    @Test
    void deliversTheSameInstanceToTheSubscriber() {
        ECEventBus bus = ECEventBus.create();
        List<IECEvent> received = new ArrayList<>();
        bus.subscribe(SampleEvent.class, received::add);

        SampleEvent event = new SampleEvent();
        SampleEvent returned = bus.post(event);

        assertEquals(1, received.size());
        assertSame(event, received.get(0));
        assertSame(event, returned, "post() must hand the event back to the producer");
    }

    @Test
    void doesNotDeliverAnUnrelatedEventType() {
        ECEventBus bus = ECEventBus.create();
        List<IECEvent> received = new ArrayList<>();
        bus.subscribe(SampleEvent.class, received::add);

        bus.post(new OtherEvent());

        assertTrue(received.isEmpty());
    }

    @Test
    void invokesHandlersInPriorityOrder() {
        ECEventBus bus = ECEventBus.create();
        List<String> order = new ArrayList<>();

        bus.subscribe(SampleEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.LAST), event -> order.add("last"));
        bus.subscribe(SampleEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.FIRST), event -> order.add("first"));
        bus.subscribe(SampleEvent.class, event -> order.add("normal"));
        bus.subscribe(SampleEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.EARLY), event -> order.add("early"));

        bus.post(new SampleEvent());

        assertEquals(Arrays.asList("first", "early", "normal", "last"), order);
    }

    @Test
    void handlersOfTheSamePriorityKeepTheOrderTheySubscribedIn() {
        ECEventBus bus = ECEventBus.create();
        List<String> order = new ArrayList<>();

        bus.subscribe(SampleEvent.class, event -> order.add("one"));
        bus.subscribe(SampleEvent.class, event -> order.add("two"));
        bus.subscribe(SampleEvent.class, event -> order.add("three"));

        bus.post(new SampleEvent());

        assertEquals(Arrays.asList("one", "two", "three"), order);
    }

    @Test
    void aSubscriptionOnAMarkerInterfaceHearsEveryEventCarryingIt() {
        ECEventBus bus = ECEventBus.create();
        List<String> received = new ArrayList<>();
        bus.subscribe(Marked.class, event -> received.add(event.getClass().getSimpleName()));

        bus.post(new MarkedEvent());
        bus.post(new AlsoMarkedEvent());
        bus.post(new OtherEvent());

        assertEquals(Arrays.asList("MarkedEvent", "AlsoMarkedEvent"), received);
    }

    @Test
    void aSubscriptionOnASupertypeHearsTheSubtype() {
        ECEventBus bus = ECEventBus.create();
        List<String> received = new ArrayList<>();
        bus.subscribe(SampleEvent.class, event -> received.add(event.getClass().getSimpleName()));

        bus.post(new SubSampleEvent());

        assertEquals(Collections.singletonList("SubSampleEvent"), received);
    }

    @Test
    void aHandlerThatThrowsDoesNotStopTheOnesAfterIt() {
        List<Throwable> failures = new ArrayList<>();
        ECEventBus bus = ECEventBus.create((subscription, event, failure) -> failures.add(failure));
        List<String> order = new ArrayList<>();

        bus.subscribe(SampleEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.FIRST), event -> order.add("before"));
        bus.subscribe(SampleEvent.class, event -> {
            throw new IllegalStateException("this handler is broken on purpose");
        });
        bus.subscribe(SampleEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.LAST), event -> order.add("after"));

        bus.post(new SampleEvent());

        assertEquals(Arrays.asList("before", "after"), order);
        assertEquals(1, failures.size(), "the failure reached the bus's exception handler");
        assertTrue(failures.get(0) instanceof IllegalStateException);
    }

    @Test
    void aSubscriptionTakenDuringADeliveryOnlyHearsTheNextPost() {
        ECEventBus bus = ECEventBus.create();
        List<String> seen = new ArrayList<>();
        AtomicBoolean alreadySubscribed = new AtomicBoolean(false);

        bus.subscribe(SampleEvent.class, event -> {
            seen.add("first");
            if (alreadySubscribed.compareAndSet(false, true)) {
                bus.subscribe(SampleEvent.class, late -> seen.add("subscribed-mid-delivery"));
            }
            //a reentrant post of another type, from inside a delivery
            bus.post(new OtherEvent());
        });
        bus.subscribe(SampleEvent.class, event -> seen.add("second"));
        bus.subscribe(OtherEvent.class, event -> seen.add("other"));

        bus.post(new SampleEvent());
        assertEquals(Arrays.asList("first", "other", "second"), seen,
                "the plan of the event being delivered is a snapshot - the newcomer is not in it");

        seen.clear();
        bus.post(new SampleEvent());
        assertEquals(Arrays.asList("first", "other", "second", "subscribed-mid-delivery"), seen);
    }

    // ------------------------------------------------------------------
    //  Unsubscribing
    // ------------------------------------------------------------------

    @Test
    void unsubscribingThroughTheHandleStopsTheDelivery() {
        ECEventBus bus = ECEventBus.create();
        List<String> received = new ArrayList<>();
        ECEventSubscription<SampleEvent> subscription = bus.subscribe(SampleEvent.class, event -> received.add("hit"));

        assertTrue(subscription.isActive());
        assertSame(SampleEvent.class, subscription.getEventType());

        subscription.unsubscribe();
        subscription.unsubscribe(); //idempotent

        assertFalse(subscription.isActive());
        bus.post(new SampleEvent());
        assertTrue(received.isEmpty());
    }

    @Test
    void registerScansTheAnnotatedMethodsAndUnregisterDropsThem() {
        ECEventBus bus = ECEventBus.create();
        AnnotatedListener listener = new AnnotatedListener();

        bus.register(listener);
        bus.post(new SampleEvent());
        assertEquals(Arrays.asList("early", "late"), listener.order);

        bus.unregister(listener);
        listener.order.clear();
        bus.post(new SampleEvent());
        assertTrue(listener.order.isEmpty());
    }

    @Test
    void aHandlerThatIgnoresCancelledStepsAsideOnceTheEventIsCancelled() {
        ECEventBus bus = ECEventBus.create();
        CancellationListener listener = new CancellationListener();
        bus.register(listener);

        bus.post(new CancellableEvent());

        assertEquals(Arrays.asList("canceller", "hears-anyway"), listener.order);
    }

    // ------------------------------------------------------------------
    //  Asking who listens
    // ------------------------------------------------------------------

    @Test
    void hasListenersIsFalseWhenNobodySubscribed() {
        ECEventBus bus = ECEventBus.create();

        assertFalse(bus.hasListeners(SampleEvent.class));
    }

    @Test
    void hasListenersSeesASubscriberOnTheExactType() {
        ECEventBus bus = ECEventBus.create();
        bus.subscribe(SampleEvent.class, event -> {
        });

        assertTrue(bus.hasListeners(SampleEvent.class));
        assertFalse(bus.hasListeners(OtherEvent.class), "an unrelated type is still nobody's");
    }

    @Test
    void hasListenersSeesASubscriberOnASupertypeOfTheEvent() {
        ECEventBus bus = ECEventBus.create();
        bus.subscribe(SampleEvent.class, event -> {
        });

        assertTrue(bus.hasListeners(SubSampleEvent.class), "a post of the subtype would reach the supertype handler");
    }

    @Test
    void hasListenersSeesASubscriberOnAMarkerInterfaceTheEventCarries() {
        ECEventBus bus = ECEventBus.create();
        bus.subscribe(Marked.class, event -> {
        });

        assertTrue(bus.hasListeners(MarkedEvent.class));
        assertTrue(bus.hasListeners(AlsoMarkedEvent.class));
        assertFalse(bus.hasListeners(OtherEvent.class));
    }

    @Test
    void hasListenersIgnoresASubscriberOnASubtype() {
        ECEventBus bus = ECEventBus.create();
        bus.subscribe(SubSampleEvent.class, event -> {
        });

        assertFalse(bus.hasListeners(SampleEvent.class),
                "a post of the supertype would never reach a handler that only wants the subtype");
        assertTrue(bus.hasListeners(SubSampleEvent.class));
    }

    @Test
    void hasListenersTurnsFalseAgainAfterTheHandleUnsubscribes() {
        ECEventBus bus = ECEventBus.create();
        ECEventSubscription<SampleEvent> subscription = bus.subscribe(SampleEvent.class, event -> {
        });
        assertTrue(bus.hasListeners(SampleEvent.class));

        subscription.unsubscribe();

        assertFalse(bus.hasListeners(SampleEvent.class));
    }

    @Test
    void hasListenersTurnsFalseAgainAfterTheAnnotatedListenerIsUnregistered() {
        ECEventBus bus = ECEventBus.create();
        AnnotatedListener listener = new AnnotatedListener();
        bus.register(listener);
        assertTrue(bus.hasListeners(SampleEvent.class));

        bus.unregister(listener);

        assertFalse(bus.hasListeners(SampleEvent.class));
    }

    @Test
    void postIfListenedNeverBuildsTheEventWhenNobodyListens() {
        ECEventBus bus = ECEventBus.create();
        AtomicInteger builds = new AtomicInteger();

        SampleEvent posted = bus.postIfListened(SampleEvent.class, () -> {
            builds.incrementAndGet();
            return new SampleEvent();
        });

        assertNull(posted, "nobody listened, so there is no event to hand back");
        assertEquals(0, builds.get(), "the supplier is the cost postIfListened exists to avoid");
    }

    @Test
    void postIfListenedBuildsTheEventOnceAndHandsBackWhatTheSubscriberReceived() {
        ECEventBus bus = ECEventBus.create();
        List<IECEvent> received = new ArrayList<>();
        bus.subscribe(SampleEvent.class, received::add);
        AtomicInteger builds = new AtomicInteger();

        SampleEvent posted = bus.postIfListened(SampleEvent.class, () -> {
            builds.incrementAndGet();
            return new SampleEvent();
        });

        assertEquals(1, builds.get());
        assertEquals(1, received.size());
        assertSame(posted, received.get(0), "the subscriber must have seen the very instance handed back");
    }

    // ------------------------------------------------------------------
    //  Plugin-owned subscriptions
    // ------------------------------------------------------------------

    @Test
    void unsubscribeAllDropsOnlyTheOwningPluginsSubscriptions() {
        ECPluginData owner = pluginData("EventBusOwner");
        ECPluginData bystander = pluginData("EventBusBystander");

        ECEventBus bus = ECEventBus.create();
        List<String> received = new ArrayList<>();
        ECEventSubscription<SampleEvent> ownerSubscription =
                bus.subscribe(owner, SampleEvent.class, event -> received.add("owner"));
        ECEventSubscription<SampleEvent> bystanderSubscription =
                bus.subscribe(bystander, SampleEvent.class, event -> received.add("bystander"));

        bus.unsubscribeAll(owner);

        assertFalse(ownerSubscription.isActive());
        assertTrue(bystanderSubscription.isActive());

        bus.post(new SampleEvent());
        assertEquals(Collections.singletonList("bystander"), received);
    }

    @Test
    void hasListenersTurnsFalseAgainAfterThePluginIsDrained() {
        ECPluginData owner = pluginData("EventBusGateOwner");

        ECEventBus bus = ECEventBus.create();
        bus.subscribe(owner, SampleEvent.class, event -> {
        });
        assertTrue(bus.hasListeners(SampleEvent.class));

        bus.unsubscribeAll(owner);

        assertFalse(bus.hasListeners(SampleEvent.class));
    }

    @Test
    void aPluginShutdownDrainsItsSubscriptionsFromTheGlobalBus() {
        ECPluginData data = pluginData("EventBusShutdown");
        List<String> received = new ArrayList<>();
        ECEventSubscription<SampleEvent> subscription =
                ECEventBus.global().subscribe(data, SampleEvent.class, event -> received.add("hit"));

        new ShutdownOnlyBootstrap(data).runECPluginShutdown();

        assertFalse(subscription.isActive(), "the pre-shutdown must drain the plugin's subscriptions");
        ECEventBus.global().post(new SampleEvent());
        assertTrue(received.isEmpty());
    }

    // ------------------------------------------------------------------
    //  fixtures
    // ------------------------------------------------------------------

    static class SampleEvent implements IECEvent {
    }

    static class SubSampleEvent extends SampleEvent {
    }

    static class OtherEvent implements IECEvent {
    }

    interface Marked extends IECEvent {
    }

    static class MarkedEvent implements Marked {
    }

    static class AlsoMarkedEvent implements Marked {
    }

    static class CancellableEvent implements IECEvent, ECCancellable {
        private boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    static class CancellationListener {
        final List<String> order = new ArrayList<>();

        @ECEventHandler(priority = ECEventPriority.FIRST)
        public void canceller(CancellableEvent event) {
            order.add("canceller");
            event.setCancelled(true);
        }

        @ECEventHandler(ignoreCancelled = true)
        public void skipsCancelled(CancellableEvent event) {
            order.add("skips-cancelled");
        }

        @ECEventHandler(priority = ECEventPriority.LAST)
        public void hearsAnyway(CancellableEvent event) {
            order.add("hears-anyway");
        }
    }

    static class AnnotatedListener {
        final List<String> order = new ArrayList<>();

        @ECEventHandler(priority = ECEventPriority.LAST)
        public void late(SampleEvent event) {
            order.add("late");
        }

        @ECEventHandler(priority = ECEventPriority.FIRST)
        public void early(SampleEvent event) {
            order.add("early");
        }

        @ECEventHandler
        public void notAnECEvent(String notAnEvent) {
            order.add("should never run");
        }
    }

    /** A bootstrap that exists only to run the default pre-shutdown cleanup. */
    static final class ShutdownOnlyBootstrap implements IECPluginBootstrap {
        private final ECPluginData data;

        ShutdownOnlyBootstrap(ECPluginData data) {
            this.data = data;
        }

        @Override
        public ECPluginData getPluginData() {
            return data;
        }

        @Override
        public void onECPluginEnable() {
        }

        @Override
        public void onECPluginShutdown() {
        }

        @Override
        public void onECPluginReload() {
        }
    }

    private ECPluginData pluginData(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        createdPlugins.add(pluginName);
        ECPluginData data = ECPluginManager.getOrCreateECorePluginData(new Object());
        //The default onECPluginShutdownPre() touches FinalCMDManager, whose static block logs through
        //EverNifeCore.getEcPluginData() - it must be non-null the FIRST time any test in the JVM
        //references that class, or its <clinit> fails permanently for the run.
        EverNifeCore.instance.onLoaderInstantiate(data);
        return data;
    }

}
