package br.com.finalcraft.evernifecore.conformance.hytale;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The few world chores the suites share. Hytale has no main thread: every world ticks on its own, and a component or a
 * block is touched only there - {@link #on} hops to it. Waiting is never done on a world thread: {@link #ticks} watches
 * the world's tick counter from a thread of its own.
 */
public final class HyWorld {

    private static final ScheduledExecutorService WATCHER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "ecc-tick-watcher");
        thread.setDaemon(true);
        return thread;
    });

    private HyWorld() {
    }

    public static World getDefault() {
        return Universe.get().getDefaultWorld();
    }

    /** Runs {@code task} on {@code world}'s thread. */
    public static <T> CompletableFuture<T> on(World world, Callable<T> task) {
        CompletableFuture<T> answer = new CompletableFuture<>();
        world.execute(() -> {
            try {
                answer.complete(task.call());
            } catch (Throwable failed) {
                answer.completeExceptionally(failed);
            }
        });
        return answer;
    }

    /** Completes, off the world's thread, once {@code world} has ticked {@code count} more times. */
    public static CompletableFuture<Void> ticks(World world, int count) {
        long until = world.getTick() + count;
        CompletableFuture<Void> done = new CompletableFuture<>();
        ScheduledFuture<?>[] poll = new ScheduledFuture<?>[1];
        poll[0] = WATCHER.scheduleAtFixedRate(() -> {
            if (world.getTick() >= until && done.complete(null)) {
                poll[0].cancel(false);
            }
        }, 5, 5, TimeUnit.MILLISECONDS);
        return done;
    }

    /** The online player behind {@code player}; throws naming him when he is not in this server. */
    public static PlayerRef player(FPlayer player) {
        PlayerRef found = Universe.get().getPlayer(player.getName(), NameMatching.EXACT);
        if (found == null || found.getReference() == null || !found.getReference().isValid()) {
            throw new IllegalStateException(player.getName() + " is not in this server - join him (mc_exec player_join) before the run");
        }
        return found;
    }

    public static World worldOf(PlayerRef player) {
        return Universe.get().getWorld(player.getWorldUuid());
    }

    /** Where {@code player} stands; on his world's thread. */
    public static Vector3d position(PlayerRef player) {
        Ref<EntityStore> ref = player.getReference();
        return new Vector3d(ref.getStore().getComponent(ref, TransformComponent.getComponentType()).getPosition());
    }

    /** Sends {@code player} to {@code to} the way the game does, with a {@link Teleport}; on his world's thread. */
    public static void teleport(PlayerRef player, World world, Vector3d to) {
        Ref<EntityStore> ref = player.getReference();
        ref.getStore().putComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(world, to, new Rotation3f()));
    }

    /** Whether a teleport is still waiting to be played on {@code ref}; on its world's thread. */
    public static boolean isTeleporting(Ref<EntityStore> ref) {
        Store<EntityStore> store = ref.getStore();
        return store.getComponent(ref, Teleport.getComponentType()) != null;
    }

    /**
     * Answers for the client a teleport the server is waiting on, as a real client's acknowledgement does: the synthetic
     * player has no client, so without this every teleport stays pending on him forever. On his world's thread.
     *
     * @return whether one was pending
     */
    public static boolean acknowledgeTeleport(PlayerRef player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref.getStore().getComponent(ref, PendingTeleport.getComponentType()) == null) {
            return false;
        }
        ref.getStore().removeComponent(ref, PendingTeleport.getComponentType());
        return true;
    }

    /** The block id at a position, or -1 when its chunk section is not loaded; on the world's thread. */
    public static int blockId(World world, int x, int y, int z) {
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null) {
            return -1;
        }
        BlockSection blocks = section.getStore().getComponent(section, BlockSection.getComponentType());
        return blocks.getChunkSection().get(ChunkUtil.indexBlock(x, y, z));
    }

    /** Puts {@code blockId} (an asset id, {@code Empty} for air) at a position, as the server's own {@code block set}. */
    public static void setBlock(World world, int x, int y, int z, String blockId) {
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null) {
            throw new IllegalStateException("the chunk section at " + x + "," + y + "," + z + " is not loaded - stand the player"
                    + " nearer, the server keeps only the chunks around someone");
        }
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        if (type == null) {
            throw new IllegalArgumentException("no block type '" + blockId + "' on this server");
        }
        BlockOperations.setBlock(world.getChunkStore(), section, x, y, z, BlockType.getAssetMap().getIndex(blockId), type, 0, 0, 0);
    }

    /**
     * The median of the last {@code samples} tick lengths the world measured itself, in milliseconds - the work of a
     * tick, without the sleep that paces it.
     */
    public static double medianTickMillis(World world, int samples) {
        long[] values = world.getBufferedTickLengthMetricSet().getAllValues();
        return medianMillis(values, samples);
    }

    /** The median of the last {@code samples} entries of {@code nanos}, in milliseconds; NaN when there is none. */
    static double medianMillis(long[] nanos, int samples) {
        int from = Math.max(0, nanos.length - samples);
        long[] window = Arrays.copyOfRange(nanos, from, nanos.length);
        if (window.length == 0) {
            return Double.NaN;
        }
        Arrays.sort(window);
        int mid = window.length / 2;
        double median = window.length % 2 == 1 ? window[mid] : (window[mid - 1] + window[mid]) / 2.0;
        return median / 1_000_000.0;
    }
}
