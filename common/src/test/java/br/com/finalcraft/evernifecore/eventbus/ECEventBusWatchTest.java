package br.com.finalcraft.evernifecore.eventbus;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RecordingAudience;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The listener watches: when a producer is told the first listener arrived, when it is told the last
 * one left, and what keeps that pair honest across every way a listener can appear or vanish - a
 * subscription, an annotated listener, a plugin shutdown or a native audience changing its mind.
 */
@ECoreTest
class ECEventBusWatchTest {

    @TempDir
    Path tempDir;

    private final List<String> createdPlugins = new ArrayList<>();

    private static Level previousLogLevel;

    @BeforeAll
    static void muteTheBusLogger() {
        Logger logger = Logger.getLogger("ECEventBus");
        previousLogLevel = logger.getLevel();
        //the isolation test breaks a watch callback on purpose: the SEVERE it logs would read as a
        //real failure in the build output
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
    //  The two transitions
    // ------------------------------------------------------------------

    @Test
    void theFirstListenerOpensTheWatchAndTheSecondDoesNotFireItAgain() {
        ECEventBus bus = ECEventBus.create();
        List<String> transitions = new ArrayList<>();
        bus.watchListeners(SampleEvent.class, () -> transitions.add("first"), () -> transitions.add("gone"));

        assertTrue(transitions.isEmpty(), "a watch over nobody is born silent");

        bus.subscribe(SampleEvent.class, event -> {
        });
        assertEquals(Collections.singletonList("first"), transitions);

        bus.subscribe(SampleEvent.class, event -> {
        });
        assertEquals(Collections.singletonList("first"), transitions, "presence did not change, so nothing fires");
    }

    @Test
    void aWatchTakenWhenSomebodyAlreadyListensOpensImmediately() {
        ECEventBus bus = ECEventBus.create();
        bus.subscribe(SampleEvent.class, event -> {
        });

        List<String> transitions = new ArrayList<>();
        bus.watchListeners(SampleEvent.class, () -> transitions.add("first"), () -> transitions.add("gone"));

        assertEquals(Collections.singletonList("first"), transitions);
    }

    @Test
    void unsubscribingTheLastListenerClosesTheWatch() {
        ECEventBus bus = ECEventBus.create();
        List<String> transitions = new ArrayList<>();
        bus.watchListeners(SampleEvent.class, () -> transitions.add("first"), () -> transitions.add("gone"));

        ECEventSubscription<SampleEvent> one = bus.subscribe(SampleEvent.class, event -> {
        });
        ECEventSubscription<SampleEvent> two = bus.subscribe(SampleEvent.class, event -> {
        });

        one.unsubscribe();
        assertEquals(Collections.singletonList("first"), transitions, "one of two leaving is not the last one leaving");

        two.unsubscribe();
        assertEquals(Arrays.asList("first", "gone"), transitions);
    }

    @Test
    void unregisteringTheLastAnnotatedListenerClosesTheWatch() {
        ECEventBus bus = ECEventBus.create();
        List<String> transitions = new ArrayList<>();
        bus.watchListeners(SampleEvent.class, () -> transitions.add("first"), () -> transitions.add("gone"));

        AnnotatedListener listener = new AnnotatedListener();
        bus.register(listener);
        assertEquals(Collections.singletonList("first"), transitions);

        bus.unregister(listener);
        assertEquals(Arrays.asList("first", "gone"), transitions);
    }

    @Test
    void drainingThePluginThatOwnedTheLastSubscriptionClosesTheWatch() {
        ECPluginData owner = pluginData("EventBusWatchSubscriber");

        ECEventBus bus = ECEventBus.create();
        List<String> transitions = new ArrayList<>();
        bus.watchListeners(SampleEvent.class, () -> transitions.add("first"), () -> transitions.add("gone"));

        bus.subscribe(owner, SampleEvent.class, event -> {
        });
        assertEquals(Collections.singletonList("first"), transitions);

        bus.unsubscribeAll(owner);
        assertEquals(Arrays.asList("first", "gone"), transitions);
    }

    @Test
    void aMultiTypeWatchOpensOnAnyTypeAndClosesOnlyWhenAllAreGone() {
        ECEventBus bus = ECEventBus.create();
        List<String> transitions = new ArrayList<>();
        bus.watchListeners(Arrays.<Class<? extends IECEvent>>asList(SampleEvent.class, OtherEvent.class),
                () -> transitions.add("first"), () -> transitions.add("gone"));

        ECEventSubscription<SampleEvent> onSample = bus.subscribe(SampleEvent.class, event -> {
        });
        assertEquals(Collections.singletonList("first"), transitions);

        ECEventSubscription<OtherEvent> onOther = bus.subscribe(OtherEvent.class, event -> {
        });
        assertEquals(Collections.singletonList("first"), transitions, "the family was already being listened to");

        onSample.unsubscribe();
        assertEquals(Collections.singletonList("first"), transitions, "the other type still has somebody");

        onOther.unsubscribe();
        assertEquals(Arrays.asList("first", "gone"), transitions);
    }

    @Test
    void aCallbackThatThrowsDoesNotStopTheNextTransition() {
        ECEventBus bus = ECEventBus.create();
        List<String> transitions = new ArrayList<>();
        bus.watchListeners(SampleEvent.class, () -> {
            transitions.add("first");
            throw new IllegalStateException("this watch callback is broken on purpose");
        }, () -> transitions.add("gone"));

        ECEventSubscription<SampleEvent> subscription = bus.subscribe(SampleEvent.class, event -> {
        });
        assertEquals(Collections.singletonList("first"), transitions);

        subscription.unsubscribe();
        assertEquals(Arrays.asList("first", "gone"), transitions,
                "the state moved even though the callback blew up, so the closing transition is still due");
    }

    @Test
    void aWatchWithoutAnUndoCallbackStillTracksThePresence() {
        ECEventBus bus = ECEventBus.create();
        List<String> transitions = new ArrayList<>();
        ECListenerWatch watch = bus.watchListeners(SampleEvent.class, () -> transitions.add("first"), null);

        ECEventSubscription<SampleEvent> subscription = bus.subscribe(SampleEvent.class, event -> {
        });
        assertEquals(Collections.singletonList("first"), transitions);
        assertTrue(watch.hasListeners());

        subscription.unsubscribe();

        assertEquals(Collections.singletonList("first"), transitions, "there was nothing to undo");
        assertFalse(watch.hasListeners(), "the presence still moved back to absent");
    }

    @Test
    void subscribingInsideTheFirstListenerCallbackDoesNotFireItTwice() {
        ECEventBus bus = ECEventBus.create();
        List<String> transitions = new ArrayList<>();
        AtomicBoolean subscribedFromInside = new AtomicBoolean(false);

        bus.watchListeners(SampleEvent.class, () -> {
            transitions.add("first");
            if (subscribedFromInside.compareAndSet(false, true)) {
                bus.subscribe(SampleEvent.class, event -> {
                });
            }
        }, () -> transitions.add("gone"));

        bus.subscribe(SampleEvent.class, event -> {
        });

        assertEquals(Collections.singletonList("first"), transitions,
                "the callback's own subscription is folded into the running pass, not a second transition");
    }

    @Test
    void refreshingWhenNothingChangedFiresNothing() {
        ECEventBus bus = ECEventBus.create();
        List<String> transitions = new ArrayList<>();
        bus.watchListeners(SampleEvent.class, () -> transitions.add("first"), () -> transitions.add("gone"));
        bus.subscribe(SampleEvent.class, event -> {
        });

        bus.refreshListenerWatches();
        bus.refreshListenerWatches();

        assertEquals(Collections.singletonList("first"), transitions);
    }

    // ------------------------------------------------------------------
    //  The handle
    // ------------------------------------------------------------------

    @Test
    void theHandleMirrorsTheLastEvaluation() {
        ECEventBus bus = ECEventBus.create();
        ECListenerWatch watch = bus.watchListeners(SampleEvent.class, () -> {
        }, () -> {
        });

        assertFalse(watch.hasListeners());
        assertTrue(watch.isActive());

        ECEventSubscription<SampleEvent> subscription = bus.subscribe(SampleEvent.class, event -> {
        });
        assertTrue(watch.hasListeners());

        subscription.unsubscribe();
        assertFalse(watch.hasListeners());
    }

    @Test
    void theHandleReportsTheEventTypesItWasGiven() {
        ECEventBus bus = ECEventBus.create();
        List<Class<? extends IECEvent>> family = Arrays.<Class<? extends IECEvent>>asList(SampleEvent.class, OtherEvent.class);

        ECListenerWatch single = bus.watchListeners(SampleEvent.class, () -> {
        }, null);
        ECListenerWatch multiple = bus.watchListeners(family, () -> {
        }, null);

        assertEquals(Collections.singletonList(SampleEvent.class), new ArrayList<>(single.getEventTypes()));
        assertEquals(family, new ArrayList<>(multiple.getEventTypes()));
    }

    @Test
    void stoppingTheWatchSilencesItAndMarksItInactive() {
        ECEventBus bus = ECEventBus.create();
        List<String> transitions = new ArrayList<>();
        ECListenerWatch watch = bus.watchListeners(SampleEvent.class, () -> transitions.add("first"), () -> transitions.add("gone"));

        watch.stop();
        watch.stop(); //idempotent

        assertFalse(watch.isActive());

        ECEventSubscription<SampleEvent> subscription = bus.subscribe(SampleEvent.class, event -> {
        });
        subscription.unsubscribe();

        assertTrue(transitions.isEmpty(), "a stopped watch hears nothing, whatever the presence does");
    }

    @Test
    void aWatchNeedsAtLeastOneEventType() {
        ECEventBus bus = ECEventBus.create();

        assertThrows(IllegalArgumentException.class, () -> bus.watchListeners(
                Collections.<Class<? extends IECEvent>>emptyList(), () -> {
                }, null));
    }

    // ------------------------------------------------------------------
    //  Plugin-owned watches
    // ------------------------------------------------------------------

    @Test
    void drainingAPluginStopsItsOwnWatchAndLeavesAnotherPluginsAlone() {
        ECPluginData owner = pluginData("EventBusWatchOwner");
        ECPluginData bystander = pluginData("EventBusWatchBystander");

        ECEventBus bus = ECEventBus.create();
        List<String> ownerTransitions = new ArrayList<>();
        List<String> bystanderTransitions = new ArrayList<>();
        ECListenerWatch ownerWatch = bus.watchListeners(owner, SampleEvent.class,
                () -> ownerTransitions.add("first"), () -> ownerTransitions.add("gone"));
        ECListenerWatch bystanderWatch = bus.watchListeners(bystander, SampleEvent.class,
                () -> bystanderTransitions.add("first"), () -> bystanderTransitions.add("gone"));

        bus.unsubscribeAll(owner);

        assertFalse(ownerWatch.isActive(), "the shutdown drain takes the plugin's watches with its subscriptions");
        assertTrue(bystanderWatch.isActive());

        bus.subscribe(SampleEvent.class, event -> {
        });

        assertTrue(ownerTransitions.isEmpty());
        assertEquals(Collections.singletonList("first"), bystanderTransitions);
    }

    // ------------------------------------------------------------------
    //  Presence coming from a native audience
    // ------------------------------------------------------------------

    @Test
    void anAudienceThatStartsListeningOpensTheWatchOnTheNextRefresh() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        audience.setHasListeners(false);
        bus.addNativeAudience(audience);

        List<String> transitions = new ArrayList<>();
        bus.watchListeners(PlatformEvent.class, () -> transitions.add("first"), () -> transitions.add("gone"));
        assertTrue(transitions.isEmpty());

        audience.setHasListeners(true);
        bus.refreshListenerWatches();

        assertEquals(Collections.singletonList("first"), transitions);
    }

