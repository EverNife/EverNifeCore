package br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.ForgeRegistration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * On Mohist a listener handed a mod's own bus - Pixelmon's, typically - is subscribed on THAT bus, the
 * way the Arclight route does; only the no-bus form falls back to Forge's main bus.
 */
class MohistForgeListenerTest {

    @AfterEach
    void clearTheDouble() {
        MinecraftForge.EVENT_BUS = null;
    }

    @Test
    void aListenerHandedAModBusIsSubscribedOnThatBus() {
        RecordingBus forge = new RecordingBus();
        RecordingBus pixelmon = new RecordingBus();
        MinecraftForge.EVENT_BUS = forge;
        BattleListener listener = new BattleListener();

        ForgeRegistration registration = new MohistForgeListener(SubscribeEvent.class).registerListener(null, listener, pixelmon);

        assertEquals(Collections.singletonList(BattleEvent.class), pixelmon.eventTypes, "the handed bus must carry the handler");
        assertTrue(forge.eventTypes.isEmpty(), "Forge's main bus must not get a listener that asked for another bus");

        pixelmon.post(new BattleEvent());
        assertEquals(1, listener.heard, "an event posted on the handed bus must reach the listener");

        registration.unregister();
        assertEquals(pixelmon.consumers, pixelmon.unregistered, "unregistering must take the handler off the bus it is on");
    }

    @Test
    void everyHandedBusGetsTheListener() {
        MinecraftForge.EVENT_BUS = new RecordingBus();
        RecordingBus first = new RecordingBus();
        RecordingBus second = new RecordingBus();

        new MohistForgeListener(SubscribeEvent.class).registerListener(null, new BattleListener(), first, second);

        assertEquals(Collections.singletonList(BattleEvent.class), first.eventTypes);
        assertEquals(Collections.singletonList(BattleEvent.class), second.eventTypes);
    }

    @Test
    void withNoBusTheListenerGoesOnForgesMainBus() {
        RecordingBus forge = new RecordingBus();
        MinecraftForge.EVENT_BUS = forge;

        new MohistForgeListener(SubscribeEvent.class).registerListener(null, new BattleListener());

        assertEquals(Collections.singletonList(BattleEvent.class), forge.eventTypes);
    }

    @Test
    void anEmptyBusListMeansForgesMainBus() {
        RecordingBus forge = new RecordingBus();
        MinecraftForge.EVENT_BUS = forge;

        new MohistForgeListener(SubscribeEvent.class).registerListener(null, new BattleListener(), new Object[0]);

        assertEquals(Collections.singletonList(BattleEvent.class), forge.eventTypes);
    }

    public static class BattleEvent {
    }

    public static class BattleListener implements ECListener {
        int heard;

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onBattle(BattleEvent event) {
            heard++;
        }
    }

    /** A modern bus that keeps what it was handed, and can post to it. */
    public static class RecordingBus implements IEventBus {
        final List<Class<?>> eventTypes = new ArrayList<>();
        final List<Consumer<?>> consumers = new ArrayList<>();
        final List<Object> unregistered = new ArrayList<>();

        @Override
        public <T> void addListener(EventPriority priority, boolean receiveCancelled, Class<T> eventType, Consumer<T> consumer) {
            eventTypes.add(eventType);
            consumers.add(consumer);
        }

        public void unregister(Object listener) {
            unregistered.add(listener);
        }

        @SuppressWarnings("unchecked")
        void post(Object event) {
            for (Consumer<?> consumer : consumers) {
                ((Consumer<Object>) consumer).accept(event);
            }
        }
    }
}
