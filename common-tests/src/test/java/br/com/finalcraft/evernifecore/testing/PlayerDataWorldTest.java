package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same scenario - log in, dirty, flush, reboot, read back - has to hold on every backend the
 * lib offers, and the teardown has to leave the process as it found it.
 */
@ECoreTest
class PlayerDataWorldTest {

    public static class SampleSection extends PDSection {
        public long value;
    }

    @TempDir
    Path tempDir;

    @Test
    void aPlayerSurvivesARebootOnH2() {
        try (PlayerDataWorld world = PlayerDataWorld.with(Storages.h2("playerdataworld_h2").loadModeAll())
                .sections(SampleSection.class)
                .boot(tempDir)) {

            UUID uuid = UUID.randomUUID();
            PlayerData created = PlayerController.handleLogin(uuid, "Petrus").join();
            created.markDirty();
            PlayerController.get().flushAll().join();

            world.reboot();

            PlayerData reloaded = PlayerController.getLoaded(uuid);
            assertNotNull(reloaded, "the reboot has to find the player the first boot saved");
            assertEquals("Petrus", reloaded.getName());
        }
    }

    @Test
    void aPlayerSurvivesARebootOnLocalFile() {
        try (PlayerDataWorld world = PlayerDataWorld.with(Storages.localFile().loadModeAll())
                .boot(tempDir)) {

            UUID uuid = UUID.randomUUID();
            PlayerData created = PlayerController.handleLogin(uuid, "Nife").join();
            created.markDirty();
            PlayerController.get().flushAll().join();

            world.reboot();

            assertEquals("Nife", PlayerController.getLoaded(uuid).getName());
        }
    }

    @Test
    void closingLeavesNoConfiguredSectionBehind() {
        try (PlayerDataWorld world = PlayerDataWorld.with(Storages.memory())
                .sections(SampleSection.class)
                .boot(tempDir)) {

            assertTrue(PlayerController.getConfiguredPDSections().containsKey(SampleSection.class));
        }

        assertTrue(PlayerController.getConfiguredPDSections().isEmpty(),
                "a world that does not clear its sections hands them to the next test class");
    }

    @Test
    void theGeneratedYamlNamesTheBackendAndResolvesTheDataPath() {
        String yaml = Storages.groupedFile().toYaml(tempDir);

        assertTrue(yaml.contains("type: groupedfile"), yaml);
        assertTrue(yaml.contains("default-backend: grouped"), yaml);
        assertTrue(yaml.contains(tempDir.resolve("StorageData").toString().replace("\\", "/")), yaml);
        assertTrue(yaml.contains("collection: evernifecore_playerdata"), yaml);
    }
}
