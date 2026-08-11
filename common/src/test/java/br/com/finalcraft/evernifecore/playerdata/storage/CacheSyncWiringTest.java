package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.storage.StorageRegistry;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.codec.JacksonJsonCodec;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The semantics of the cache-sync wiring: {@code enabled: false} never starts anything; a set where
 * nothing can push is a DELIBERATELY silent no-op (no nag - that is the stock single-server install);
 * an explicit {@code native} reaches a file backend's feed; and selection is per manager, so one
 * feedless backend no longer costs the others their coherence. Runs over memory/H2/groupedfile - no
 * Docker.
 */
@ECoreTest
class CacheSyncWiringTest {


    @TempDir
    Path tempDir;

    private final List<StorageRegistry> openRegistries = new ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void teardown() {
        for (StorageRegistry registry : openRegistries) {
            try {
                registry.closeAll().join();
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
        }
        openRegistries.clear();
    }

    private ParsedStorageConfig parse(boolean enableSync) throws IOException {
        return parse(enableSync, null);
    }

    private ParsedStorageConfig parse(boolean enableSync, String transport) throws IOException {
        String dataPath = tempDir.resolve("gf_" + System.nanoTime()).toString().replace("\\", "/");
        String yml = String.join("\n",
                "storage-backends:",
                "  files:",
                "    enabled: true",
                "    type: groupedfile",
                "    path: \"" + dataPath + "\"",
                "    format: yaml",
                "default-backend: files",
                "network:",
                "  storage-backend-id: files",
                "multi-server-cache-sync:",
                "  enabled: " + enableSync,
                transport == null ? "" : "  transport: " + transport,
                "");
        File file = tempDir.resolve("storage_" + System.nanoTime() + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return StorageYamlParser.parse(file);
    }

    /**
     * A config with one backend of each kind the native path treats differently: a change feed that
     * works anywhere (memory), one with no feed at all (H2), and a file backend, whose feed is
     * machine-local and therefore only reachable by an explicit {@code native}.
     */
    private ParsedStorageConfig parseMixed() throws IOException {
        String name = "mixed_" + System.nanoTime();
        String dataPath = tempDir.resolve("gf_" + name).toString().replace("\\", "/");
        String yml = String.join("\n",
                "storage-backends:",
                "  mem:",
                "    enabled: true",
                "    type: memory",
                "  feedless:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1\"",
                "  files:",
                "    enabled: true",
                "    type: groupedfile",
                "    path: \"" + dataPath + "\"",
                "    format: yaml",
                "default-backend: mem",
                "network:",
                "  storage-backend-id: mem",
                "multi-server-cache-sync:",
                "  enabled: true",
                "");
        File file = tempDir.resolve("storage_" + name + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return StorageYamlParser.parse(file);
    }

    /** Builds one real (feedless, groupedfile-backed) manager over the parsed config. */
    private CachingManager<UUID, PlayerData> managerOver(ParsedStorageConfig parsed) {
        return managerOver(openRegistry(parsed), "files", "cachesync_test");
    }

    private StorageRegistry openRegistry(ParsedStorageConfig parsed) {
        StorageRegistry registry = StorageYamlParser.buildRegistry(parsed, StorageLogConfig.silent());
        registry.initAll().join();
        openRegistries.add(registry);
        return registry;
    }

    private CachingManager<UUID, PlayerData> managerOver(StorageRegistry registry, String backendId,
                                                         String collection) {
        EntityDescriptor<UUID, PlayerData> descriptor = EntityDescriptor.builder(UUID.class, PlayerData.class)
                .collection(collection)
                .keyExtractor(PlayerData::getUniqueId)
                .codec(new JacksonJsonCodec<>(PlayerData.class))
                .build();
        return new RefRegistry().manager(descriptor, registry.get(backendId), CachePolicy.always());
    }

    @Test
    void syncDisabled_returnsNullAndWarnsNothing() throws IOException {
        ParsedStorageConfig parsed = parse(false);
        List<CachingManager<?, ?>> managers = new ArrayList<>();
        managers.add(managerOver(parsed));

        List<String> warnings = new ArrayList<>();
        CacheSyncWiring.Handle handle = CacheSyncWiring.startIfEnabled(parsed, managers,
                info -> { }, warnings::add);

        assertNull(handle, "sync disabled must be a clean no-op (null handle)");
        assertTrue(warnings.isEmpty(), "a disabled sync must not warn: " + warnings);
    }

    @Test
    void autoOverAFileBackendIsStillANoOp() throws IOException {
        ParsedStorageConfig parsed = parse(true);
        List<CachingManager<?, ?>> managers = new ArrayList<>();
        managers.add(managerOver(parsed));

        List<String> warnings = new ArrayList<>();
        CacheSyncWiring.Handle handle = CacheSyncWiring.startIfEnabled(parsed, managers,
                info -> { }, warnings::add);

        //a file backend DOES have a change feed, but it watches this machine's filesystem: it cannot
        //carry another server's write, so on the default transport it would cost a watcher thread per
        //storage to report only writes this server just made
        assertNull(handle, "auto over a file backend must stay a NO-OP (null)");
        assertTrue(warnings.isEmpty(), "the no-op must be SILENT (no nag): " + warnings);
    }

    @Test
    void anExplicitNativeTransportDoesReachTheFileBackendsFeed() throws IOException {
        ParsedStorageConfig parsed = parse(true, "native");
        List<CachingManager<?, ?>> managers = new ArrayList<>();
        managers.add(managerOver(parsed));

        List<String> warnings = new ArrayList<>();
        CacheSyncWiring.Handle handle = CacheSyncWiring.startIfEnabled(parsed, managers,
                info -> { }, warnings::add);

        //the gate above is about the DEFAULT, not about the feed being unusable: an admin who edits
        //the data files by hand asks for it by name and gets it
        assertNotNull(handle, "'native' must reach the file backend's feed: " + warnings);
        handle.close();
    }

    @Test
    void aFeedlessBackendDoesNotDisableTheOthers() throws IOException {
        ParsedStorageConfig parsed = parseMixed();
        StorageRegistry registry = openRegistry(parsed);
        List<CachingManager<?, ?>> managers = new ArrayList<>();
        managers.add(managerOver(registry, "mem", "with_feed"));
        managers.add(managerOver(registry, "feedless", "no_feed"));
        managers.add(managerOver(registry, "files", "on_files"));

        List<String> infos = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        CacheSyncWiring.Handle handle = CacheSyncWiring.startIfEnabled(parsed, managers,
                infos::add, warnings::add);

        //selection is per manager: one backend without a feed used to take the whole native path down
        assertNotNull(handle, "the manager WITH a feed must still be synced: " + warnings);
        assertTrue(infos.stream().anyMatch(info -> info.contains("1 of 3")),
                "the coverage must be reported so partial is never read as full: " + infos);

        //the two reasons for being left out need different answers, so they are reported apart
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("no_feed")),
                "a collection with no cross-instance signal must be named: " + warnings);
        assertTrue(warnings.stream().noneMatch(warning -> warning.contains("with_feed")),
                "a covered collection must not be reported as uncovered: " + warnings);
        assertTrue(warnings.stream().noneMatch(warning -> warning.contains("on_files")),
                "a file backend under 'auto' is working as intended, not a coherence hole: " + warnings);
        assertTrue(infos.stream().anyMatch(info -> info.contains("on_files")),
                "the skipped file backend must still be surfaced, as info: " + infos);
        handle.close();
    }
}
