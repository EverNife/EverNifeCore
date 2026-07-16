package br.com.finalcraft.evernifecore.playerdata.storage.legacy;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.playerdata.storage.legacy.LegacyMigrationMetadata.SectionProgress;
import br.com.finalcraft.evernifecore.playerdata.storage.legacy.LegacyMigrationMetadata.SectionStatus;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Legacy import acceptance tests: given a folder with YAML files in the v3 layout, a boot imports the
 * base PlayerData and, through a legacyYaml adapter, every section someone claims. A file is archived
 * only once ALL of it migrated - so what stays in the folder is the pending list, and a re-run never
 * duplicates what already reached the backend.
 *
 * <p>The test platform fixture runs {@code runOnFirstTick} inline, so the whole
 * import + load pipeline completes within {@code PlayerController.bootstrap}.</p>
 */
class LegacyImportTest {

    private static final UUID PETRUS_UUID = UUID.fromString("068117bc-0000-4000-8000-000000000001");
    private static final UUID SIMPLE_UUID = UUID.fromString("068117bc-0000-4000-8000-000000000002");
    private static final String FAKE_PLUGIN_NAME = "LegacyTestPlugin";

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
        //the ECPluginData cache is static and keyed by name: dropping it keeps a stale one, pointing
        //at a @TempDir that no longer exists, from reaching the next test in this JVM
        ECPluginManager.removePluginData(FAKE_PLUGIN_NAME);
    }

    public static class LegacyJobsPDSection extends PDSection {
        public int level;
        public String job = "none";
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private File writeStorageYml(String fileName, String extraBlock) throws IOException {
        String dataPath = tempDir.resolve("storagedata").toString().replace("\\", "/");
        String yml = String.join("\n",
                "storage-backends:",
                "  test_files:",
                "    enabled: true",
                "    type: localfile",
                "    path: \"" + dataPath + "\"",
                "    format: yaml",
                "default-backend: test_files",
                extraBlock,
                "");
        File file = tempDir.resolve(fileName).toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File legacyFolder() {
        return tempDir.resolve("PlayerData").toFile();
    }

    private File metadataFile() {
        return tempDir.resolve("playerdata-storage-migration-metadata.yml").toFile();
    }

    /** Hand-writes a progress file claiming the migration is over (the state a finished run leaves). */
    private File writeCompleteMetadata() throws IOException {
        File file = metadataFile();
        Files.write(file.toPath(), "complete: true\n".getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File writeLegacyYml(String fileName, String content) throws IOException {
        File folder = legacyFolder();
        folder.mkdirs();
        File file = new File(folder, fileName);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String petrusV3Yaml() {
        return String.join("\n",
                "PlayerData:",
                "  Username: Petrus",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1600000000000",
                "  lastSeen: 1700000000000",
                "  lastSaved: 1700000000001",
                "Cooldown:",
                "  test_kit:",
                "    identifier: test_kit",
                "    timeStart: " + System.currentTimeMillis(),
                "    timeDuration: 99999999",
                "FinalJobs:",
                "  level: 42",
                "  job: miner",
                "OrphanPluginSection:",
                "  some: data",
                "");
    }

    /** Same layout as {@link #petrusV3Yaml()} minus the orphan blocks: every root key here has an owner. */
    private String fullyMappedV3Yaml(String username, int level, String job) {
        return fullyMappedV3Yaml(PETRUS_UUID, username, level, job);
    }

    /** As {@link #fullyMappedV3Yaml(String, int, String)}, for a batch that needs a second player. */
    private String fullyMappedV3Yaml(UUID uuid, String username, int level, String job) {
        return String.join("\n",
                "PlayerData:",
                "  Username: " + username,
                "  UUID: " + uuid,
                "  firstSeen: 1600000000000",
                "  lastSeen: 1700000000000",
                "  lastSaved: 1700000000001",
                "FinalJobs:",
                "  level: " + level,
                "  job: " + job,
                "");
    }

    private void registerJobsSectionWithAdapter() {
        registerJobsSectionOwnedBy(null);
    }

    private void registerJobsSectionOwnedBy(ECPluginData owner) {
        PlayerController.registerPDSectionCfg(
                PDSectionConfiguration.builder(owner, LegacyJobsPDSection.class)
                        .legacyYaml("FinalJobs", section -> {
                            LegacyJobsPDSection jobs = new LegacyJobsPDSection();
                            jobs.level = section.getInt("level");
                            jobs.job = section.getString("job");
                            return jobs; //'xp' and any other field is deliberately dropped
                        })
                        .build());
    }

    /**
     * A real {@link ECPluginData}, the way production builds one: through the plugin extractor the
     * platform registers. Sections in production always carry one, and it is what names the owner in
     * the progress file - {@code builder(null, ...)} silently reports no owner at all.
     */
    private ECPluginData realPluginData() {
        Object plugin = new FakePlugin();
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                new FakePluginExtractor(tempDir.resolve(FAKE_PLUGIN_NAME).toFile()));
        return ECPluginManager.getOrCreateECorePluginData(plugin);
    }

    /** Stands in for the platform's plugin object (a JavaPlugin on Bukkit); only its identity matters. */
    public static final class FakePlugin {
    }

    private static final class FakePluginExtractor implements IECPluginExtractor {
        private final File dataFolder;

        FakePluginExtractor(File dataFolder) {
            this.dataFolder = dataFolder;
        }

        @Override
        public String getPluginName(Object javaPlugin) {
            return FAKE_PLUGIN_NAME;
        }

        @Override
        public boolean isJavaPlugin(Object plugin) {
            return plugin instanceof FakePlugin;
        }

        @Override
        public Object getProvidingPlugin(Class<?> clazz) {
            return null;
        }

        @Override
        public IPluginMetaInfo getPluginMetaInfo(Object javaPlugin) {
            return new FakeMetaInfo(javaPlugin, dataFolder);
        }
    }

    private static final class FakeMetaInfo implements IPluginMetaInfo {
        private final Object plugin;
        private final File dataFolder;

        FakeMetaInfo(Object plugin, File dataFolder) {
            this.plugin = plugin;
            this.dataFolder = dataFolder;
        }

        @Override
        public String getName() {
            return FAKE_PLUGIN_NAME;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public String getAuthor() {
            return "Petrus";
        }

        @Override
        public String getGroup() {
            return "br.com.finalcraft";
        }

        @Override
        public File getDataFolder() {
            return dataFolder;
        }

        @Override
        public Object getDelegate() {
            return plugin;
        }
    }

    private static File[] ymlFiles(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        return files != null ? files : new File[0];
    }

    /** The report the importer hands the console, filled from the progress file a real run left behind. */
    private LegacyImportReport reportOfThePersistedRun() {
        LegacyMigrationMetadata metadata = LegacyMigrationMetadata.load(metadataFile());
        LegacyImportReport report = new LegacyImportReport();
        report.setPaths(legacyFolder(), tempDir.resolve("PlayerData-Imported").toFile(),
                tempDir.resolve("PlayerData-Failed").toFile(), metadataFile());
        report.setFiles(metadata.getFilesTotalFound(), metadata.getFilesFullyImported(),
                metadata.getFilesPending(), metadata.getFilesFailed());
        report.setSections(metadata.getSections());
        report.setCompletion(false, metadata.isComplete());
        return report;
    }

    /**
     * The one line of the per-root-key table that belongs to {@code rootKey}. Matching the whole line
     * keeps the assertions blind to the column padding, which is free to change.
     */
    private static String sectionLine(String text, String rootKey) {
        for (String line : text.split("\n")) {
            if (line.trim().startsWith(rootKey + " ")) {
                return line;
            }
        }
        return fail("no table line for the root key '" + rootKey + "' in:\n" + text);
    }

    // ------------------------------------------------------------------
    // base converter (unit)
    // ------------------------------------------------------------------

    @Test
    void convertBaseParsesTheV3LayoutAndDefaults() throws IOException {
        File file = writeLegacyYml("petrus.yml", String.join("\n",
                "PlayerData:",
                "  Username: Petrus",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1600000000000",
                "  lastSeen: 1700000000000",
                "Cooldown:",
                "  no_identifier_block:",
                "    timeStart: 123456",
                "    timeDuration: 654321",
                ""));

        PlayerData converted = LegacyPlayerDataYamlConverter.convertBase(ConfigFactory.open(file));

        assertEquals(PETRUS_UUID, converted.getUniqueId());
        assertEquals("Petrus", converted.getName());
        assertEquals(1600000000000L, converted.getFirstSeen());
        assertEquals(1700000000000L, converted.getLastSeen());
        assertEquals(1700000000000L, converted.getLastSaved(), "missing lastSaved must default to lastSeen");
        //the legacy Cooldown: block is deliberately NOT imported (the player-cooldown flow
        //was removed from PlayerData); it stays intact in the archived YAML file
    }

    // ------------------------------------------------------------------
    // first boot: base + section via adapter, archived
    // ------------------------------------------------------------------

    @Test
    void firstBootImportsBaseAndAdapterSection() throws IOException {
        //every root key of this file has an adapter, which is what lets it be archived at all
        writeLegacyYml("petrus.yml", fullyMappedV3Yaml("Petrus", 42, "miner"));
        writeLegacyYml("simple.yml", String.join("\n",
                "PlayerData:",
                "  Username: Simple",
                "  UUID: " + SIMPLE_UUID,
                "  firstSeen: 1500000000000",
                "  lastSeen: 1500000000001",
                "  lastSaved: 1500000000002",
                ""));
        registerJobsSectionWithAdapter();

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        //base entity came from the backend (start() loads what the import saved)
        PlayerData petrus = PlayerController.getLoaded(PETRUS_UUID);
        assertNotNull(petrus, "the imported player must be loaded after the first-tick pipeline");
        assertEquals("Petrus", petrus.getName());
        assertEquals(1600000000000L, petrus.getFirstSeen());
        assertEquals(1700000000000L, petrus.getLastSeen());

        assertNotNull(PlayerController.getLoaded(SIMPLE_UUID), "base-only files must import too");

        //section routed through the adapter
        LegacyJobsPDSection jobs = petrus.getPDSection(LegacyJobsPDSection.class).join();
        assertEquals(42, jobs.level);
        assertEquals("miner", jobs.job);

        //the section of a player without that root key gets a default instance
        LegacyJobsPDSection simpleJobs = PlayerController.getLoaded(SIMPLE_UUID)
                .getPDSection(LegacyJobsPDSection.class).join();
        assertEquals(0, simpleJobs.level);

        //archiving: nothing was left pending, so both files are moved to -Imported
        assertEquals(0, ymlFiles(legacyFolder()).length, "the PlayerData folder must be drained");
        File importedFolder = tempDir.resolve("PlayerData-Imported").toFile();
        assertEquals(2, ymlFiles(importedFolder).length);
        assertFalse(tempDir.resolve("PlayerData-Failed").toFile().exists(), "no failures expected");

        //an archived file is moved, never rewritten: its blocks are still there afterwards
        Config archived = ConfigFactory.open(new File(importedFolder, "petrus.yml"));
        assertTrue(archived.contains("FinalJobs"), "files are moved, never rewritten");
        assertEquals("miner", archived.getString("FinalJobs.job"));

        //logins released (the ready gate has completed): a brand-new login works immediately
        PlayerData fresh = PlayerController.handleLogin(UUID.randomUUID(), "Newcomer").join();
        assertNotNull(fresh);
    }

    // ------------------------------------------------------------------
    // broken files are archived as failures, the rest still imports
    // ------------------------------------------------------------------

    @Test
    void brokenFileGoesToFailedFolderAndOthersStillImport() throws IOException {
        writeLegacyYml("broken.yml", "JustSomeGarbage:\n  no: playerdata\n");
        writeLegacyYml("petrus.yml", fullyMappedV3Yaml("Petrus", 42, "miner"));
        registerJobsSectionWithAdapter();

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        assertNotNull(PlayerController.getLoaded(PETRUS_UUID), "the healthy file must import");
        assertEquals(42, PlayerController.getLoaded(PETRUS_UUID).getPDSection(LegacyJobsPDSection.class).join().level);
        assertEquals(1, PlayerController.getLoadedCount());

        //the healthy file completed, so it is archived; the broken one holds nobody back
        File[] archived = ymlFiles(tempDir.resolve("PlayerData-Imported").toFile());
        assertEquals(1, archived.length);
        assertEquals("petrus.yml", archived[0].getName());

        //the failure is copied for diagnosis, and the original stays behind as the pending item
        File[] remaining = ymlFiles(legacyFolder());
        assertEquals(1, remaining.length);
        assertEquals("broken.yml", remaining[0].getName());
        File[] failed = ymlFiles(tempDir.resolve("PlayerData-Failed").toFile());
        assertEquals(1, failed.length);
        assertEquals("broken.yml", failed[0].getName());
    }

    // ------------------------------------------------------------------
    // Idempotency: a re-run (migrate-legacy: force) never duplicates or overwrites
    // ------------------------------------------------------------------

    @Test
    void forcedReRunSkipsEntitiesAlreadyOnTheBackend() throws IOException {
        writeLegacyYml("petrus.yml", fullyMappedV3Yaml("Petrus", 42, "miner"));
        registerJobsSectionWithAdapter();
        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));
        assertEquals("Petrus", PlayerController.getLoaded(PETRUS_UUID).getName());

        //the same UUID comes back with DIFFERENT data - a skipped import must not apply it
        //(every field differs from the backend's, so an overwrite could not hide anywhere)
        writeLegacyYml("petrus.yml", String.join("\n",
                "PlayerData:",
                "  Username: Imposter",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1",
                "  lastSeen: 2",
                "  lastSaved: 3",
                "FinalJobs:",
                "  level: 99",
                "  job: hacker",
                ""));

        PlayerController.bootstrap(writeStorageYml("storage_force.yml",
                "playerdata:\n  migrate-legacy: force"));

        //the backend data is intact (skip by UUID), nothing duplicated
        PlayerData petrus = PlayerController.getLoaded(PETRUS_UUID);
        assertEquals("Petrus", petrus.getName(), "a forced re-run must never overwrite newer backend data");
        assertEquals(1600000000000L, petrus.getFirstSeen());
        assertEquals(42, petrus.getPDSection(LegacyJobsPDSection.class).join().level);
        assertEquals(1, PlayerController.getLoadedCount());

        //the re-imported file is still archived (with an anti-collision suffix), the folder is drained again
        assertEquals(0, ymlFiles(legacyFolder()).length);
        assertEquals(2, ymlFiles(tempDir.resolve("PlayerData-Imported").toFile()).length);
    }

    // ------------------------------------------------------------------
    // Trigger: a finished migration never runs again
    // ------------------------------------------------------------------

    @Test
    void noImportWhenTheMigrationAlreadyCompleted() throws IOException {
        File storageYml = writeStorageYml("storage.yml", "");
        PlayerController.bootstrap(storageYml);
        PlayerController.handleLogin(UUID.randomUUID(), "Resident").join();
        PlayerController.get().flushAll().join();

        writeLegacyYml("late.yml", String.join("\n",
                "PlayerData:",
                "  Username: TooLate",
                "  UUID: " + SIMPLE_UUID,
                "  firstSeen: 1",
                "  lastSeen: 2",
                ""));
        writeCompleteMetadata();

        PlayerController.bootstrap(storageYml); //the progress file says there is nothing left to do

        assertNull(PlayerController.getLoaded(SIMPLE_UUID), "a finished migration must not import a late file");
        assertEquals(1, ymlFiles(legacyFolder()).length, "the file must stay in place");
        assertFalse(tempDir.resolve("PlayerData-Imported").toFile().exists());
    }

    // ------------------------------------------------------------------
    // Trigger: the progress file decides it, the backend is never consulted
    // ------------------------------------------------------------------

    @Test
    void completeMetadataSkipsTheImportWithoutConsultingTheBackend() throws IOException {
        writeLegacyYml("petrus.yml", petrusV3Yaml());
        registerJobsSectionWithAdapter();
        writeCompleteMetadata();

        //the backend is EMPTY: a trigger that counted rows would find 0 and import (that was the old
        //guard). Skipping the import anyway is what proves count()/exists() are never called.
        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        assertNull(PlayerController.getLoaded(PETRUS_UUID), "a complete progress file must skip the import");
        assertEquals(0, PlayerController.getLoadedCount());
        assertEquals(1, ymlFiles(legacyFolder()).length, "the file must stay in place");
        assertFalse(tempDir.resolve("PlayerData-Imported").toFile().exists());
    }

    // ------------------------------------------------------------------
    // Trigger: 'force' overrides a complete progress file
    // ------------------------------------------------------------------

    @Test
    void forceImportsDespiteACompleteMetadata() throws IOException {
        writeLegacyYml("petrus.yml", fullyMappedV3Yaml("Petrus", 42, "miner"));
        registerJobsSectionWithAdapter();
        writeCompleteMetadata();

        PlayerController.bootstrap(writeStorageYml("storage_force.yml",
                "playerdata:\n  migrate-legacy: force"));

        PlayerData petrus = PlayerController.getLoaded(PETRUS_UUID);
        assertNotNull(petrus, "force must ignore the progress file and import anyway");
        assertEquals(42, petrus.getPDSection(LegacyJobsPDSection.class).join().level);
        assertEquals(0, ymlFiles(legacyFolder()).length, "the folder must be drained");
        assertEquals(1, ymlFiles(tempDir.resolve("PlayerData-Imported").toFile()).length);
    }

    // ------------------------------------------------------------------
    // The progress file is rebuildable: deleting it re-scans, and the re-scan imports nothing new
    // ------------------------------------------------------------------

    @Test
    void deletingTheMetadataRebuildsItWithoutReimporting() throws IOException {
        writeLegacyYml("petrus.yml", fullyMappedV3Yaml("Petrus", 42, "miner"));
        registerJobsSectionWithAdapter();
        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));
        assertEquals("Petrus", PlayerController.getLoaded(PETRUS_UUID).getName());

        //the progress file lands next to storage.yml - never inside a folder the import drains
        File metadata = metadataFile();
        assertTrue(metadata.isFile(), "the import must leave a progress file next to storage.yml");
        assertEquals(tempDir.toFile(), metadata.getParentFile());
        assertTrue(ConfigFactory.open(metadata).getBoolean("complete"),
                "every root key had an adapter, so the migration is complete");

        //an admin deleting the file asks for a full re-scan - over a backend that is already populated,
        //and with the same UUID carrying DIFFERENT data, which a skipped import must not apply
        assertTrue(metadata.delete());
        writeLegacyYml("petrus.yml", fullyMappedV3Yaml("Imposter", 99, "hacker"));

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        PlayerData petrus = PlayerController.getLoaded(PETRUS_UUID);
        assertEquals("Petrus", petrus.getName(), "the re-scan must import 0 entities over a populated backend");
        assertEquals(42, petrus.getPDSection(LegacyJobsPDSection.class).join().level);
        assertEquals(1, PlayerController.getLoadedCount(), "nothing may be duplicated");

        assertEquals(0, ymlFiles(legacyFolder()).length, "the re-scanned file is archived again");
        assertEquals(2, ymlFiles(tempDir.resolve("PlayerData-Imported").toFile()).length);
        assertTrue(ConfigFactory.open(metadataFile()).getBoolean("complete"),
                "the progress file must be rebuilt as complete");
    }

    // ------------------------------------------------------------------
    // Completion: only a file whose every root key migrated may leave the folder
    // ------------------------------------------------------------------

    @Test
    void aRootKeyWithoutAnAdapterKeepsItsFileInTheLegacyFolder() throws IOException {
        writeLegacyYml("petrus.yml", petrusV3Yaml()); //no adapter registered at all

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        //the base entity did reach the backend - it is the FILE that is pending, not its data
        assertNotNull(PlayerController.getLoaded(PETRUS_UUID));

        File[] remaining = ymlFiles(legacyFolder());
        assertEquals(1, remaining.length, "a root key nobody claims must keep its file in PlayerData/");
        assertEquals("petrus.yml", remaining[0].getName());
        assertFalse(tempDir.resolve("PlayerData-Imported").toFile().exists(),
                "a pending file must never be archived as imported");

        //nothing is stripped from a file left behind: it is the intact input of a later boot
        Config kept = ConfigFactory.open(remaining[0]);
        assertTrue(kept.contains("OrphanPluginSection"), "an unmapped section must stay in the file");
        assertTrue(kept.contains("FinalJobs"));

        Config progress = ConfigFactory.open(metadataFile());
        assertFalse(progress.getBoolean("complete"), "a pending root key must keep the migration open");
        assertEquals("PENDING_NO_ADAPTER", progress.getString("sections.OrphanPluginSection.status"));
        assertEquals(1, progress.getInt("files.pending"));
        assertEquals(0, progress.getInt("files.fully-imported"));
    }

    @Test
    void aPluginInstalledOnALaterBootStillMigratesItsSection() throws IOException {
        writeLegacyYml("petrus.yml", fullyMappedV3Yaml("Petrus", 42, "miner"));
        File storageYml = writeStorageYml("storage.yml", "");

        //boot 1: the plugin owning FinalJobs is not installed yet, so the file cannot complete
        PlayerController.bootstrap(storageYml);
        assertEquals(1, ymlFiles(legacyFolder()).length, "the file must wait for the missing adapter");
        assertFalse(ConfigFactory.open(metadataFile()).getBoolean("complete"));

        //boot 2: the plugin is installed - no 'force', no manual step, no re-copying anything
        registerJobsSectionWithAdapter();
        PlayerController.bootstrap(storageYml);

        PlayerData petrus = PlayerController.getLoaded(PETRUS_UUID);
        assertEquals(42, petrus.getPDSection(LegacyJobsPDSection.class).join().level,
                "the section must migrate on the boot that finally brings its adapter");
        assertEquals("miner", petrus.getPDSection(LegacyJobsPDSection.class).join().job);
        assertEquals(0, ymlFiles(legacyFolder()).length, "and only NOW may the file be archived");
        assertEquals(1, ymlFiles(tempDir.resolve("PlayerData-Imported").toFile()).length);
        assertTrue(ConfigFactory.open(metadataFile()).getBoolean("complete"));
    }

    @Test
    void onlyTheFilesThatCompletedAreArchived() throws IOException {
        writeLegacyYml("petrus.yml", petrusV3Yaml());                                    //Cooldown + orphan
        writeLegacyYml("simple.yml", fullyMappedV3Yaml(SIMPLE_UUID, "Simple", 7, "farmer"));
        registerJobsSectionWithAdapter();

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        File[] remaining = ymlFiles(legacyFolder());
        assertEquals(1, remaining.length);
        assertEquals("petrus.yml", remaining[0].getName(), "only the file with an orphan key stays");
        File[] archived = ymlFiles(tempDir.resolve("PlayerData-Imported").toFile());
        assertEquals(1, archived.length);
        assertEquals("simple.yml", archived[0].getName(), "a fully mapped file is not held back by a pending one");

        //both entities reached the backend regardless: completion is about the FILE
        assertEquals(2, PlayerController.getLoadedCount());

        Config progress = ConfigFactory.open(metadataFile());
        assertEquals(1, progress.getInt("files.fully-imported"));
        assertEquals(1, progress.getInt("files.pending"));
        assertEquals(0, progress.getInt("files.failed"));
        assertFalse(progress.getBoolean("complete"));
    }

    @Test
    void theCooldownBlockBlocksLikeAnyOtherUnclaimedRootKey() throws IOException {
        //Cooldown used to be hardcoded as 'mapped' while nothing read it, so it went silently missing.
        //Here it is the ONLY unclaimed key: every other one has an adapter, and it still must block.
        writeLegacyYml("petrus.yml", String.join("\n",
                "PlayerData:",
                "  Username: Petrus",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1600000000000",
                "  lastSeen: 1700000000000",
                "  lastSaved: 1700000000001",
                "Cooldown:",
                "  test_kit:",
                "    identifier: test_kit",
                "    timeStart: 1600000000000",
                "    timeDuration: 99999999",
                "FinalJobs:",
                "  level: 42",
                "  job: miner",
                ""));
        registerJobsSectionWithAdapter();

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        assertEquals(1, ymlFiles(legacyFolder()).length,
                "an unread Cooldown block must keep its file, not vanish with it");
        assertFalse(tempDir.resolve("PlayerData-Imported").toFile().exists());

        Config progress = ConfigFactory.open(metadataFile());
        assertEquals("PENDING_NO_ADAPTER", progress.getString("sections.Cooldown.status"),
                "Cooldown must be visible as pending, like any other key nobody claims");
        assertEquals("DONE", progress.getString("sections.FinalJobs.status"));
        assertFalse(progress.getBoolean("complete"));
    }

    @Test
    void anEmptyRootKeyNeedsNoAdapterAndBlocksNothing() throws IOException {
        writeLegacyYml("petrus.yml", String.join("\n",
                "PlayerData:",
                "  Username: Petrus",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1600000000000",
                "  lastSeen: 1700000000000",
                "  lastSaved: 1700000000001",
                "FinalRTP: {}",                //nobody ever registers an adapter for this one
                ""));

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        assertEquals(0, ymlFiles(legacyFolder()).length, "an empty block has nothing to migrate");
        assertEquals(1, ymlFiles(tempDir.resolve("PlayerData-Imported").toFile()).length);

        Config progress = ConfigFactory.open(metadataFile());
        assertEquals("EMPTY", progress.getString("sections.FinalRTP.status"),
                "an empty root key is reported, but as EMPTY - never as a pending adapter");
        assertTrue(progress.getBoolean("complete"));
    }

    @Test
    void anUnknownFieldInsideAMappedRootKeyIsNeitherTrackedNorBlocking() throws IOException {
        writeLegacyYml("petrus.yml", String.join("\n",
                "PlayerData:",
                "  Username: Petrus",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1600000000000",
                "  lastSeen: 1700000000000",
                "  lastSaved: 1700000000001",
                "  pKills: 13",              //a base field the new entity does not carry any more
                "FinalJobs:",
                "  level: 42",
                "  job: miner",
                "  xp: 999",                 //present in the YAML, deliberately dropped by the adapter
                ""));
        registerJobsSectionWithAdapter();

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        assertEquals(42, PlayerController.getLoaded(PETRUS_UUID).getPDSection(LegacyJobsPDSection.class).join().level);
        assertEquals(0, ymlFiles(legacyFolder()).length, "a field nobody reads must not hold a file back");
        assertEquals(1, ymlFiles(tempDir.resolve("PlayerData-Imported").toFile()).length);

        //the contract is the root key, never the field: what an adapter drops is its author's call,
        //so no field name may show up anywhere in the progress file
        String progress = new String(Files.readAllBytes(metadataFile().toPath()), StandardCharsets.UTF_8);
        assertFalse(progress.contains("pKills"), "base fields must never be tracked");
        assertFalse(progress.contains("xp"), "fields inside a mapped root key must never be tracked");
        assertTrue(progress.contains("FinalJobs"), "root keys, on the other hand, ARE tracked");
    }

    @Test
    void aFailedFileIsCopiedForDiagnosisAndKeptAsPending() throws IOException {
        writeLegacyYml("broken.yml", "JustSomeGarbage:\n  no: playerdata\n");

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        //both exist at once: the copy is for the admin to read, the original is the pending item
        File[] remaining = ymlFiles(legacyFolder());
        assertEquals(1, remaining.length);
        assertEquals("broken.yml", remaining[0].getName(), "a failure is pending, so its file stays");
        File[] failed = ymlFiles(tempDir.resolve("PlayerData-Failed").toFile());
        assertEquals(1, failed.length);
        assertEquals("broken.yml", failed[0].getName());
        assertFalse(tempDir.resolve("PlayerData-Imported").toFile().exists(),
                "a failure must never be archived as imported");

        Config progress = ConfigFactory.open(metadataFile());
        assertEquals("FAILED", progress.getString("sections.JustSomeGarbage.status"),
                "the root keys of a broken file must be reported as FAILED, not as merely unclaimed");
        assertEquals(1, progress.getInt("files.failed"));
        assertFalse(progress.getBoolean("complete"),
                "a file left behind by a failure keeps the migration open just like any other");
    }

    @Test
    void theProgressFileNamesThePluginOwningEachSection() throws IOException {
        writeLegacyYml("petrus.yml", fullyMappedV3Yaml("Petrus", 42, "miner"));
        ECPluginData owner = realPluginData();
        registerJobsSectionOwnedBy(owner);

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        assertEquals(FAKE_PLUGIN_NAME, owner.getMetaInfo().getName(), "a real ECPluginData, not a null stand-in");
        assertEquals(42, PlayerController.getLoaded(PETRUS_UUID).getPDSection(LegacyJobsPDSection.class).join().level);

        Config progress = ConfigFactory.open(metadataFile());
        assertEquals(FAKE_PLUGIN_NAME, progress.getString("sections.FinalJobs.owner"),
                "the progress file must name the plugin an admin has to install for a pending key");
        assertEquals("LegacyJobsPDSection", progress.getString("sections.FinalJobs.pdsection"));
        assertEquals("DONE", progress.getString("sections.FinalJobs.status"));
    }

    // ------------------------------------------------------------------
    // The report: what an admin reads on the console
    // ------------------------------------------------------------------

    @Test
    void theReportBreaksTheRunDownByRootKey() {
        Map<String, SectionProgress> sections = new LinkedHashMap<>();
        sections.put("PlayerData", new SectionProgress(SectionStatus.DONE, 3, 3, "EverNifeCore", "PlayerData"));
        sections.put("Ontime", new SectionProgress(SectionStatus.DONE, 2, 2, "ENCTemplate", "LegacyOntimeSection"));
        sections.put("Cooldown", new SectionProgress(SectionStatus.PENDING_NO_ADAPTER, 181, 0, "", ""));
        sections.put("FinalRTP", new SectionProgress(SectionStatus.EMPTY, 153, 0, "", ""));

        LegacyImportReport report = new LegacyImportReport();
        report.setPaths(legacyFolder(), tempDir.resolve("PlayerData-Imported").toFile(),
                tempDir.resolve("PlayerData-Failed").toFile(), metadataFile());
        report.setFiles(3, 2, 1, 0);
        report.setSections(sections);
        report.setCompletion(false, false);

        String text = report.format();

        //the discovery breakdown, per status
        assertTrue(text.contains("4 root key(s)"), text);
        assertTrue(text.contains("2 with adapter"), text);
        assertTrue(text.contains("1 without adapter"), text);
        assertTrue(text.contains("1 empty"), text);

        //a claimed key names its owner AND its PDSection right next to the root key
        String ontime = sectionLine(text, "Ontime");
        assertTrue(ontime.contains("LegacyOntimeSection"), ontime);
        assertTrue(ontime.contains("ENCTemplate"), ontime);
        assertTrue(ontime.contains("2 found"), ontime);
        assertTrue(ontime.contains("[DONE]"), ontime);

        //an unclaimed key still says how much data it is holding hostage
        String cooldown = sectionLine(text, "Cooldown");
        assertTrue(cooldown.contains("181 found"), cooldown);
        assertTrue(cooldown.contains("[PENDING_NO_ADAPTER]"), cooldown);

        //an empty key is reported as empty, never as a pending adapter
        String finalRtp = sectionLine(text, "FinalRTP");
        assertTrue(finalRtp.contains("[EMPTY]"), finalRtp);
        assertFalse(finalRtp.contains("PENDING"), finalRtp);
    }

    @Test
    void theReportTellsArchivedFilesApartFromPendingOnes() {
        Map<String, SectionProgress> sections = new LinkedHashMap<>();
        sections.put("PlayerData", new SectionProgress(SectionStatus.DONE, 3, 3, "EverNifeCore", "PlayerData"));
        sections.put("Cooldown", new SectionProgress(SectionStatus.PENDING_NO_ADAPTER, 1, 0, "", ""));

        LegacyImportReport report = new LegacyImportReport();
        report.setPaths(legacyFolder(), tempDir.resolve("PlayerData-Imported").toFile(),
                tempDir.resolve("PlayerData-Failed").toFile(), metadataFile());
        report.setFiles(3, 2, 1, 0);
        report.setSections(sections);
        report.setCompletion(false, false);

        String text = report.format();

        //"processed" counted the pending files too, so it read as "archived" while meaning nothing
        assertFalse(text.contains("Files processed:"),
                "a count that lumps pending files in with archived ones misleads the admin: " + text);
        assertTrue(text.contains("3 file(s) found"), text);
        assertTrue(text.contains("2 file(s) fully imported"), text);
        assertTrue(text.contains("1 file(s) still pending"), text);
        assertTrue(text.contains("0 file(s) failed"), text);
        assertTrue(text.contains(metadataFile().getPath()), "an incomplete run must point at the progress file");
    }

    @Test
    void theRollbackWarningBelongsOnlyToTheRunThatDrainedTheFolder() {
        assertTrue(completionOf(false, true).isBecameCompleteThisRun(),
                "the run that finally emptied the folder is the one that owes the warning");
        assertFalse(completionOf(true, true).isBecameCompleteThisRun(),
                "a later boot merely FINDS the folder empty - the warning was already given");
        assertFalse(completionOf(false, false).isBecameCompleteThisRun());
        assertFalse(completionOf(true, false).isBecameCompleteThisRun());
    }

    private static LegacyImportReport completionOf(boolean wasComplete, boolean isCompleteNow) {
        LegacyImportReport report = new LegacyImportReport();
        report.setCompletion(wasComplete, isCompleteNow);
        return report;
    }

    @Test
    void theReportNarratesARealRunDownToTheOwningPlugin() throws IOException {
        //one claimed key (a real plugin owns it), one nobody claims, one empty
        writeLegacyYml("petrus.yml", String.join("\n",
                "PlayerData:",
                "  Username: Petrus",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1600000000000",
                "  lastSeen: 1700000000000",
                "  lastSaved: 1700000000001",
                "FinalJobs:",
                "  level: 42",
                "  job: miner",
                "Cooldown:",
                "  test_kit:",
                "    identifier: test_kit",
                "    timeStart: 1600000000000",
                "    timeDuration: 99999999",
                "FinalRTP: {}",
                ""));
        registerJobsSectionOwnedBy(realPluginData());

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        String text = reportOfThePersistedRun().format();

        //the four root keys the real run discovered, each one narrated
        assertTrue(text.contains("4 root key(s)"), text);
        String jobs = sectionLine(text, "FinalJobs");
        assertTrue(jobs.contains(FAKE_PLUGIN_NAME), "the log must name the plugin owning the key: " + jobs);
        assertTrue(jobs.contains("LegacyJobsPDSection"), jobs);
        assertTrue(jobs.contains("[DONE]"), jobs);
        assertTrue(sectionLine(text, "PlayerData").contains("EverNifeCore"), text);
        assertTrue(sectionLine(text, "Cooldown").contains("[PENDING_NO_ADAPTER]"), text);
        assertTrue(sectionLine(text, "FinalRTP").contains("[EMPTY]"), text);

        //the outcome of a run that kept its only file back, and WHY it did
        assertFalse(text.contains("Files processed:"), text);
        assertTrue(text.contains("1 file(s) found"), text);
        assertTrue(text.contains("0 file(s) fully imported"), text);
        assertTrue(text.contains("1 file(s) still pending"), text);
        assertTrue(text.contains("0 file(s) failed"), text);
    }

    @Test
    void theOutcomeNamesTheSingleRootKeyHoldingTheRunBack() throws IOException {
        //Cooldown is the ONLY unclaimed key here: a generic "1 pending" would leave the admin guessing
        //which plugin to go install
        writeLegacyYml("petrus.yml", String.join("\n",
                "PlayerData:",
                "  Username: Petrus",
                "  UUID: " + PETRUS_UUID,
                "  firstSeen: 1600000000000",
                "  lastSeen: 1700000000000",
                "  lastSaved: 1700000000001",
                "Cooldown:",
                "  test_kit:",
                "    identifier: test_kit",
                "    timeStart: 1600000000000",
                "    timeDuration: 99999999",
                "FinalJobs:",
                "  level: 42",
                "  job: miner",
                ""));
        registerJobsSectionWithAdapter();

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        String text = reportOfThePersistedRun().format();

        String pending = null;
        for (String line : text.split("\n")) {
            if (line.contains("still pending")) {
                pending = line;
            }
        }
        assertNotNull(pending, "the outcome must state what stayed behind: " + text);
        assertTrue(pending.contains("Cooldown"),
                "the outcome must NAME the root key holding the run back, not just count it: " + pending);
        assertFalse(pending.contains("FinalJobs"), "a key that migrated is not a blocker: " + pending);
    }

    @Test
    void theMetadataHeaderExplainsHowToRollBack() throws IOException {
        writeLegacyYml("petrus.yml", fullyMappedV3Yaml("Petrus", 42, "miner"));
        registerJobsSectionWithAdapter();

        PlayerController.bootstrap(writeStorageYml("storage.yml", ""));

        //the header is the admin's only instruction once the server is down and the folder is empty
        String raw = new String(Files.readAllBytes(metadataFile().toPath()), StandardCharsets.UTF_8);
        assertTrue(raw.contains("ROLLBACK"), raw);
        assertTrue(raw.contains("DOWNGRADE"), raw);
        assertTrue(raw.contains("PlayerData-Imported"), "the header must say WHERE the .yml files went: " + raw);
    }

    @Test
    void theBootThatCompletesTheMigrationIsTheOneThatFlipsTheFlag() throws IOException {
        writeLegacyYml("petrus.yml", fullyMappedV3Yaml("Petrus", 42, "miner"));
        File storageYml = writeStorageYml("storage.yml", "");

        //boot 1: nobody claims FinalJobs yet, so the migration stays open
        PlayerController.bootstrap(storageYml);
        assertFalse(LegacyMigrationMetadata.load(metadataFile()).isComplete(),
                "a pending key must leave the migration incomplete");

        //boot 2: the adapter finally shows up - THIS is the run that drains the folder, and the only
        //one that may warn about a downgrade
        registerJobsSectionWithAdapter();
        PlayerController.bootstrap(storageYml);
        assertTrue(LegacyMigrationMetadata.load(metadataFile()).isComplete());
        assertEquals(0, ymlFiles(legacyFolder()).length);
    }
}
