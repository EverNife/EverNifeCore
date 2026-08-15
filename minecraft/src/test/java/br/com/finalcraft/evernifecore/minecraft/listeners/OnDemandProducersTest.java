package br.com.finalcraft.evernifecore.minecraft.listeners;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.minecraft.api.events.ECPlayerChangeChunkEvent;
import br.com.finalcraft.evernifecore.minecraft.api.events.ECPlayerCraftItemEvent;
import br.com.finalcraft.evernifecore.minecraft.api.events.damage.ECPetDamagedByPet;
import br.com.finalcraft.evernifecore.minecraft.api.events.damage.ECPetDamagedByPlayer;
import br.com.finalcraft.evernifecore.minecraft.api.events.damage.ECPlayerDamagedByPet;
import br.com.finalcraft.evernifecore.minecraft.api.events.damage.ECPlayerDamagedByPlayer;
import br.com.finalcraft.evernifecore.minecraft.api.events.damage.ECPlayerdataDamagePlayerdata;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.minecraft.testkit.Doubles;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core's own producers are hooked on the hottest native events there are, and they only exist for
 * whoever listens to what they produce. So the server carries them exactly while somebody does, the
 * listener that appears may be a Bukkit one the bus cannot see by itself, and the work a producer does
 * beyond its gate never runs for nobody.
 */
class OnDemandProducersTest {

    @TempDirNobodyCleans
    Path tempDir;

