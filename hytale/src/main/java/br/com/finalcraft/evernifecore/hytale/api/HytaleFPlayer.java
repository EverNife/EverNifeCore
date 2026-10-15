package br.com.finalcraft.evernifecore.hytale.api;

import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.BaseFPlayer;
import br.com.finalcraft.evernifecore.api.platoverride.player.FPlayerAdapter;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleAdventureUtil;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleUtil;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import com.hypixel.hytale.builtin.teleport.components.TeleportHistory;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Rotation3fc;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class HytaleFPlayer<DELEGATE> extends BaseFPlayer<DELEGATE> {

    public HytaleFPlayer(DELEGATE delegate) {
        super(delegate);
    }

    public static HytaleFPlayer of(PlayerRef playerRef) {
        return new PlayerRefFPlayer(playerRef);
    }

    public static HytaleFPlayer of(Player player) {
        return new PlayerFPlayer(player);
    }

    public abstract PlayerRef getPlayerRef();

    @Override
    public String getName() {
        return getPlayerRef().getUsername();
    }

    @Override
    public UUID getUniqueId() {
        return getPlayerRef().getUuid();
    }

    @Override
    public void sendMessage(@Nonnull Component component) {
        Message message = FCHytaleAdventureUtil.toHytaleMessage(component);
        getPlayerRef().sendMessage(message);
    }

    @Override
    public boolean hasPermission(@Nonnull String permission) {
        return PermissionsModule.get().hasPermission(getPlayerRef().getUuid(), permission);
    }

    @Override
    public boolean isOnline() {
        return getPlayerRef() != null && getPlayerRef().isValid();
    }

    /**
     * Whether the universe's player storage holds a record of this player. The storage API answers only
     * with the whole set of stored players ({@code PlayerStorage.getPlayers()}, which the disk storage builds
     * by listing its folder); the per-player path is private to it, and the storage may not be on disk at
     * all. So each call costs one listing of every stored player - ask once per login, not per tick.
     */
    @Override
    public boolean hasPlayedBefore() {
        try {
            return Universe.get().getPlayerStorage().getPlayers().contains(getUniqueId());
        } catch (IOException e) {
            throw new IllegalStateException("Could not list the universe player storage to tell whether "
                    + getName() + " played before. The chained cause is what reading that directory threw.", e);
        }
    }

    @Override
    public void kick(@Nonnull String reason) {
        PlayerRef playerRef = getPlayerRef();
        if (playerRef == null || !playerRef.isValid()) return; // nothing to kick
        //Mirror KickCommand: disconnect is a packet write, same category as sendMessage above
        playerRef.getPacketHandler().disconnect(Message.raw(reason));
    }

    public @Nullable World getWorld() {
        Ref<EntityStore> ref = getPlayerRef().getReference();

        if (ref == null || !ref.isValid()) {
            return null;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return null;
        }

        return store.getExternalData().getWorld();
    }


    public @Nullable Location getMutableHytaleLocation() {
        Ref<EntityStore> ref = getPlayerRef().getReference();

        if (ref == null || !ref.isValid()) {
            return null;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return null;
        }

        World world = store.getExternalData().getWorld();

        return FCScheduler.getHytaleScheduler().getSynchronizedAction().runAndGet(world, () -> {
            TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
            if (transformComponent == null) {
                return null;
            }

            HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());

            Rotation3f rotation = headRotation != null ? headRotation.getRotation() : new Rotation3f(0.0F, 0.0F, 0.0F);
            Vector3d position = transformComponent.getPosition();

            return new Location(world.getName(), position, rotation);
        });
    }

    @Override
    public @Nullable FLocation getLocation() {
        Location hytaleLocation = getMutableHytaleLocation();

        if (hytaleLocation == null) {
            return null;
        }

        return FCHytaleUtil.adapt(hytaleLocation);
    }

    /**
     * Sends the player to {@code targetLocation} the way the server's own teleport command does. Never blocks: when the
     * target chunk is not in memory it is loaded first, and the teleport is put on the player from his world's thread
     * once it is.
     *
     * @return false when the player is in no world or the target world is not loaded; true once the teleport is on its
     *         way - it may still not happen (the player left, or another teleport was already pending on him)
     */
    @Override
    public boolean teleportTo(FLocation targetLocation){
        Ref<EntityStore> ref = getPlayerRef().getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        World sourceWorld = ref.getStore().getExternalData().getWorld();

        Location hyLocation = targetLocation.getDelegate() instanceof Location ? (Location) targetLocation.getDelegate() : null;
        WorldLocPos at = targetLocation.getWorldLocPos();
        String targetWorldName = hyLocation != null ? hyLocation.getWorld() : at.getWorldName();
        World targetWorld = targetWorldName.equals(sourceWorld.getName()) ? sourceWorld : Universe.get().getWorld(targetWorldName);
        if (targetWorld == null) {
            return false;
        }

        //copies: a Hytale Location is mutable. Coordinates alone keep the player's facing - NaN is the server's "leave it as is"
        Vector3d position = hyLocation != null ? new Vector3d(hyLocation.getPosition()) : new Vector3d(at.getX(), at.getY(), at.getZ());
        Rotation3fc facing = hyLocation != null ? hyLocation.getRotation().clone() : Rotation3f.NaN;

        chunkLoaded(targetWorld.getChunkStore(), ChunkUtil.indexChunkFromBlock(position.x(), position.z()))
                .thenRunAsync(() -> putTeleport(ref, targetWorld, position, facing), sourceWorld)
                .exceptionally(failed -> {
                    ECDebugModule.HYTALE_FPLAYER.debug("[TP] {} was not teleported to {} in {}: {}", getName(), position, targetWorldName, failed.toString());
                    return null;
                });
        return true;
    }

    /** On the player's world thread, outside any System: the teleport, after the entry the server's {@code /back} reads. */
    private void putTeleport(Ref<EntityStore> ref, World targetWorld, Vector3d position, Rotation3fc facing) {
        if (!ref.isValid()) {
            return;   //he left while the chunk loaded
        }
        Store<EntityStore> store = ref.getStore();
        if (store.getComponent(ref, Teleport.getComponentType()) != null) {
            ECDebugModule.HYTALE_FPLAYER.debug("[TP] {} already has a teleport pending; the one to {} is dropped.", getName(), position);
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        if (transform != null) {
            Rotation3f previousRotation = head != null ? head.getRotation().clone() : new Rotation3f();
            store.ensureAndGetComponent(ref, TeleportHistory.getComponentType()).append(store.getExternalData().getWorld(),
                    new Vector3d(transform.getPosition()), previousRotation, "[EC] teleport " + getName() + " to " + position);
        }
        store.addComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(targetWorld, position, facing));
        ECDebugModule.HYTALE_FPLAYER.debug("[TP] Teleporting player {} to {} in {}", getName(), position, targetWorld.getName());
    }

    /** The ChunkGrid load flag that also makes the chunk ticking, the one the server's own {@code chunk load} command passes. */
    private static final int LOAD_TICKING = 4;

    /**
     * Done once the chunk under a teleport target is in memory - a player who lands in an unloaded chunk falls out of
     * the world. Already done for a chunk in memory; otherwise the load, which the target world's thread finishes.
     */
    static CompletableFuture<?> chunkLoaded(ChunkGrid chunks, long chunkIndex) {
        if (chunks.getChunkReference(chunkIndex) != null) {
            return CompletableFuture.completedFuture(null);
        }
        return chunks.getChunkReferenceAsync(chunkIndex, LOAD_TICKING);
    }

    public Player getPlayer() {
        if (this instanceof PlayerFPlayer) {
            return ((PlayerFPlayer)this).getDelegate();
        }
        Player player = FCHytaleUtil.getComponent(getPlayerRef().getReference(), Player.getComponentType());
        if (player == null) {
            throw new IllegalStateException("The PlayerRef for '" + getName() + "' is not inside any World to get its Player variable.");
        }
        return player;
    }

    /**
     * One section of this player's inventory - an {@code InventoryComponent.*_SECTION_ID}, such as
     * {@link InventoryComponent#ARMOR_SECTION_ID} - or {@code null} when the player has no such section.
     */
    public @Nullable ItemContainer getInventorySection(int sectionId) {
        InventoryComponent section = FCHytaleUtil.getComponent(getPlayer().getReference(), InventoryComponent.getComponentTypeById(sectionId));
        return section == null ? null : section.getInventory();
    }

    public static class PlayerRefFPlayer extends FPlayerAdapter<PlayerRef> {

        public PlayerRefFPlayer(PlayerRef playerRef) {
            super(playerRef);
        }

        @Override
        public PlayerRef getPlayerRef() {
            return getDelegate();
        }

    }

    public static class PlayerFPlayer extends FPlayerAdapter<Player> {

        private PlayerRef playerRef;

        public PlayerFPlayer(Player player) {
            super(player);
        }

        /** Kept once read, so a player whose entity already left the world still answers its name. */
        @Override
        public PlayerRef getPlayerRef() {
            if (playerRef == null) {
                playerRef = FCHytaleUtil.getComponent(getDelegate().getReference(), PlayerRef.getComponentType());
            }
            return playerRef;
        }

    }

}
