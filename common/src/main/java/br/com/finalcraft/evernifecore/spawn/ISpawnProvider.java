package br.com.finalcraft.evernifecore.spawn;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

/**
 * The server spawn a spawn plugin defines - not a world's own spawn point, which the platform answers. The core
 * ships no implementation: the plugin that owns the spawn registers one with
 * {@code EverNifeCore.getProviders().getBaseProvider().register(ISpawnProvider.class, ...)}, and
 * {@link SpawnPoints} falls back to the world spawn while none is registered.
 */
public interface ISpawnProvider {

    /** The server spawn, or {@code null} while the plugin has not defined one yet. */
    @Nullable WorldLocPos getSpawn();

    /** Where {@code player} spawns - override when it depends on who asks (a first-join spawn, a team spawn). */
    default @Nullable WorldLocPos getSpawn(FPlayer player) {
        return getSpawn();
    }
}