    // -----------------------------------------------------------------------------------------------------------------
    //  Registered while listened
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aBukkitListenerOnTheChunkEventIsWhatPutsTheMoveProducerOnTheServer() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ECListenerWatch watch = ECListener.registerWhileListened(world.getPluginData(),
                    new PlayerMoveListener(), ECPlayerChangeChunkEvent.class);
            try {
                assertFalse(ECEventBus.global().hasListeners(ECPlayerChangeChunkEvent.class));
                assertFalse(isRegistered(PlayerMoveEvent.getHandlerList(), PlayerMoveListener.class),
                        "nobody wants the chunk event, so nothing of ours sits on PlayerMoveEvent");

                Listener onChunkChange = new Listener() {
                };
                world.getPluginManager().registerEvent(ECPlayerChangeChunkEvent.class, onChunkChange,
                        EventPriority.NORMAL, (ignoredListener, event) -> {
                        }, world.getPlugin());

                assertTrue(ECEventBus.global().hasListeners(ECPlayerChangeChunkEvent.class),
                        "a Bukkit registration is a listener too, and the bus is told about it");
                assertTrue(isRegistered(PlayerMoveEvent.getHandlerList(), PlayerMoveListener.class),
                        "and that is what registers the producer, with no post and no tick in between");

                HandlerList.unregisterAll(onChunkChange);

                assertFalse(ECEventBus.global().hasListeners(ECPlayerChangeChunkEvent.class));
                assertFalse(isRegistered(PlayerMoveEvent.getHandlerList(), PlayerMoveListener.class),
                        "the last one asking left, and the producer left with it");
            } finally {
                watch.stop();
                ECListener.unregisterAll(world.getPluginData());
            }
        }
    }

    @Test
    void aBusSubscriberToTheCraftEventIsWhatPutsTheCraftProducerOnTheServer() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ECListenerWatch watch = ECListener.registerWhileListened(world.getPluginData(),
                    new PlayerCraftListener(), ECPlayerCraftItemEvent.class);
            try {
                assertFalse(isRegistered(CraftItemEvent.getHandlerList(), PlayerCraftListener.class),
                        "nobody wants the craft event, so nothing of ours sits on CraftItemEvent");

                //the other route in: a bus subscription, no Bukkit registration anywhere
                ECEventSubscription<ECPlayerCraftItemEvent> subscription =
                        ECEventBus.global().subscribe(ECPlayerCraftItemEvent.class, event -> {
                        });

                assertTrue(isRegistered(CraftItemEvent.getHandlerList(), PlayerCraftListener.class),
                        "a bus subscriber is what the old per-list hook could never see; the watch does");

                subscription.unsubscribe();

                assertFalse(isRegistered(CraftItemEvent.getHandlerList(), PlayerCraftListener.class),
                        "the last one asking left, and the producer left with it");
            } finally {
                watch.stop();
                ECListener.unregisterAll(world.getPluginData());
            }
        }
    }

    @Test
    void aBukkitListenerOnTheDamageFamilyBaseIsWhatPutsTheDamageProducerOnTheServer() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ECListenerWatch watch = ECListener.registerWhileListened(world.getPluginData(),
                    new PlayerDamageByEntityListener(),
                    ECPlayerDamagedByPlayer.class, ECPlayerDamagedByPet.class,
                    ECPetDamagedByPet.class, ECPetDamagedByPlayer.class);
            try {
                assertFalse(isRegistered(EntityDamageEvent.getHandlerList(), PlayerDamageByEntityListener.class));

                Listener onTheFamilyBase = new Listener() {
                };
                world.getPluginManager().registerEvent(ECPlayerdataDamagePlayerdata.class, onTheFamilyBase,
                        EventPriority.NORMAL, (ignoredListener, event) -> {
                        }, world.getPlugin());

                for (Class<? extends IECEvent> concrete : damageFamily()) {
                    assertTrue(ECEventBus.global().hasListeners(concrete),
                            concrete.getSimpleName() + " shares the base's handler list, so the base's listener hears it");
                }
                assertTrue(isRegistered(EntityDamageEvent.getHandlerList(), PlayerDamageByEntityListener.class));

                HandlerList.unregisterAll(onTheFamilyBase);

                for (Class<? extends IECEvent> concrete : damageFamily()) {
                    assertFalse(ECEventBus.global().hasListeners(concrete));
                }
                assertFalse(isRegistered(EntityDamageEvent.getHandlerList(), PlayerDamageByEntityListener.class));
            } finally {
                watch.stop();
                ECListener.unregisterAll(world.getPluginData());
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What the move producer does before it decides to post
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aMoveInsideOneChunkPostsNothingAndNeverLooksAChunkUp() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            List<ECPlayerChangeChunkEvent> posted = new ArrayList<>();
            world.subscribe(ECPlayerChangeChunkEvent.class, posted::add);

            ChunkCountingWorld gameWorld = new ChunkCountingWorld();
            new PlayerMoveListener().onPlayerMove(moveEvent(gameWorld, 3, 3, 5, 5));

            assertTrue(posted.isEmpty(), "the player is where it was, chunk-wise");
            assertEquals(0, gameWorld.lookups.get(),
                    "the gate is arithmetic on the location - a chunk-map lookup is the cost it exists to avoid");
        }
    }

    @Test
    void crossingAChunkBorderPostsOnceWithTheChunksTheWorldHandedBack() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            List<ECPlayerChangeChunkEvent> posted = new ArrayList<>();
            world.subscribe(ECPlayerChangeChunkEvent.class, posted::add);

            ChunkCountingWorld gameWorld = new ChunkCountingWorld();
            new PlayerMoveListener().onPlayerMove(moveEvent(gameWorld, 3, 5, 17, 5));

            assertEquals(1, posted.size());
            assertEquals(2, gameWorld.lookups.get(), "one lookup for where it came from, one for where it went");
            assertSame(gameWorld.firstChunk, posted.get(0).getFrom());
            assertSame(gameWorld.secondChunk, posted.get(0).getTo());
        }
    }

    @Test
    void crossingAChunkBorderWithNobodyListeningNeverLooksAChunkUp() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            assertFalse(ECEventBus.global().hasListeners(ECPlayerChangeChunkEvent.class),
                    "this world starts with nobody on the chunk event");

            ChunkCountingWorld gameWorld = new ChunkCountingWorld();
            new PlayerMoveListener().onPlayerMove(moveEvent(gameWorld, 3, 5, 17, 5));

            assertEquals(0, gameWorld.lookups.get(), "the two chunks live inside the supplier, which never ran");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  fixtures
    // -----------------------------------------------------------------------------------------------------------------

    /** A world that hands back a different chunk per ask and counts how many times it was asked. */
    static final class ChunkCountingWorld {
        final Chunk firstChunk = Doubles.of(Chunk.class).build();
        final Chunk secondChunk = Doubles.of(Chunk.class).build();
        final AtomicInteger lookups = new AtomicInteger();
        final World world = Doubles.of(World.class)
                .on("getChunkAt", args -> lookups.incrementAndGet() == 1 ? firstChunk : secondChunk)
                .build();
    }

    /**
     * A move between two block positions of the same world. The chunks are the arguments of one
     * constructor call, so the world is asked for the origin one first.
     */
    private static PlayerMoveEvent moveEvent(ChunkCountingWorld gameWorld, int fromX, int fromZ, int toX, int toZ) {
        return new PlayerMoveEvent(Doubles.of(Player.class).build(),
                new Location(gameWorld.world, fromX, 64, fromZ),
                new Location(gameWorld.world, toX, 64, toZ));
    }

    private static List<Class<? extends IECEvent>> damageFamily() {
        List<Class<? extends IECEvent>> family = new ArrayList<>();
        family.add(ECPlayerDamagedByPlayer.class);
        family.add(ECPlayerDamagedByPet.class);
        family.add(ECPetDamagedByPet.class);
        family.add(ECPetDamagedByPlayer.class);
        return family;
    }

    private static boolean isRegistered(HandlerList list, Class<? extends ECListener> producer) {
        for (RegisteredListener registered : list.getRegisteredListeners()) {
            if (producer.isInstance(registered.getListener())) {
                return true;
            }
        }
        return false;
    }

}
