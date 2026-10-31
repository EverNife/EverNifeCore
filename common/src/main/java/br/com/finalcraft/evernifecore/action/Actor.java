package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.platoverride.block.NativeBlock;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.version.FCPlatformType;
import jakarta.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Who acts, or who suffers, in an action a region is asked about: an entity, a block or the environment, with what it
 * is ({@link #getKey()}), the player behind it ({@link #getRootPlayer()}) and, for an NPC a provider recognised, which NPC
 * it is ({@link #getNpc()}).
 *
 * <p>The platform's own entity or block is reached through {@link #getNativeEntity()} / {@link #getNativeBlock()}:
 * the handle is made on the first call and kept; an actor whose handle nobody asks for never makes one. The handles
 * are closed when the dispatch of the action ends - read what you need during the event, keep the UUID, not the
 * handle.</p>
 */
public final class Actor {

    public enum Type {
        ENTITY, BLOCK, ENVIRONMENT;

        /** How settings write this type: {@code entity}, {@code block}, {@code environment}. */
        public String getWord() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final TypeKey PLAYER_KEY = TypeKey.of(FCPlatformType.getCurrent().name().toLowerCase(Locale.ROOT), "player");

    private final Type type;
    private final TypeKey key;
    private final @Nullable ECEntityType entityType;
    private final @Nullable FPlayer rootPlayer;
    private final @Nullable Supplier<NativeEntity> entityHandle;
    private final @Nullable Supplier<NativeBlock> blockHandle;
    private final @Nullable NpcRef npc;
    private @Nullable NativeEntity nativeEntity;
    private @Nullable NativeBlock nativeBlock;
    private boolean closed;

    private Actor(Type type, TypeKey key, @Nullable ECEntityType entityType, @Nullable FPlayer rootPlayer,
                  @Nullable Supplier<NativeEntity> entityHandle, @Nullable Supplier<NativeBlock> blockHandle, @Nullable NpcRef npc) {
        this.type = type;
        this.key = Objects.requireNonNull(key, "key");
        this.entityType = entityType;
        this.rootPlayer = rootPlayer;
        this.entityHandle = entityHandle;
        this.blockHandle = blockHandle;
        this.npc = npc;
    }

    /**
     * @param rootPlayer   the player behind the entity - itself for a player, the shooter of a projectile, the owner of a
     *                     pet; null for none
     * @param entityHandle makes the platform's handle of the entity, called at most once; null when there is none
     */
    public static Actor entity(TypeKey key, ECEntityType entityType, @Nullable FPlayer rootPlayer, @Nullable Supplier<NativeEntity> entityHandle) {
        return entity(key, entityType, rootPlayer, entityHandle, null);
    }

    /**
     * As {@link #entity(TypeKey, ECEntityType, FPlayer, Supplier)}, for an entity an {@code NpcProvider} recognised -
     * or a packet NPC with no server entity at all, whose {@code entityHandle} is null.
     *
     * @param npc the NPC this entity is; null for one no provider knows
     */
    public static Actor entity(TypeKey key, ECEntityType entityType, @Nullable FPlayer rootPlayer, @Nullable Supplier<NativeEntity> entityHandle,
                               @Nullable NpcRef npc) {
        return new Actor(Type.ENTITY, key, Objects.requireNonNull(entityType, "entityType"), rootPlayer, entityHandle, null, npc);
    }

    /**
     * A player acting as itself, keyed {@code <platform>:player} ({@code minecraft:player}, {@code hytale:player}).
     *
     * @param entityHandle makes the platform's handle of the player, called at most once; null when there is none
     */
    public static Actor player(FPlayer player, @Nullable Supplier<NativeEntity> entityHandle) {
        return entity(PLAYER_KEY, ECEntityType.PLAYER, Objects.requireNonNull(player, "player"), entityHandle);
    }

    /** @param blockHandle makes the platform's handle of the block, called at most once; null when there is none */
    public static Actor block(TypeKey key, @Nullable Supplier<NativeBlock> blockHandle) {
        return new Actor(Type.BLOCK, key, null, null, null, blockHandle, null);
    }

    /** Something no entity or block does: {@code environment:fire}, {@code environment:spawn}. */
    public static Actor environment(TypeKey key) {
        return new Actor(Type.ENVIRONMENT, key, null, null, null, null, null);
    }

    /** The key a player acting as itself carries: {@code minecraft:player} on Minecraft, {@code hytale:player} on Hytale. */
    public static TypeKey getPlayerKey() {
        return PLAYER_KEY;
    }

    public Type getType() {
        return type;
    }

    public TypeKey getKey() {
        return key;
    }

    /** What role the entity plays; null for a block or the environment. */
    public @Nullable ECEntityType getEntityType() {
        return entityType;
    }

    /** The NPC a provider recognised this entity as; null for a player, a block, the environment or an entity no provider knows. */
    public @Nullable NpcRef getNpc() {
        return npc;
    }

    /** The player behind this actor; null when there is none. */
    public @Nullable FPlayer getRootPlayer() {
        return rootPlayer;
    }

    /**
     * The platform's handle of this entity, made on the first call; null for an actor with none.
     *
     * @throws IllegalStateException after the dispatch of the action ended
     */
    public @Nullable NativeEntity getNativeEntity() {
        if (nativeEntity == null && entityHandle != null) {
            requireOpen("entity");
            nativeEntity = entityHandle.get();
        }
        return nativeEntity;
    }

    /**
     * The platform's handle of this block, made on the first call; null for an actor with none.
     *
     * @throws IllegalStateException after the dispatch of the action ended
     */
    public @Nullable NativeBlock getNativeBlock() {
        if (nativeBlock == null && blockHandle != null) {
            requireOpen("block");
            nativeBlock = blockHandle.get();
        }
        return nativeBlock;
    }

    private void requireOpen(String what) {
        if (closed) {
            throw new IllegalStateException("The native " + what + " of " + key + " was asked for after its action was decided: read it"
                    + " during the event, and keep the UUID or the position, not the handle.");
        }
    }

    /** Internal: the dispatch of the action ended - the handles made are closed and no new one is made. */
    public void close() {
        closed = true;
        if (nativeEntity != null) {
            nativeEntity.close();
        }
        if (nativeBlock != null) {
            nativeBlock.close();
        }
    }

    @Override
    public String toString() {
        if (type != Type.ENTITY) {
            return key + " (" + type.getWord() + ")";
        }
        return npc == null ? key + " (" + entityType.getWord() + ")" : key + " (" + entityType.getWord() + " " + npc + ")";
    }
}
