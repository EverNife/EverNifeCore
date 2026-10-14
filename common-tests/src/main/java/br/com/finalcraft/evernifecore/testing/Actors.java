package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.platoverride.block.NativeBlock;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The actors a region test builds its contexts with - a player, a mob, a block, the environment - keyed the way the
 * Bukkit side keys them ({@code minecraft:<type>}). Their handles come from {@link Handles}, which records every one it
 * makes; the handles are the platform-free stubs, so this only runs headless.
 */
public final class Actors {

    private Actors() {
    }

    /** {@code player} acting as itself, with no handle. */
    public static Actor player(FPlayer player) {
        return Actor.player(player, null);
    }

    /** A machine posing as {@code player}, with no handle. */
    public static Actor fakePlayer(FPlayer player) {
        return Actor.entity(TypeKey.of("minecraft", "player"), ECEntityType.FAKE_PLAYER, player, null);
    }

    /** An entity of {@code key} ({@code minecraft:creeper}) with no player behind it and no handle. */
    public static Actor entity(String key, ECEntityType type) {
        return Actor.entity(TypeKey.parse(key), type, null, null);
    }

    public static Actor block(String key) {
        return Actor.block(TypeKey.parse(key), null);
    }

    /** {@code environment:<path>}. */
    public static Actor environment(String path) {
        return Actor.environment(TypeKey.of("environment", path));
    }

    /** Hands actors a handle factory and keeps every handle it made, in order. */
    public static final class Handles {

        private final List<NativeEntity> entities = new ArrayList<>();
        private final List<NativeBlock> blocks = new ArrayList<>();

        /** An entity actor whose handle this records when it is made. */
        public Actor entity(String key, ECEntityType type, @Nullable FPlayer rootPlayer) {
            return Actor.entity(TypeKey.parse(key), type, rootPlayer, () -> record(entities, new NativeEntity()));
        }

        public Actor block(String key) {
            return Actor.block(TypeKey.parse(key), () -> record(blocks, new NativeBlock()));
        }

        public List<NativeEntity> getEntitiesMade() {
            return Collections.unmodifiableList(entities);
        }

        public List<NativeBlock> getBlocksMade() {
            return Collections.unmodifiableList(blocks);
        }

        private static <H> H record(List<H> made, H handle) {
            made.add(handle);
            return handle;
        }
    }
}
