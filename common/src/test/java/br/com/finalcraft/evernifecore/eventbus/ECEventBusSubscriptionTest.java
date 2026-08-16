package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A subscription as an object: what {@link ECSubscribeOptions} decide, what the {@link ECEventSubscription}
 * view answers, and what {@link ECEventBus#getSubscriptions} and {@link ECEventBus#unsubscribeIf} do with it.
 */
@ECoreTest
class ECEventBusSubscriptionTest {

    @TempDir
    Path tempDir;

    private final List<String> createdPlugins = new ArrayList<>();

    @AfterEach
    void forgetTheFakePlugins() {
        for (String pluginName : createdPlugins) {
            Plugins.forget(pluginName);
        }
        createdPlugins.clear();
    }

    // ------------------------------------------------------------------
    //  ECSubscribeOptions, the value object
    // ------------------------------------------------------------------

    @Test
    void theDefaultsAreOneSharedInstanceAndAWitherThatChangesNothingHandsItBack() {
        assertSame(ECSubscribeOptions.defaults(), ECSubscribeOptions.defaults());
        assertSame(ECSubscribeOptions.defaults(), ECSubscribeOptions.defaults().withExact(false));
        assertSame(ECSubscribeOptions.defaults(), ECSubscribeOptions.defaults().withPriority(ECEventPriority.NORMAL));
        assertNotSame(ECSubscribeOptions.defaults(), ECSubscribeOptions.defaults().withExact(true));
        assertSame(ECSubscribeOptions.defaults(), ECSubscribeOptions.defaults().withExact(true).withExact(false),
                "back to the defaults means back to the shared instance");

        ECSubscribeOptions late = ECSubscribeOptions.defaults().withPriority(ECEventPriority.LATE);
        assertSame(late, late.withPriority(ECEventPriority.LATE), "off the defaults too: a wither that changes nothing allocates nothing");
        assertSame(late, late.withIgnoreCancelled(false));
        assertSame(late, late.withPlugin(null));
    }

    @Test
    void optionsAreEqualByValueWithThePluginComparedByName() {
        ECPluginData plugin = pluginData("OptionsOwner");

        ECSubscribeOptions one = ECSubscribeOptions.ownedBy(plugin).withPriority(ECEventPriority.LATE).withIgnoreCancelled(true);
        ECSubscribeOptions two = ECSubscribeOptions.defaults().withIgnoreCancelled(true).withPriority(ECEventPriority.LATE).withPlugin(plugin);

        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
        assertNotEquals(one, two.withPriority(ECEventPriority.LAST));
        assertNotEquals(one, two.withPlugin(null));
        assertEquals(ECEventPriority.LATE.getValue(), one.getPriority());
        assertTrue(one.isIgnoringCancelled());
        assertFalse(one.isExact());
        assertSame(plugin, one.getPlugin());
    }

    @Test
    void optionsDescribeThePriorityStepThePluginAndOnlyTheFlagsThatAreSet() {
        ECPluginData plugin = pluginData("OptionsDescribed");

        assertEquals("NORMAL", ECSubscribeOptions.defaults().toString());
        assertEquals("LATE, plugin=OptionsDescribed, exact, ignoreCancelled",
                ECSubscribeOptions.ownedBy(plugin).withPriority(ECEventPriority.LATE).withExact(true).withIgnoreCancelled(true).toString());
        assertEquals("-30000", ECSubscribeOptions.defaults().withPriority((short) -30000).toString(),
                "a value between the steps is printed raw");
    }

    // ------------------------------------------------------------------
    //  What the options decide on the bus
    // ------------------------------------------------------------------

    @Test
    void aFunctionalSubscriptionThatIgnoresCancelledStepsAsideOnceTheEventIsCancelled() {
        ECEventBus bus = ECEventBus.create();
        List<String> order = new ArrayList<>();

        bus.subscribe(CancellableEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.FIRST), event -> {
            order.add("canceller");
            event.setCancelled(true);
        });
        bus.subscribe(CancellableEvent.class, ECSubscribeOptions.defaults().withIgnoreCancelled(true), event -> order.add("skips-cancelled"));
        bus.subscribe(CancellableEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.LAST), event -> order.add("hears-anyway"));

        bus.post(new CancellableEvent());

        assertEquals(Arrays.asList("canceller", "hears-anyway"), order);
    }

    @Test
    void anExactSubscriptionHearsTheClassItNamedAndNoSubtypeOfIt() {
        ECEventBus bus = ECEventBus.create();
        List<String> received = new ArrayList<>();
        bus.subscribe(SampleEvent.class, ECSubscribeOptions.defaults().withExact(true), event -> received.add(event.getClass().getSimpleName()));

        assertTrue(bus.hasListeners(SampleEvent.class));
        assertFalse(bus.hasListeners(SubSampleEvent.class), "an exact subscription on the base does not count for the subtype");

        bus.post(new SubSampleEvent());
        bus.post(new SampleEvent());

        assertEquals(Collections.singletonList("SampleEvent"), received);
    }

    @Test
    void anAnnotatedExactHandlerHearsTheClassItNamedAndNoSubtypeOfIt() {
        ECEventBus bus = ECEventBus.create();
        ExactListener listener = new ExactListener();
        bus.register(listener);

        assertFalse(bus.hasListeners(SubSampleEvent.class));

        bus.post(new SubSampleEvent());
        bus.post(new SampleEvent());

        assertEquals(Collections.singletonList("SampleEvent"), listener.received);
    }

    // ------------------------------------------------------------------
    //  register hands back views
    // ------------------------------------------------------------------

    @Test
    void registerHandsBackOneOwnedViewPerAnnotatedMethodAndTheDrainTakesThemAll() {
        ECPluginData plugin = pluginData("RegisterOwner");
        ECEventBus bus = ECEventBus.create();
        AnnotatedListener listener = new AnnotatedListener();

        List<ECEventSubscription<?>> views = bus.register(plugin, listener);

        assertEquals(2, views.size(), "one per method that names an IECEvent; the String one is not the bus's");
        for (ECEventSubscription<?> view : views) {
            assertSame(listener, view.getSubscriber());
            assertSame(plugin, view.getOptions().getPlugin());
            assertTrue(view.isActive());
        }
        ECEventSubscription<?> late = views.get(0).getOptions().getPriority() > 0 ? views.get(0) : views.get(1);
        ECEventSubscription<?> early = late == views.get(0) ? views.get(1) : views.get(0);
        assertEquals(ECEventPriority.LAST.getValue(), late.getOptions().getPriority());
        assertTrue(late.getOptions().isIgnoringCancelled(), "read off the annotation");
        assertEquals(ECEventPriority.FIRST.getValue(), early.getOptions().getPriority());
        assertFalse(early.getOptions().isIgnoringCancelled());
        assertEquals(SampleEvent.class, late.getEventType());

        bus.unsubscribeAll(plugin);

        assertFalse(late.isActive());
        assertFalse(early.isActive());
        bus.post(new SampleEvent());
        assertTrue(listener.order.isEmpty());
    }

    @Test
    void registerWithoutAPluginHandsBackUnownedViewsAndUnregisterDropsThem() {
        ECEventBus bus = ECEventBus.create();
        AnnotatedListener listener = new AnnotatedListener();

        List<ECEventSubscription<?>> views = bus.register(listener);

        assertEquals(2, views.size());
        assertNull(views.get(0).getOptions().getPlugin());
        assertNull(views.get(1).getOptions().getPlugin());

        bus.unregister(listener);

        assertFalse(views.get(0).isActive());
        assertFalse(views.get(1).isActive());
        assertTrue(bus.getSubscriptions().isEmpty());
    }

    // ------------------------------------------------------------------
    //  getSubscriptions and unsubscribeIf
    // ------------------------------------------------------------------

    @Test
    void getSubscriptionsListsEveryoneInSubscriptionOrderAndPerTypeInDeliveryOrderWithoutSubtypeSubscribers() {
        ECEventBus bus = ECEventBus.create();
        ECEventSubscription<SampleEvent> lateOnBase = bus.subscribe(SampleEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.LATE), event -> {
        });
        ECEventSubscription<SubSampleEvent> onSub = bus.subscribe(SubSampleEvent.class, event -> {
        });
        ECEventSubscription<SampleEvent> firstOnBase = bus.subscribe(SampleEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.FIRST), event -> {
        });

        assertEquals(Arrays.asList(lateOnBase, onSub, firstOnBase), bus.getSubscriptions(), "everyone, as they subscribed");
        assertEquals(Arrays.asList(firstOnBase, lateOnBase), bus.getSubscriptions(SampleEvent.class),
                "delivery order, and the subtype subscriber is not in a post of the base");
        assertEquals(Arrays.asList(firstOnBase, onSub, lateOnBase), bus.getSubscriptions(SubSampleEvent.class));
    }

    @Test
    void unsubscribeIfRemovesOnlyTheMatchesHandsThemBackAndClosesTheWatchTheyHeldOpen() {
        ECEventBus bus = ECEventBus.create();
        List<String> transitions = new ArrayList<>();
        bus.watchListeners(() -> transitions.add("first"), () -> transitions.add("gone"), SampleEvent.class);

        ECEventSubscription<SampleEvent> late = bus.subscribe(SampleEvent.class, ECSubscribeOptions.defaults().withPriority(ECEventPriority.LATE), event -> {
        });
        ECEventSubscription<OtherEvent> other = bus.subscribe(OtherEvent.class, event -> {
        });
        ECEventSubscription<SampleEvent> normal = bus.subscribe(SampleEvent.class, event -> {
        });
        assertEquals(Collections.singletonList("first"), transitions);

        List<ECEventSubscription<?>> removed = bus.unsubscribeIf(subscription -> subscription.getEventType() == SampleEvent.class);

        assertEquals(Arrays.asList(late, normal), removed, "exactly the matches, in subscription order");
        assertFalse(late.isActive());
        assertFalse(normal.isActive());
        assertTrue(other.isActive());
        assertEquals(Collections.singletonList(other), bus.getSubscriptions());
        assertEquals(Arrays.asList("first", "gone"), transitions, "the watch heard its last listener leave");
        assertTrue(bus.unsubscribeIf(subscription -> false).isEmpty());
    }

    // ------------------------------------------------------------------
    //  the view describes itself
    // ------------------------------------------------------------------

    @Test
    void theViewDescribesItselfInOneLine() {
        ECPluginData plugin = pluginData("Shop");
        ECEventBus bus = ECEventBus.create();

        ECEventSubscription<?> annotated = bus.register(plugin, new ShopListener()).get(0);
        ECEventSubscription<SampleEvent> functional = bus.subscribe(SampleEvent.class, event -> {
        });

        assertEquals("ShopListener#onLogin on SampleEvent [LATE, plugin=Shop]", annotated.toString());
        String line = functional.toString();
        assertTrue(line.contains(ECEventBusSubscriptionTest.class.getName()), "the lambda's class names the class it was written in: " + line);
        assertTrue(line.endsWith(" on SampleEvent [NORMAL]"), line);
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

    static class AnnotatedListener {
        final List<String> order = new ArrayList<>();

        @ECEventHandler(priority = ECEventPriority.LAST, ignoreCancelled = true)
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

    static class ExactListener {
        final List<String> received = new ArrayList<>();

        @ECEventHandler(exact = true)
        public void onSample(SampleEvent event) {
            received.add(event.getClass().getSimpleName());
        }
    }

    static class ShopListener {
        @ECEventHandler(priority = ECEventPriority.LATE)
        public void onLogin(SampleEvent event) {
        }
    }

    private ECPluginData pluginData(String pluginName) {
        createdPlugins.add(pluginName);
        return Plugins.fakePluginData(pluginName, tempDir.resolve(pluginName).toFile());
    }

}