    @Test
    void anAudienceThatStopsListeningClosesTheWatchOnTheNextRefresh() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);

        List<String> transitions = new ArrayList<>();
        bus.watchListeners(PlatformEvent.class, () -> transitions.add("first"), () -> transitions.add("gone"));
        assertEquals(Collections.singletonList("first"), transitions);

        audience.setHasListeners(false);
        bus.refreshListenerWatches();

        assertEquals(Arrays.asList("first", "gone"), transitions);
    }

    @Test
    void removingTheAudienceThatWasTheOnlyPresenceClosesTheWatch() {
        ECEventBus bus = EventBuses.mirroring();
        bus.addNativeAudience(new RecordingAudience("bukkit"));

        List<String> transitions = new ArrayList<>();
        bus.watchListeners(PlatformEvent.class, () -> transitions.add("first"), () -> transitions.add("gone"));
        assertEquals(Collections.singletonList("first"), transitions);

        bus.removeNativeAudience("bukkit");

        assertEquals(Arrays.asList("first", "gone"), transitions);
    }

    @Test
    void aWatchOnAnEventNoAudienceCanSeeIgnoresTheAudienceEntirely() {
        ECEventBus bus = EventBuses.mirroring();
        RecordingAudience audience = new RecordingAudience();
        bus.addNativeAudience(audience);
        audience.reset();

        List<String> transitions = new ArrayList<>();
        bus.watchListeners(SampleEvent.class, () -> transitions.add("first"), () -> transitions.add("gone"));

        assertTrue(transitions.isEmpty(), "nothing that only implements IECEvent can have a native listener");
        assertEquals(0, audience.getGateChecks());
    }

    // ------------------------------------------------------------------
    //  fixtures
    // ------------------------------------------------------------------

    /** Local-only: no audience can ever be the presence behind a watch on it. */
    static class SampleEvent implements IECEvent {
    }

    static class OtherEvent implements IECEvent {
    }

    /** Platform-visible, so an audience is allowed to be the one listening. */
    static class PlatformEvent extends ECEvent implements IECEvent {
    }

    static class AnnotatedListener {
        @ECEventHandler
        public void onSample(SampleEvent event) {
        }
    }

    private ECPluginData pluginData(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        createdPlugins.add(pluginName);
        ECPluginData data = ECPluginManager.getOrCreateECorePluginData(new Object());
        EverNifeCore.instance.onLoaderInstantiate(data);
        return data;
    }

}
