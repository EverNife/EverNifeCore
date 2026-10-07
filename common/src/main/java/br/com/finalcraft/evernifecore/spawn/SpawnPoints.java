package br.com.finalcraft.evernifecore.spawn;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Where "spawn" is: the {@link ISpawnProvider} a plugin registered, or the world's own spawn point.
 */
public final class SpawnPoints {

    //A server without a spawn plugin is normal, so the fallback is reported once per boot, at its first use
    private static final AtomicBoolean FALLBACK_REPORTED = new AtomicBoolean(false);

    private SpawnPoints() {
    }

    /**
     * The server spawn for {@code player}: the registered provider's answer, or - with no provider registered -
     * the spawn point of {@code fallbackWorld}. {@code null} when the provider has no spawn yet, or the
     * platform does not know that world.
     */
    public static @Nullable WorldLocPos getServerSpawn(@Nullable FPlayer player, String fallbackWorld) {
        ISpawnProvider provider = EverNifeCore.getProviders().getSpawnOrNull();
        if (provider != null) {
            return player == null ? provider.getSpawn() : provider.getSpawn(player);
        }
        if (FALLBACK_REPORTED.compareAndSet(false, true)) {
            EverNifeCore.getLog().warning("No spawn provider is registered - `spawn` falls back to the world spawn until a plugin registers ISpawnProvider");
        }
        return getWorldSpawn(fallbackWorld);
    }

    /** The spawn point of the world named {@code world}, or {@code null} when the platform does not know it. */
    public static @Nullable WorldLocPos getWorldSpawn(String world) {
        return EverNifeCore.getPlatform().getWorldSpawn(world);
    }

    /** Internal: makes the next fallback report again, as a new boot would. */
    public static void resetWarning() {
        FALLBACK_REPORTED.set(false);
    }
}
