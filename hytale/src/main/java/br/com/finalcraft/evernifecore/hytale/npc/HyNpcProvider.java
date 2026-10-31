package br.com.finalcraft.evernifecore.hytale.npc;

import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.npc.NpcProvider;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import jakarta.annotation.Nullable;
import org.joml.Vector3d;

import java.util.Optional;
import java.util.UUID;

/**
 * Hytale's NPCs: every entity with an {@link NPCEntity} component, {@code hytale:<uuid>}. Its role is not in the
 * reference - it is already the actor's key ({@code hytale:<npc type id>}). Hytale stores no NPC position apart from the
 * live entity, so {@link #getLocation} and {@link #getName} read the entity too: on the thread of its world, like
 * {@link #find}.
 */
public final class HyNpcProvider implements NpcProvider {

    public static final String ID = "hytale";

    /** {@code hytale:<uuid>} of an entity with {@code npc} and {@code uuid}; null when either is missing - a player has no NPC component. */
    public static @Nullable NpcRef refOf(@Nullable NPCEntity npc, @Nullable UUIDComponent uuid) {
        return npc == null || uuid == null ? null : NpcRef.of(ID, uuid.getUuid().toString());
    }

    @Override
    public String getId() {
        return ID;
    }

    /** Always: NPCs are the server's own. */
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<NpcRef> resolve(NativeEntity entity) {
        return Optional.ofNullable(refOf(entity.getComponent(NPCEntity.getComponentType()), entity.getComponent(UUIDComponent.getComponentType())));
    }

    /** World thread only: the entity of a world is looked up on that world's thread. */
    @Override
    public Optional<NativeEntity> find(NpcRef ref) {
        Located found = locate(ref);
        return found == null ? Optional.empty() : Optional.of(new NativeEntity(found.ref, found.uuid));
    }

    @Override
    public Optional<WorldLocPos> getLocation(NpcRef ref) {
        Located found = locate(ref);
        TransformComponent transform = found == null ? null : found.ref.getStore().getComponent(found.ref, TransformComponent.getComponentType());
        if (transform == null) {
            return Optional.empty();
        }
        Vector3d at = transform.getPosition();
        return Optional.of(WorldLocPos.of(at.x, at.y, at.z, found.world.getName()));
    }

    @Override
    public Optional<String> getName(NpcRef ref) {
        Located found = locate(ref);
        NPCEntity npc = found == null ? null : found.ref.getStore().getComponent(found.ref, NPCEntity.getComponentType());
        return npc == null ? Optional.empty() : Optional.ofNullable(npc.getRoleName());
    }

    private static @Nullable Located locate(NpcRef ref) {
        if (!ID.equals(ref.getProvider())) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(ref.getId());
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
        for (World world : Universe.get().getWorlds().values()) {
            EntityStore store = world.getEntityStore();
            if (store == null || !store.getStore().isInThread()) {
                continue;
            }
            Ref<EntityStore> found = store.getRefFromUUID(uuid);
            if (found != null && found.isValid() && found.getStore().getComponent(found, NPCEntity.getComponentType()) != null) {
                return new Located(world, found, uuid);
            }
        }
        return null;
    }

    private static final class Located {

        private final World world;
        private final Ref<EntityStore> ref;
        private final UUID uuid;

        private Located(World world, Ref<EntityStore> ref, UUID uuid) {
            this.world = world;
            this.ref = ref;
            this.uuid = uuid;
        }
    }
}
