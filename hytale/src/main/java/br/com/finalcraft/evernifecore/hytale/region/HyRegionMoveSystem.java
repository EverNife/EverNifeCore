package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleUtil;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.region.session.MoveVerdict;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3fc;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nullable;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Region borders on Hytale, which fires no move event: every tick this System reads where each player stands and,
 * only when that is another block or another world, hands the step to the {@link RegionMovementTracker}. A denied
 * step puts a {@link Teleport} back to {@link MoveVerdict#getRevertTo()} on the player.
 *
 * <p>The first tick a player is seen - a login, or the first tick after some region came to need MOVE - is a
 * {@link MoveCause#JOIN}: he is inside wherever he stands, unasked. A position reached through a teleport is a
 * {@link MoveCause#TELEPORT}; one in another world a {@link MoveCause#WORLD_CHANGE}. While a teleport is still pending
 * the player is left alone - his position has not caught up yet. There is no event object: {@code nativeEvent} is null.</p>
 */
public final class HyRegionMoveSystem extends EntityTickingSystem<EntityStore> {

    /** Where a player was last seen standing, on the thread of his world. */
    private static final class Seen {
        String world;
        double x, y, z;
        boolean teleported;

        Seen(String world, Vector3d at) {
            moveTo(world, at);
        }

        void moveTo(String world, Vector3d at) {
            this.world = world;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        boolean sameBlock(String world, Vector3d at) {
            return Math.floor(x) == Math.floor(at.x) && Math.floor(y) == Math.floor(at.y) && Math.floor(z) == Math.floor(at.z)
                    && this.world.equals(world);
        }

        WorldLocPos position() {
            return WorldLocPos.of(x, y, z, world);
        }
    }

    private final Supplier<RegionMovementTracker> tracker;
    //one System for every world, each ticking on its own thread
    private final Map<UUID, Seen> seen = new ConcurrentHashMap<>();

    HyRegionMoveSystem(Supplier<RegionMovementTracker> tracker) {
        this.tracker = tracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;   //the tracker runs on the world's own thread
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (player == null || transform == null) {
            return;
        }
        boolean teleporting = chunk.getComponent(index, Teleport.getComponentType()) != null
                || chunk.getComponent(index, PendingTeleport.getComponentType()) != null;
        World world = store.getExternalData().getWorld();
        WorldLocPos revertTo = step(player.getUuid(), () -> FCHytaleUtil.adapt(player), world.getName(), transform.getPosition(), teleporting);
        if (revertTo != null) {
            buffer.putComponent(chunk.getReferenceTo(index), Teleport.getComponentType(), teleport(revertTo, world, transform.getRotation()));
        }
    }

    /**
     * One tick of one player standing at {@code at} in {@code world}.
     *
     * @return where to send him back, or null when he stays where he is
     */
    @Nullable WorldLocPos step(UUID uuid, Supplier<FPlayer> player, String world, Vector3d at, boolean teleporting) {
        if (!EventSourceUsage.get().isNeeded(EventSource.MOVE)) {
            if (!seen.isEmpty()) {
                seen.clear();   //positions go stale while nobody listens; the next tick seeds everyone again
            }
            return null;
        }
        Seen last = seen.get(uuid);
        if (teleporting) {
            if (last != null) {
                last.teleported = true;
            }
            return null;
        }
        if (HyRegionContexts.isDisabled(world) || (last != null && last.sameBlock(world, at))) {
            return null;   //standing still or turning the head is most of the ticks; the tracker is asked on a block change only
        }
        WorldLocPos to = HyRegionContexts.position(world, at);
        if (last == null) {
            tracker.get().onJoin(player.get(), to);
            seen.put(uuid, new Seen(world, at));
            return null;
        }
        MoveCause cause = !last.world.equals(world) ? MoveCause.WORLD_CHANGE : last.teleported ? MoveCause.TELEPORT : MoveCause.MOVE;
        last.teleported = false;
        MoveVerdict verdict = tracker.get().onMove(player.get(), last.position(), to, cause, null);
        if (!verdict.isAllowed()) {
            return verdict.getRevertTo();
        }
        last.moveTo(world, at);
        return null;
    }

    /** The player left: his session ends, if this System ever saw him. Any thread. */
    void quit(UUID uuid, Supplier<FPlayer> player) {
        if (seen.remove(uuid) != null) {
            tracker.get().onQuit(player.get());
        }
    }

    boolean isFollowing(UUID uuid) {
        return seen.containsKey(uuid);
    }

    /** A teleport to {@code to}, keeping the player's facing; into another world only when that world is loaded. */
    static Teleport teleport(WorldLocPos to, @Nullable World here, Rotation3fc rotation) {
        Vector3d position = new Vector3d(to.getX(), to.getY(), to.getZ());
        if (here == null || here.getName().equals(to.getWorldName())) {
            return Teleport.createForPlayer(position, rotation);
        }
        World there = Universe.get().getWorld(to.getWorldName());
        return there == null ? Teleport.createForPlayer(position, rotation) : Teleport.createForPlayer(there, position, rotation);
    }
}
