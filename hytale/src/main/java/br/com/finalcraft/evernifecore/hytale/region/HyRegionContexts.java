package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleUtil;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import jakarta.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.function.Supplier;

/** Internal: what every region System repeats to build an ActionContext - the disabled-world check, positions, type keys and actors. */
public final class HyRegionContexts {

    public static final String NAMESPACE = "hytale";
    /** What breaks blocks with no entity behind the event: on 0.7.1 that is an explosion. */
    public static final TypeKey EXPLOSION = TypeKey.of("environment", "explosion");
    /** An entity that is neither a player nor an NPC - a dropped item, a projectile: the server gives it no type id. */
    public static final TypeKey ENTITY = TypeKey.of(NAMESPACE, "entity");

    private HyRegionContexts() {
    }

    /** The only reader of {@code Regions.disabled-worlds} on Hytale: nothing in such a world is asked of a region. */
    public static boolean isDisabled(String world) {
        return ECSettings.REGIONS_DISABLED_WORLDS.contains(world);
    }

    public static WorldLocPos position(String world, Vector3i block) {
        return WorldLocPos.of(block.x, block.y, block.z, world);
    }

    public static WorldLocPos position(String world, Vector3d at) {
        return WorldLocPos.of(at.x, at.y, at.z, world);
    }

    /**
     * An asset id as a key, exactly as the server spells it ({@code hytale:Rock_Stone}); an id that already carries a
     * namespace keeps it.
     */
    public static TypeKey key(String assetId) {
        return assetId.indexOf(':') > 0 ? TypeKey.parse(assetId) : TypeKey.of(NAMESPACE, assetId);
    }

    /** A block the event did not name is the server's own {@code Unknown}. */
    public static TypeKey key(@Nullable BlockType block) {
        return key(block == null ? BlockType.UNKNOWN_KEY : block.getId());
    }

    /** What a hand holds; null for an empty hand. */
    public static @Nullable TypeKey itemKey(@Nullable ItemStack stack) {
        return ItemStack.isEmpty(stack) ? null : key(stack.getItemId());
    }

    /**
     * The role of an NPC by the attitude it shows players: hostile is a {@link ECEntityType#MONSTER}, any other a
     * {@link ECEntityType#ANIMAL}, and a role with no attitude at all - a scripted one - an {@link ECEntityType#NPC}.
     */
    public static ECEntityType npcType(@Nullable Attitude towardsPlayers) {
        if (towardsPlayers == null) {
            return ECEntityType.NPC;
        }
        return towardsPlayers == Attitude.HOSTILE ? ECEntityType.MONSTER : ECEntityType.ANIMAL;
    }

    /**
     * An actor from what the entity is: a player acts as itself, an NPC by its type id and {@link #npcType}, anything
     * else is {@link #ENTITY}. An NPC never has a player behind it.
     */
    static Actor actor(@Nullable FPlayer player, @Nullable String npcTypeId, @Nullable Attitude towardsPlayers,
                       @Nullable Supplier<NativeEntity> handle) {
        if (player != null) {
            return Actor.player(player, handle);
        }
        if (npcTypeId != null) {
            return Actor.entity(key(npcTypeId), npcType(towardsPlayers), null, handle);
        }
        return Actor.entity(ENTITY, ECEntityType.OTHER, null, handle);
    }

    /** {@code ref} as an actor, read on the thread of its store; the handle is made only if a flag asks for it. */
    public static Actor actor(Ref<EntityStore> ref, ComponentAccessor<EntityStore> store) {
        Supplier<NativeEntity> handle = handle(ref, store);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            return actor(FCHytaleUtil.adapt(playerRef), null, null, handle);
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null) {
            WorldSupport support = store.getComponent(ref, WorldSupport.getComponentType());
            return actor(null, npc.getNPCTypeId(), support == null ? null : support.getDefaultPlayerAttitude(), handle);
        }
        return actor(null, null, null, handle);
    }

    /** The player behind {@code ref}, or null when it is no player. */
    public static @Nullable FPlayer player(Ref<EntityStore> ref, ComponentAccessor<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        return playerRef == null ? null : FCHytaleUtil.adapt(playerRef);
    }

    /** A projectile acts as itself, with the player who shot it - if a player did - behind it. */
    public static Actor projectile(Ref<EntityStore> projectile, @Nullable FPlayer shooter, ComponentAccessor<EntityStore> store) {
        return shooter == null ? actor(projectile, store) : Actor.entity(ENTITY, ECEntityType.OTHER, shooter, handle(projectile, store));
    }

    private static Supplier<NativeEntity> handle(Ref<EntityStore> ref, ComponentAccessor<EntityStore> store) {
        return () -> {
            UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
            return uuid == null ? null : new NativeEntity(ref, uuid.getUuid());
        };
    }
}
