package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IECPluginBootstrap;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static Level previousLogLevel;

    @BeforeAll
    static void muteTheBusLogger() {
        Logger logger = Logger.getLogger("ECEventBus");
        previousLogLevel = logger.getLevel();
        //the isolation test breaks a handler on purpose: the SEVERE it logs would read as a real
        //failure in the build output
        logger.setLevel(Level.OFF);
    }

    @AfterAll
    static void unmuteTheBusLogger() {
        Logger.getLogger("ECEventBus").setLevel(previousLogLevel);
    }

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

        bus.subscribe(SampleEvent.class, ECEventPriority.LAST, event -> order.add("last"));
        bus.subscribe(SampleEvent.class, ECEventPriority.FIRST, event -> order.add("first"));
        bus.subscribe(SampleEvent.class, event -> order.add("normal"));
        bus.subscribe(SampleEvent.class, ECEventPriority.EARLY, event -> order.add("early"));

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
        ECEventBus bus = ECEventBus.create();
        List<String> order = new ArrayList<>();

        bus.subscribe(SampleEvent.class, ECEventPriority.FIRST, event -> order.add("before"));
        bus.subscribe(SampleEvent.class, event -> {
            throw new IllegalStateException("this handler is broken on purpose");
        });
        bus.subscribe(SampleEvent.class, ECEventPriority.LAST, event -> order.add("after"));

        bus.post(new SampleEvent());

        assertEquals(Arrays.asList("before", "after"), order);
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
