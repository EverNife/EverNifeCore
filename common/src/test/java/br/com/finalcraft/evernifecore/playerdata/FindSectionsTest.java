package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link PlayerController#findSections}: the stored rows of a known set of players, by key, without the cache. */
@ECoreTest
class FindSectionsTest {

    @TempDir
    Path tempDir;

    public static class KeyedSection extends PDSection {
        public int level;
    }

    private static void stored(UUID uuid, int level) {
        PlayerController.handleLogin(uuid, "p" + level).join();
        KeyedSection section = PlayerController.getPDSection(uuid, KeyedSection.class).join();
        section.level = level;
        section.forceSave().join();
    }

    @Test
    void theRowsOfTheseKeysComeBackInOneReadAndStayOutOfTheCache() {
        try (PlayerDataWorld world = PlayerDataWorld.with(Storages.groupedFile()).boot(tempDir)) {
            PlayerController.registerPDSectionCfg(PDSectionConfiguration.builder(null, KeyedSection.class, "keyedsection").build());
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            stored(first, 3);
            stored(second, 7);
            PlayerController.releasePDSection(KeyedSection.class).join();

            List<KeyedSection> rows = PlayerController.findSections(KeyedSection.class, Arrays.asList(first, second, UUID.randomUUID())).join();

            assertEquals(2, rows.size(), "a key with no stored row is absent");
            assertEquals(10, rows.stream().mapToInt(row -> row.level).sum());
            assertNull(PlayerController.getLoadedSection(first, KeyedSection.class), "the read never fills the cache");
            assertTrue(PlayerController.findSections(KeyedSection.class, Collections.<UUID>emptyList()).join().isEmpty());
        }
    }
}
