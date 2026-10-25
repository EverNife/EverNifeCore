package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.playerdata.storage.SectionLifecycle;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code getAllSections} answers "everybody" only where the cache holds everybody. */
@ECoreTest
class PlayerControllerAllSectionsTest {

    @TempDir
    Path tempDir;

    private PlayerDataWorld world;

    public static class ScoreSection extends PDSection {
        public int score;
    }

    @AfterEach
    void close() {
        world.close();
    }

    private void boot(SectionLifecycle lifecycle) {
        world = PlayerDataWorld.with(Storages.memory())
                .section(PDSectionConfiguration.builder(null, ScoreSection.class, "scoresection").lifecycle(lifecycle).build())
                .boot(tempDir);
    }

    private static ScoreSection login(UUID uuid, String name) throws Exception {
        PlayerController.handleLogin(uuid, name).get(5, TimeUnit.SECONDS);
        return PlayerController.getLoaded(uuid).getPDSection(ScoreSection.class).get(5, TimeUnit.SECONDS);
    }

    @Test
    void aPRELOADEDSectionAnswersEveryStoredRowAndNotTheTransientDefaults() throws Exception {
        boot(SectionLifecycle.PRELOADED);
        ScoreSection scored = login(UUID.randomUUID(), "Scorer");
        scored.score = 10;
        scored.markDirty();
        world.flushAll();
        login(UUID.randomUUID(), "Visitor"); //seeded at login, never dirtied: no row exists for it

        Collection<ScoreSection> all = PlayerController.getAllSections(ScoreSection.class);

        assertEquals(1, all.size(), "the visitor's default is not a stored row: " + all);
        assertSame(scored, all.iterator().next(), "the live instance, not a copy");
    }

    @Test
    void anONLINESectionRefusesAndNamesBothWaysOut() {
        boot(SectionLifecycle.ONLINE);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> PlayerController.getAllSections(ScoreSection.class));

        assertTrue(refused.getMessage().contains("ONLINE"), refused.getMessage());
        assertTrue(refused.getMessage().contains("PRELOADED"), refused.getMessage());
        assertTrue(refused.getMessage().contains("querySections"), refused.getMessage());
    }
}
