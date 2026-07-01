package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serialization edge cases: a section carrying {@code java.time} and {@code Optional} fields
 * survives a real backend round-trip (guards the jsr310/jdk8 datatype modules the EveryDatabase
 * codec registers - a serialization failure would surface here).
 * Runs on LocalFile (yaml) and H2 mem - no Docker or external services.
 */
class PlayerControllerG1CoverageTest {

    @BeforeAll
    static void installTestPlatform() {
        TestPlatformFixture.ensureInstalled();
    }

    @TempDir
    Path tempDir;

    @AfterEach
    void teardown() {
        PlayerController.shutdown();
        PlayerController.getConfiguredPDSections().clear();
    }

    /** A section whose persisted state includes a {@code java.time.Instant}, a {@code LocalDateTime} and an {@code Optional<String>}. */
    public static class TemporalPDSection extends PDSection {
        public Instant lastReward = Instant.EPOCH;
        public LocalDateTime joinedAt;
        public Optional<String> nickname = Optional.empty();
    }

    private File writeLocalFileStorageYml() throws IOException {
        String dataPath = tempDir.resolve("storagedata").toString().replace("\\", "/");
        String yml = String.join("\n",
                "storage-backends:",
                "  test_files:",
                "    enabled: true",
                "    type: localfile",
                "    path: \"" + dataPath + "\"",
                "    format: yaml",
                "default-backend: test_files",
                "");
        File file = tempDir.resolve("storage_localfile.yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File writeH2StorageYml(String dbName) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "");
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ------------------------------------------------------------------
    // java.time / Optional round-trip on a real backend (jsr310 + jdk8 modules)
    // ------------------------------------------------------------------

    @Test
    void javaTimeAndOptionalFieldsSurviveRoundTrip_onH2() throws IOException {
        File storageYml = writeH2StorageYml("g1_temporal");
        PlayerController.bootstrap(storageYml);
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, TemporalPDSection.class).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Chronos").join();

        Instant rewardAt = Instant.ofEpochMilli(1_700_000_123_456L);
        LocalDateTime joined = LocalDateTime.of(2023, 11, 14, 8, 55, 23, 456_000_000);

        TemporalPDSection section = PlayerController.getLoaded(uuid).getPDSection(TemporalPDSection.class).join();
        section.lastReward = rewardAt;
        section.joinedAt = joined;
        section.nickname = Optional.of("The Timekeeper");
        section.markDirty();
        PlayerController.get().flushAll().join();

        //reboot: the section is re-read from the backend through the codec
        PlayerController.bootstrap(storageYml);

        TemporalPDSection reloaded = PlayerController.getLoaded(uuid).getPDSection(TemporalPDSection.class).join();
        assertEquals(rewardAt, reloaded.lastReward, "java.time.Instant must survive the codec round-trip");
        assertEquals(joined, reloaded.joinedAt, "LocalDateTime must survive the codec round-trip");
        assertTrue(reloaded.nickname.isPresent(), "a present Optional must survive the codec round-trip");
        assertEquals("The Timekeeper", reloaded.nickname.get());
    }

    @Test
    void absentOptionalAndDefaultInstantSurviveRoundTrip_onLocalFileYaml() throws IOException {
        File storageYml = writeLocalFileStorageYml();
        PlayerController.bootstrap(storageYml);
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(null, TemporalPDSection.class).build());

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Blank").join();

        //leave nickname absent and joinedAt null, mutate only the Instant so a row is written
        TemporalPDSection section = PlayerController.getLoaded(uuid).getPDSection(TemporalPDSection.class).join();
        section.lastReward = Instant.ofEpochSecond(42);
        section.markDirty();
        PlayerController.get().flushAll().join();

        PlayerController.bootstrap(storageYml);

        TemporalPDSection reloaded = PlayerController.getLoaded(uuid).getPDSection(TemporalPDSection.class).join();
        assertEquals(Instant.ofEpochSecond(42), reloaded.lastReward);
        assertFalse(reloaded.nickname.isPresent(), "an absent Optional must round-trip as Optional.empty(), not null");
    }

}
