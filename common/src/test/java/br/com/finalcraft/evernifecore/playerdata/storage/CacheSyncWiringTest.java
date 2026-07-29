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
 * The default semantics of the cache-sync wiring: {@code multi-server-cache-sync.enabled: false}
 * never starts anything; {@code enabled: true} with no redis block over a feedless backend is a
 * DELIBERATELY silent no-op (no nag - a backend with no feed and no redis simply has no coherence,
 * which is fine on a single server). Runs over a groupedfile backend - feedless, no Docker.
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

    /** Builds one real (feedless, groupedfile-backed) manager over the parsed config. */
    private CachingManager<UUID, PlayerData> managerOver(ParsedStorageConfig parsed) {
        StorageRegistry registry = StorageYamlParser.buildRegistry(parsed, StorageLogConfig.silent());
        registry.initAll().join();
        openRegistries.add(registry);
        EntityDescriptor<UUID, PlayerData> descriptor = EntityDescriptor.builder(UUID.class, PlayerData.class)
                .collection("cachesync_test")
                .keyExtractor(PlayerData::getUniqueId)
                .codec(new JacksonJsonCodec<>(PlayerData.class))
                .build();
        return new RefRegistry().manager(descriptor, registry.get("files"), CachePolicy.always());
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
}
