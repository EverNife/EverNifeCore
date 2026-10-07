package br.com.finalcraft.evernifecore.spawn;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Spawn" is the spawn plugin's answer when one is registered, and the world's own spawn point otherwise. */
class SpawnPointsTest {

    private static final String FALLBACK_WARNING = "No spawn provider is registered";
    private static final WorldLocPos WORLD_SPAWN = WorldLocPos.of(7, 70, 7, "world");

    private static boolean warned(List<String> logged) {
        return logged.stream().anyMatch(line -> line.contains(FALLBACK_WARNING));
    }

    @Test
    void aRegisteredProviderAnswersPerPlayer() {
        WorldLocPos lobby = WorldLocPos.of(0, 64, 0, "lobby");
        WorldLocPos firstJoin = WorldLocPos.of(100, 64, 100, "lobby");
        TestFPlayerSender newcomer = new TestFPlayerSender("newcomer");
        ISpawnProvider provider = new ISpawnProvider() {
            @Override
            public WorldLocPos getSpawn() {
                return lobby;
            }

            @Override
            public WorldLocPos getSpawn(FPlayer player) {
                return player == newcomer ? firstJoin : lobby;
            }
        };

        try (ECoreTestWorld ignored = Platforms.lenient().worldSpawn("world", WORLD_SPAWN).install().withSpawn(provider)) {
            List<String> logged = Logs.capture(() -> {
                assertEquals(firstJoin, SpawnPoints.getServerSpawn(newcomer, "world"));
                assertEquals(lobby, SpawnPoints.getServerSpawn(new TestFPlayerSender("veteran"), "world"));
                assertEquals(lobby, SpawnPoints.getServerSpawn(null, "world"));
            });
            assertFalse(warned(logged), "a registered provider is no fallback");
        }
    }

    @Test
    void withoutAProviderItFallsBackToTheWorldSpawnWarningOnceInTwoCalls() {
        try (ECoreTestWorld ignored = Platforms.lenient().worldSpawn("world", WORLD_SPAWN).install()) {
            List<String> first = Logs.capture(() -> assertEquals(WORLD_SPAWN, SpawnPoints.getServerSpawn(null, "world")));
            List<String> second = Logs.capture(() -> assertEquals(WORLD_SPAWN, SpawnPoints.getServerSpawn(null, "world")));

            assertTrue(warned(first), "the first fallback says so: " + first);
            assertFalse(warned(second), "and only the first: " + second);
            assertNull(SpawnPoints.getServerSpawn(null, "nether"), "a world the platform does not know has no spawn");
        }
    }

    @Test
    void resetWarningReportsTheNextFallbackAgain() {
        try (ECoreTestWorld ignored = Platforms.lenient().worldSpawn("world", WORLD_SPAWN).install()) {
            assertTrue(warned(Logs.capture(() -> SpawnPoints.getServerSpawn(null, "world"))));

            SpawnPoints.resetWarning();

            assertTrue(warned(Logs.capture(() -> SpawnPoints.getServerSpawn(null, "world"))));
        }
    }
}
