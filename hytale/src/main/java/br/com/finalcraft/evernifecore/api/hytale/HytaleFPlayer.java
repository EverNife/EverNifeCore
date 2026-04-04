package br.com.finalcraft.evernifecore.api.hytale;

import br.com.finalcraft.evernifecore.api.common.player.BaseFPlayer;
import br.com.finalcraft.evernifecore.api.hytale.math.vector.LocPos;
import br.com.finalcraft.evernifecore.hytale.util.FCAdventureUtil;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import com.hypixel.hytale.builtin.teleport.components.TeleportHistory;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.NonNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
    public void sendMessage(@NonNull Component component) {
        Message message = FCAdventureUtil.toHytaleMessage(component);
        getPlayerRef().sendMessage(message);
    }

    @Override
    public boolean hasPermission(@NonNull String permission) {
        return PermissionsModule.get().hasPermission(getPlayerRef().getUuid(), permission);
    }

    @Override
    public boolean isOnline() {
        return getPlayerRef() != null && getPlayerRef().isValid();
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

    public @Nullable Location getLocation() {
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

            Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0.0F, 0.0F, 0.0F);
            Vector3d position = transformComponent.getPosition();

            return new Location(world.getName(), position.clone(), rotation.clone());
        });
    }

    public boolean teleportTo(Location targetLocation){
        //Safe copy the reference... hytale location is Mutable!
        Location safeTargetLocation = new Location(targetLocation.getWorld(), targetLocation.getPosition(), targetLocation.getRotation());

        Ref<EntityStore> ref = getPlayerRef().getReference();

        if (ref == null || !ref.isValid()) {
            return false;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return false;
        }

        World sourceWorld = store.getExternalData().getWorld();

        World targetWorld = safeTargetLocation.getWorld().equals(sourceWorld.getName())
                ? sourceWorld
                : Universe.get().getWorld(safeTargetLocation.getWorld());

        if (targetWorld == null){
            return false;
        }

        AtomicReference<TransformComponent> transformComponent = new AtomicReference<>();
        AtomicReference<HeadRotation> headRotationComponent = new AtomicReference<>();

        FCScheduler.getHytaleScheduler().getSynchronizedAction().run(sourceWorld, () -> {
            //Get these components only inside the sourceWorld
            transformComponent.set(store.getComponent(ref, TransformComponent.getComponentType()));
            headRotationComponent.set(store.getComponent(ref, HeadRotation.getComponentType()));
        });

        if (transformComponent.get() == null) {
            return false;
        }

        Vector3d previousPos = transformComponent.get().getPosition().clone();
        Vector3f previousRotation = headRotationComponent.get() == null
                ? headRotationComponent.get().getRotation().clone()
                : new Vector3f(0, 0, 0);

        //Load the chunk if already not loaded, this will prevent the player from be teleported OUTSIDE THE FRICKING WORLD
        WorldChunk worldChunk = targetWorld.isInThread()
                ? targetWorld.getChunk(safeTargetLocation.getPosition().hashCode())
                : targetWorld.getChunkAsync(safeTargetLocation.getPosition().hashCode()).join();

        float pitch = safeTargetLocation.getRotation().getX();
        float yaw = safeTargetLocation.getRotation().getY();
        float roll = safeTargetLocation.getRotation().getZ();

        FCScheduler.getHytaleScheduler().getSynchronizedAction().run(sourceWorld, () -> {
            Teleport teleport = new Teleport(
                    targetWorld,
                    safeTargetLocation.getPosition(),
                    new Vector3f(previousRotation.getPitch(), yaw, previousRotation.getRoll())
            ).setHeadRotation(new Vector3f(pitch, yaw, roll));

            //Teleport history must be called prior to the teleportation to prevent race conditions
            TeleportHistory teleportHistoryComponent = store.ensureAndGetComponent(ref, TeleportHistory.getComponentType());
            teleportHistoryComponent.append(sourceWorld, previousPos, previousRotation, "[EC] teleport " + getPlayerRef().getUsername() +   " to " + safeTargetLocation);

            //do the actual teleport
            store.addComponent(ref, Teleport.getComponentType(), teleport);
        });

        ECDebugModule.HYTALE_FPLAYER.debugModule(() -> {
            Location origin = getLocation();

            float displayYaw    = Float.isNaN(yaw)   ? previousRotation.getYaw()    * (180.0F / (float) Math.PI) : yaw   * (180.0F / (float) Math.PI);
            float displayPitch  = Float.isNaN(pitch) ? previousRotation.getPitch()  * (180.0F / (float) Math.PI) : pitch * (180.0F / (float) Math.PI);
            float displayRoll   = Float.isNaN(roll)  ? previousRotation.getRoll()   * (180.0F / (float) Math.PI) : roll  * (180.0F / (float) Math.PI);

            return String.format("[TP] Teleporting player %s from %s to %s { Yaw:%s, Pitch:%s, Roll:%s }",
                    getName(),
                    LocPos.at(origin),
                    LocPos.at(safeTargetLocation),
                    displayYaw,
                    displayPitch,
                    displayRoll
            );
        });

        return true;
    }

    public Player getPlayer() {
        if (this instanceof PlayerFPlayer) {
            return ((PlayerFPlayer)this).getDelegate();
        } else {
            UUID worldUuid = getPlayerRef().getWorldUuid();
            World world = Universe.get().getWorld(worldUuid);
            for (Player player : world.getPlayers()) {
                if (player.getPlayerRef().getUuid().equals(getPlayerRef().getUuid())) {
                    return  player;
                }
            }
            throw new IllegalStateException("The PlayerRef for '" + getName() + "' is not inside any World to get its Player variable.");
        }
    }

    public static class PlayerRefFPlayer extends HytaleFPlayer<PlayerRef> {

        public PlayerRefFPlayer(PlayerRef playerRef) {
            super(playerRef);
        }

        @Override
        public PlayerRef getPlayerRef() {
            return getDelegate();
        }

    }

    public static class PlayerFPlayer extends HytaleFPlayer<Player> {

        public PlayerFPlayer(Player player) {
            super(player);
        }

        @Override
        public PlayerRef getPlayerRef() {
            return getDelegate().getPlayerRef();
        }

    }

}
