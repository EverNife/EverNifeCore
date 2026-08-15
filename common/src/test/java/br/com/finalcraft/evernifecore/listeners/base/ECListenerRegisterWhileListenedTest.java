package br.com.finalcraft.evernifecore.listeners.base;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A producer that costs nothing while nobody asks: {@link ECListener#registerWhileListened} registers
 * the listener when the first listener of anything it produces appears, unregisters it when the last
 * one leaves, and tries the registration again after one the listener itself refused.
 *
 * <p>The watch is taken on the process-wide bus, so every test here stops what it opened.</p>
 */
@ECoreTest
class ECListenerRegisterWhileListenedTest {

    @TempDirNobodyCleans
    Path tempDir;

    private final List<ECPluginData> createdPlugins = new ArrayList<>();
    private final List<ECListenerWatch> openedWatches = new ArrayList<>();
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();

    @AfterEach
    void leaveTheGlobalBusAsItWasFound() {
        //watches first: a watch still following would answer the unsubscribes below by unregistering
        for (ECListenerWatch watch : openedWatches) {
            watch.stop();
        }
        openedWatches.clear();
        for (ECEventSubscription<?> subscription : subscriptions) {
            subscription.unsubscribe();
        }
        subscriptions.clear();
        for (ECPluginData plugin : createdPlugins) {
            ECListener.unregisterAll(plugin);
            ECPluginManager.removePluginData(plugin.getMetaInfo().getName());
        }
        createdPlugins.clear();
    }

    // ------------------------------------------------------------------
    //  The two transitions
    // ------------------------------------------------------------------

    @Test
    void theListenerStaysUnregisteredUntilSomebodyListensToWhatItProduces() {
        ECPluginData plugin = pluginData("WhileListenedFirst");
        RecordingListener listener = new RecordingListener();

        watch(ECListener.registerWhileListened(plugin, listener, ProducedEvent.class));

        assertTrue(ECListener.getRegistered(plugin).isEmpty(), "nobody asked for the event yet");
        assertEquals(0, listener.registrations.get());

        subscribe(ProducedEvent.class);

        assertTrue(ECListener.getRegistered(plugin).contains(listener),
                "the first listener of the produced event is what puts the producer on the platform");
        assertEquals(1, listener.registrations.get());
    }

    @Test
    void aSecondListenerRegistersNothingAndTheLastOneLeavingUnregistersIt() {
        ECPluginData plugin = pluginData("WhileListenedSecond");
        RecordingListener listener = new RecordingListener();
        watch(ECListener.registerWhileListened(plugin, listener, ProducedEvent.class));

        ECEventSubscription<ProducedEvent> one = subscribe(ProducedEvent.class);
        ECEventSubscription<ProducedEvent> two = subscribe(ProducedEvent.class);

        assertEquals(1, listener.registrations.get(), "presence did not change, so there was nothing to register");

        one.unsubscribe();
        assertTrue(ECListener.getRegistered(plugin).contains(listener),
                "one of two leaving is not the last one leaving");
        assertEquals(0, listener.unregistrations.get());

        two.unsubscribe();
        assertFalse(ECListener.getRegistered(plugin).contains(listener));
        assertEquals(1, listener.unregistrations.get());
    }

    @Test
    void aFamilyKeepsTheListenerWhileAnyOfItsEventsHasSomebody() {
        ECPluginData plugin = pluginData("WhileListenedFamily");
        RecordingListener listener = new RecordingListener();
        watch(ECListener.registerWhileListened(plugin, listener,
                ProducedEvent.class, OtherProducedEvent.class, ThirdProducedEvent.class));

        ECEventSubscription<OtherProducedEvent> onOther = subscribe(OtherProducedEvent.class);
        assertTrue(ECListener.getRegistered(plugin).contains(listener),
                "any one of the produced events is enough to want the producer");

        ECEventSubscription<ThirdProducedEvent> onThird = subscribe(ThirdProducedEvent.class);
        onOther.unsubscribe();
        assertTrue(ECListener.getRegistered(plugin).contains(listener), "the family still has somebody");
        assertEquals(1, listener.registrations.get());

        onThird.unsubscribe();
        assertFalse(ECListener.getRegistered(plugin).contains(listener), "and now nothing it produces is wanted");
        assertEquals(1, listener.unregistrations.get());
    }

    // ------------------------------------------------------------------
    //  Shutdown
    // ------------------------------------------------------------------

    @Test
    void thePluginShutdownDrainStopsTheWatchAndALaterListenerRegistersNothing() {
        ECPluginData plugin = pluginData("WhileListenedDrain");
        RecordingListener listener = new RecordingListener();
        ECListenerWatch watch = watch(ECListener.registerWhileListened(plugin, listener, ProducedEvent.class));

        ECEventBus.global().unsubscribeAll(plugin);

        assertFalse(watch.isActive(), "the shutdown drain takes the plugin's watches with its subscriptions");

        subscribe(ProducedEvent.class);

        assertEquals(0, listener.registrations.get(), "a stopped watch has nobody left to tell");
        assertTrue(ECListener.getRegistered(plugin).isEmpty());
    }

    // ------------------------------------------------------------------
    //  A registration the listener refused
    // ------------------------------------------------------------------

    @Test
    void aRefusedRegistrationIsTriedAgainWhenTheNextListenerArrives() {
        ECPluginData plugin = pluginData("WhileListenedRefusal");
        RefusingOnceListener listener = new RefusingOnceListener();
        watch(ECListener.registerWhileListened(plugin, listener, ProducedEvent.class));

        ECEventSubscription<ProducedEvent> first = subscribe(ProducedEvent.class);
        assertEquals(1, listener.canRegisterCalls.get());
        assertTrue(ECListener.getRegistered(plugin).isEmpty(), "the listener refused, so nothing was registered");

        first.unsubscribe();
        assertEquals(0, listener.unregistrations.get(),
                "there is nothing to undo for a registration that never happened");

        subscribe(ProducedEvent.class);

        assertEquals(2, listener.canRegisterCalls.get(), "a refusal leaves the door open for the next opening");
        assertTrue(ECListener.getRegistered(plugin).contains(listener));
    }

    // ------------------------------------------------------------------
    //  fixtures
    // ------------------------------------------------------------------

    /** Local-only, so no native audience can ever be the presence a watch here reads. */
    static class ProducedEvent implements IECEvent {
    }

    static class OtherProducedEvent implements IECEvent {
    }

    static class ThirdProducedEvent implements IECEvent {
    }

    /** Counts the two ends of its own lifecycle while still doing what the real one does. */
    static class RecordingListener implements ECListener {
        final AtomicInteger registrations = new AtomicInteger();
        final AtomicInteger unregistrations = new AtomicInteger();

        @Override
        public boolean silentRegistration() {
            return true;
        }

        @Override
        public void onRegister() {
            registrations.incrementAndGet();
        }

        @Override
        public void unregisterThis() {
            unregistrations.incrementAndGet();
            ECListener.super.unregisterThis();
        }
    }

    /** Refuses the first registration and accepts every later one. */
    static class RefusingOnceListener extends RecordingListener {
        final AtomicInteger canRegisterCalls = new AtomicInteger();

        @Override
        public boolean canRegister() {
            return canRegisterCalls.incrementAndGet() > 1;
        }
    }

    private <T extends IECEvent> ECEventSubscription<T> subscribe(Class<T> eventType) {
        ECEventSubscription<T> subscription = ECEventBus.global().subscribe(eventType, event -> {
        });
        subscriptions.add(subscription);
        return subscription;
    }

    private ECListenerWatch watch(ECListenerWatch watch) {
        openedWatches.add(watch);
        return watch;
    }

    private ECPluginData pluginData(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        ECPluginData data = ECPluginManager.getOrCreateECorePluginData(new Object());
        createdPlugins.add(data);
        //ECListener.register logs through EverNifeCore.getEcPluginData(), which only the real bootstrap
        //ever sets - a listener registration with none installed would fail on the log line
        EverNifeCore.instance.onLoaderInstantiate(data);
        return data;
    }

}
