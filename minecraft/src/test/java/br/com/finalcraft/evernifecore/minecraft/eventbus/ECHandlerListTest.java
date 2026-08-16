package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Bukkit registration happens where the bus cannot see it. The list is what reports it, and a
 * listener watch is what a producer reads that report through - so switching an expensive source on
 * costs the registration itself, with no post and no tick in between.
 */
class ECHandlerListTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void registeringABukkitListenerOpensTheWatchAndDroppingThePluginClosesIt() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            List<String> transitions = new ArrayList<>();
            ECListenerWatch watch = ECEventBus.global().watchListeners(
                    () -> transitions.add("first"), () -> transitions.add("gone"), WatchedEvent.class);
            try {
                assertEquals(Collections.emptyList(), transitions, "the watch is born absent - nobody listens yet");

                world.listen(WatchedEvent.class, event -> {
                });

                assertEquals(Collections.singletonList("first"), transitions,
                        "the registration itself reported it, with nothing else driving the bus");
                assertTrue(watch.hasListeners());

                HandlerList.unregisterAll(world.getPlugin());

                assertEquals(Arrays.asList("first", "gone"), transitions, "and so did the plugin's departure");
                assertFalse(watch.hasListeners());
            } finally {
                watch.stop();
            }
        }
    }

    @Test
    void unregisteringASingleBukkitListenerClosesTheWatchToo() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            List<String> transitions = new ArrayList<>();
            ECListenerWatch watch = ECEventBus.global().watchListeners(
                    () -> transitions.add("first"), () -> transitions.add("gone"), SingleListenerEvent.class);
            try {
                Listener listener = new Listener() {
                };
                world.getPluginManager().registerEvent(SingleListenerEvent.class, listener, EventPriority.NORMAL,
                        (ignoredListener, event) -> {
                        }, world.getPlugin());

                assertEquals(Collections.singletonList("first"), transitions);

                HandlerList.unregisterAll(listener);

                assertEquals(Arrays.asList("first", "gone"), transitions,
                        "the plugin is still enabled - what left is the one listener");
            } finally {
                watch.stop();
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  fixtures
    // -----------------------------------------------------------------------------------------------------------------

    /** Its own list, so what the watch follows is this event and not whoever shares the family one. */
    static class WatchedEvent extends ECEvent {
        public static HandlerList getHandlerList() {
            return (HandlerList) ECEvent.getHandlerListOf(WatchedEvent.class);
        }
    }

    static class SingleListenerEvent extends ECEvent {
        public static HandlerList getHandlerList() {
            return (HandlerList) ECEvent.getHandlerListOf(SingleListenerEvent.class);
        }
    }

}
