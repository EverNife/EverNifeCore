package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventHandler;
import br.com.finalcraft.evernifecore.eventbus.ECEventPriority;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where an {@code @ECEventHandler} method ends up on Bukkit. The parameter decides: an EC event goes
 * to the bus and nowhere else, a Bukkit event is registered with the server by hand, and anything
 * else is a listener that does not load.
 */
class McECEventHandlersTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void aHandlerHearsItsOwnTypeAndNoneOfTheOnesSharingItsHandlerList() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ChildOnlyListener listener = new ChildOnlyListener();
            world.registerECListener(listener);

            //all three resolve to ParentEvent's HandlerList, so all three reach this registration and
            //only the executor's filter tells them apart
            world.getPluginManager().callEvent(new ParentEvent());
            world.getPluginManager().callEvent(new SiblingChildEvent());
            assertTrue(listener.heard.isEmpty(), "neither the supertype nor the sibling is what the handler asked for");

            world.getPluginManager().callEvent(new ChildEvent());
            assertEquals(Collections.singletonList("child"), listener.heard);
        }
    }

    @Test
    void aHandlerNamingAnECEventIsTakenByTheBusAlone() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            EcEventListener listener = new EcEventListener();
            world.registerECListener(listener);

            ECEventBus.global().post(new SampleEcEvent());

            //an ECEvent is a Bukkit event too: registering it with the server as well would have it
            //arrive twice, once from the local phase and once from the mirror that phase feeds
            assertEquals(Collections.singletonList("bus"), listener.heard);
        }
    }

    @Test
    void theBukkitEventHandlerAnnotationKeepsBeingRegistered() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            BukkitAnnotatedListener listener = new BukkitAnnotatedListener();
            world.registerECListener(listener);

            world.getPluginManager().callEvent(new ParentEvent());

            assertEquals(Collections.singletonList("bukkit"), listener.heard,
                    "the server's own scan still runs alongside the programmatic route");
        }
    }

    @Test
    void aParameterThatIsNoEventAtAllRefusesTheWholeListener() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            BrokenListener listener = new BrokenListener();

            RuntimeException refused = assertThrows(RuntimeException.class, () -> world.registerECListener(listener));
            assertTrue(refused.getMessage().contains("Nothing of this listener stayed registered"), refused.getMessage());

            world.getPluginManager().callEvent(new ParentEvent());
            assertTrue(listener.heard.isEmpty(), "the rollback took the @EventHandler method with it, "
                    + "instead of leaving half a listener answering events");
        }
    }

    @Test
    void theECScaleIsCutAtTheMidpointsBetweenItsOwnSteps() {
        assertEquals(EventPriority.LOWEST, McECEventHandlers.toBukkitPriority(ECEventPriority.FIRST.getValue()));
        assertEquals(EventPriority.LOW, McECEventHandlers.toBukkitPriority(ECEventPriority.EARLY.getValue()));
        assertEquals(EventPriority.NORMAL, McECEventHandlers.toBukkitPriority(ECEventPriority.NORMAL.getValue()));
        assertEquals(EventPriority.HIGH, McECEventHandlers.toBukkitPriority(ECEventPriority.LATE.getValue()));
        assertEquals(EventPriority.HIGHEST, McECEventHandlers.toBukkitPriority(ECEventPriority.LAST.getValue()));

        //a hand-picked value lands on the side of the step it is nearest to
        assertEquals(EventPriority.LOWEST, McECEventHandlers.toBukkitPriority((short) -16384));
        assertEquals(EventPriority.LOW, McECEventHandlers.toBukkitPriority((short) -16383));
        assertEquals(EventPriority.NORMAL, McECEventHandlers.toBukkitPriority((short) -5461));
        assertEquals(EventPriority.HIGH, McECEventHandlers.toBukkitPriority((short) 5462));
        assertEquals(EventPriority.HIGHEST, McECEventHandlers.toBukkitPriority((short) 16384));

        for (ECEventPriority priority : ECEventPriority.values()) {
            assertNotEquals(EventPriority.MONITOR, McECEventHandlers.toBukkitPriority(priority.getValue()),
                    "MONITOR is Bukkit's watch-never-change slot and nothing on the EC scale claims it");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  fixtures
    // -----------------------------------------------------------------------------------------------------------------

    /** Owns the HandlerList its two subtypes have to share, the way EntityDamageEvent does on a server. */
    public static class ParentEvent extends Event {
        private static final HandlerList handlers = new HandlerList();

        @Override
        public HandlerList getHandlers() {
            return handlers;
        }

        public static HandlerList getHandlerList() {
            return handlers;
        }
    }

    public static class ChildEvent extends ParentEvent {
    }

    public static class SiblingChildEvent extends ParentEvent {
    }

    /** Platform-visible, so it is a Bukkit event AND an IECEvent - the parameter both routes could claim. */
    public static class SampleEcEvent extends ECEvent {
    }

    public static class ChildOnlyListener implements ECListener {
        final List<String> heard = new ArrayList<>();

        @ECEventHandler
        public void onChild(ChildEvent event) {
            heard.add("child");
        }
    }

    public static class EcEventListener implements ECListener {
        final List<String> heard = new ArrayList<>();

        @ECEventHandler
        public void onSample(SampleEcEvent event) {
            heard.add("bus");
        }
    }

    public static class BukkitAnnotatedListener implements ECListener {
        final List<String> heard = new ArrayList<>();

        @EventHandler
        public void onParent(ParentEvent event) {
            heard.add("bukkit");
        }
    }

    public static class BrokenListener implements ECListener {
        final List<String> heard = new ArrayList<>();

        @ECEventHandler
        public void onNothing(String notAnEventAtAll) {
            heard.add("nothing");
        }

        @EventHandler
        public void onParent(ParentEvent event) {
            heard.add("bukkit");
        }
    }

}
