package br.com.finalcraft.evernifecore.minecraft.api;

import br.com.finalcraft.evernifecore.api.common.player.BaseFPlayer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public abstract class MinecraftFPlayer<DELEGATE> extends BaseFPlayer<DELEGATE> {

    public MinecraftFPlayer(DELEGATE delegate) {
        super(delegate);
    }

    public static MinecraftFPlayer of(OfflinePlayer player) {
        return new PlayerFPlayer(player);
    }

    public abstract OfflinePlayer getOfflinePlayer();

    @Override
    public String getName() {
        return getOfflinePlayer().getName();
    }

    @Override
    public UUID getUniqueId() {
        return getOfflinePlayer().getUniqueId();
    }

    @Override
    public void sendMessage(@Nonnull Component component) {
//        getOfflinePlayer().getPlayer().get
//        Message message = FCAdventureUtil.toHytaleMessage(component);
//        getPlayerRef().sendMessage(message);
    }

    @Override
    public boolean hasPermission(@Nonnull String permission) {
        return getOfflinePlayer().getPlayer().hasPermission(permission);
    }

    @Override
    public boolean isOnline() {
        return getOfflinePlayer().isOnline();
    }

    public @Nullable World getWorld() {
        Player player = getOfflinePlayer().getPlayer();

        if (player == null || !player.isOnline()) {
            return null;
        }

        return player.getWorld();
    }

    public @Nullable Location getLocation() {
        Player player = getOfflinePlayer().getPlayer();

        if (player == null || !player.isOnline()) {
            return null;
        }

        return player.getLocation();
    }

    public boolean teleportTo(Location targetLocation){
//        //Safe copy the reference... hytale location is Mutable!
//        Location safeTargetLocation = new Location(targetLocation.getWorld(), targetLocation.getPosition(), targetLocation.getRotation());
//
//        Ref<EntityStore> ref = getPlayerRef().getReference();
//
//        if (ref == null || !ref.isValid()) {
//            return false;
//        }
//
//        Store<EntityStore> store = ref.getStore();
//        if (store == null) {
//            return false;
//        }
//
//        World sourceWorld = store.getExternalData().getWorld();
//
//        World targetWorld = safeTargetLocation.getWorld().equals(sourceWorld.getName())
//                ? sourceWorld
//                : Universe.get().getWorld(safeTargetLocation.getWorld());
//
//        if (targetWorld == null){
//            return false;
//        }
//
//        AtomicReference<TransformComponent> transformComponent = new AtomicReference<>();
//        AtomicReference<HeadRotation> headRotationComponent = new AtomicReference<>();
//
//        FCScheduler.getHytaleScheduler().getSynchronizedAction().run(sourceWorld, () -> {
//            //Get these components only inside the sourceWorld
//            transformComponent.set(store.getComponent(ref, TransformComponent.getComponentType()));
//            headRotationComponent.set(store.getComponent(ref, HeadRotation.getComponentType()));
//        });
//
//        if (transformComponent.get() == null) {
//            return false;
//        }
//
//        Vector3d previousPos = transformComponent.get().getPosition().clone();
//        Vector3f previousRotation = headRotationComponent.get() == null
//                ? headRotationComponent.get().getRotation().clone()
//                : new Vector3f(0, 0, 0);
//
//        //Load the chunk if already not loaded, this will prevent the player from be teleported OUTSIDE THE FRICKING WORLD
//        WorldChunk worldChunk = targetWorld.isInThread()
//                ? targetWorld.getChunk(safeTargetLocation.getPosition().hashCode())
//                : targetWorld.getChunkAsync(safeTargetLocation.getPosition().hashCode()).join();
//
//        float pitch = safeTargetLocation.getRotation().getX();
//        float yaw = safeTargetLocation.getRotation().getY();
//        float roll = safeTargetLocation.getRotation().getZ();
//
//        FCScheduler.getHytaleScheduler().getSynchronizedAction().run(sourceWorld, () -> {
//            Teleport teleport = new Teleport(
//                    targetWorld,
//                    safeTargetLocation.getPosition(),
//                    new Vector3f(previousRotation.getPitch(), yaw, previousRotation.getRoll())
//            ).setHeadRotation(new Vector3f(pitch, yaw, roll));
//
//            //Teleport history must be called prior to the teleportation to prevent race conditions
//            TeleportHistory teleportHistoryComponent = store.ensureAndGetComponent(ref, TeleportHistory.getComponentType());
//            teleportHistoryComponent.append(sourceWorld, previousPos, previousRotation, "[EC] teleport " + getPlayerRef().getUsername() +   " to " + safeTargetLocation);
//
//            //do the actual teleport
//            store.addComponent(ref, Teleport.getComponentType(), teleport);
//        });
//
//        ECDebugModule.HYTALE_FPLAYER.debugModule(() -> {
//            Location origin = getLocation();
//
//            float displayYaw    = Float.isNaN(yaw)   ? previousRotation.getYaw()    * (180.0F / (float) Math.PI) : yaw   * (180.0F / (float) Math.PI);
//            float displayPitch  = Float.isNaN(pitch) ? previousRotation.getPitch()  * (180.0F / (float) Math.PI) : pitch * (180.0F / (float) Math.PI);
//            float displayRoll   = Float.isNaN(roll)  ? previousRotation.getRoll()   * (180.0F / (float) Math.PI) : roll  * (180.0F / (float) Math.PI);
//
//            return String.format("[TP] Teleporting player %s from %s to %s { Yaw:%s, Pitch:%s, Roll:%s }",
//                    getName(),
//                    FCHytaleVectorUtil.locPosAt(origin),
//                    FCHytaleVectorUtil.locPosAt(safeTargetLocation),
//                    displayYaw,
//                    displayPitch,
//                    displayRoll
//            );
//        });

        return true;
    }

    public Player getPlayer() {
        return getOfflinePlayer().getPlayer();
    }

    public static class PlayerFPlayer extends MinecraftFPlayer<OfflinePlayer> {

        public PlayerFPlayer(OfflinePlayer player) {
            super(player);
        }

        @Override
        public OfflinePlayer getOfflinePlayer() {
            return getDelegate();
        }

    }

}
