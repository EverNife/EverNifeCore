package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.config.factory.ConfigFactoryCodec;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.storage.BackendType;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.StorageRegistry;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigratingCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ECoreTest
class BindingResolverTest {


    @TempDir
    Path tempDir;

    /** Test sections (contract: simple no-arg constructor, automatic persistence). */
    public static class JobsPDSection extends PDSection {
    }

    public static class PointsPDSection extends PDSection {
    }

    /** A section with a persisted field, so a codec round-trip has something to preserve. */
    public static class RoundTripSection extends PDSection {
        public String note;
    }

    private ParsedStorageConfig parsed;
    private StorageRegistry registry;
    private RefRegistry refRegistry;

    private void setup(String... pdsectionsYamlLines) throws IOException {
        StringBuilder yml = new StringBuilder(String.join("\n",
                "storage-backends:",
                "  main_storage: { enabled: true, type: memory }",
                "  economy_storage: { enabled: true, type: memory }",
                "  disabled_storage: { enabled: false, type: memory }",
                "  yaml_files:",
                "    enabled: true",
                "    type: localfile",
                "    path: \"" + tempDir.resolve("yamlfiles").toString().replace("\\", "/") + "\"",
                "    format: yaml",
                "  json_files:",
                "    enabled: true",
                "    type: localfile",
                "    path: \"" + tempDir.resolve("jsonfiles").toString().replace("\\", "/") + "\"",
                "    format: json",
                "default-backend: main_storage",
                ""));
        for (String line : pdsectionsYamlLines) {
            yml.append(line).append("\n");
        }

        File file = tempDir.resolve("storage_" + System.nanoTime() + ".yml").toFile();
        Files.write(file.toPath(), yml.toString().getBytes(StandardCharsets.UTF_8));

        parsed = StorageYamlParser.parse(file);
        registry = StorageYamlParser.buildRegistry(parsed, StorageLogConfig.silent());
        refRegistry = new RefRegistry();
    }

    private PDSectionConfiguration.Builder<JobsPDSection> jobsCfg() {
        return PDSectionConfiguration.builder(null, JobsPDSection.class);
    }

    @Test
    void fallsBackToDefaultBackendAndDerivedCollection() throws IOException {
        setup();
        PDSectionBinding<JobsPDSection> binding =
                BindingResolver.resolve("FinalJobs", jobsCfg().build(), parsed, registry, refRegistry);

        assertEquals("main_storage", binding.getBackendName());
        assertEquals("pd_finaljobs_jobspdsection", binding.getCollection());
        assertTrue(binding.getManager().defaultPolicy() instanceof CachePolicy.AlwaysPolicy);
        assertTrue(binding.getResolutionWarnings().isEmpty());
        assertEquals("application/json", binding.getDescriptor().codec().contentType());
    }

    @Test
    void devDefaultBackendIsUsedWhenNoAdminOverride() throws IOException {
        setup();
        PDSectionBinding<JobsPDSection> binding = BindingResolver.resolve("FinalJobs",
                jobsCfg().defaultBackend("economy_storage").build(), parsed, registry, refRegistry);
        assertEquals("economy_storage", binding.getBackendName());
    }

    @Test
    void adminOverrideWinsOverDevDefault() throws IOException {
        setup("pdsections:",
                "  FinalJobs:",
                "    JobsPDSection:",
                "      storage-backend-id: economy_storage",
                "      collection: custom_jobs");
        PDSectionBinding<JobsPDSection> binding = BindingResolver.resolve("FinalJobs",
                jobsCfg().defaultBackend("main_storage").collection("dev_collection").build(),
                parsed, registry, refRegistry);

        assertEquals("economy_storage", binding.getBackendName());
        assertEquals("custom_jobs", binding.getCollection());
    }

    @Test
    void adminOutsideSuggestedBackendsWarnsButProceeds() throws IOException {
        setup("pdsections:",
                "  FinalJobs:",
                "    JobsPDSection:",
                "      storage-backend-id: economy_storage");
        PDSectionBinding<JobsPDSection> binding = BindingResolver.resolve("FinalJobs",
                jobsCfg().suggestedBackends("main_storage", "yaml_files").build(),
                parsed, registry, refRegistry);

        assertEquals("economy_storage", binding.getBackendName());
        assertEquals(1, binding.getResolutionWarnings().size());
        assertTrue(binding.getResolutionWarnings().get(0).contains("economy_storage"));
    }

    @Test
    void unknownBackendIsHardError() throws IOException {
        setup("pdsections:",
                "  FinalJobs:",
                "    JobsPDSection:",
                "      storage-backend-id: nonexistent");
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> BindingResolver.resolve("FinalJobs", jobsCfg().build(), parsed, registry, refRegistry));
        assertTrue(error.getMessage().contains("nonexistent"));
        assertTrue(error.getMessage().contains("FinalJobs:JobsPDSection"));
    }

    @Test
    void disabledBackendIsHardError() throws IOException {
        setup();
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> BindingResolver.resolve("FinalJobs",
                        jobsCfg().defaultBackend("disabled_storage").build(), parsed, registry, refRegistry));
        assertTrue(error.getMessage().contains("DISABLED"));
    }

    @Test
    void collectionCollisionIsHardError() throws IOException {
        setup();
        BindingResolver.resolve("FinalJobs",
                jobsCfg().collection("shared_collection").build(), parsed, registry, refRegistry);

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> BindingResolver.resolve("FinalPoints",
                        PDSectionConfiguration.builder(null, PointsPDSection.class)
                                .collection("shared_collection").build(),
                        parsed, registry, refRegistry));
        assertTrue(error.getMessage().contains("FinalJobs:JobsPDSection")); // names the current owner
    }

    @Test
    void sameCollectionOnDifferentBackendsIsFine() throws IOException {
        setup();
        BindingResolver.resolve("FinalJobs",
                jobsCfg().collection("shared_collection").build(), parsed, registry, refRegistry);
        // different backend, same collection name - allowed
        PDSectionBinding<PointsPDSection> binding = BindingResolver.resolve("FinalPoints",
                PDSectionConfiguration.builder(null, PointsPDSection.class)
                        .collection("shared_collection").defaultBackend("economy_storage").build(),
                parsed, registry, refRegistry);
        assertEquals("economy_storage", binding.getBackendName());
    }

    @Test
    void adminCachePolicyOverridesDev() throws IOException {
        setup("pdsections:",
                "  FinalJobs:",
                "    JobsPDSection:",
                "      cache: { policy: TTL, ttlSeconds: 12 }");
        PDSectionBinding<JobsPDSection> binding = BindingResolver.resolve("FinalJobs",
                jobsCfg().cache(SectionCachePolicy.resident()).build(), parsed, registry, refRegistry);

        CachePolicy policy = binding.getManager().defaultPolicy();
        assertTrue(policy instanceof CachePolicy.TtlPolicy);
        assertEquals(12, ((CachePolicy.TtlPolicy) policy).getTtl().getSeconds());
    }

    @Test
    void devCachePolicyUsedWhenNoAdminOverride() throws IOException {
        setup();
        PDSectionBinding<JobsPDSection> binding = BindingResolver.resolve("FinalJobs",
                jobsCfg().cache(SectionCachePolicy.ttl(java.time.Duration.ofSeconds(30))).build(),
                parsed, registry, refRegistry);
        CachePolicy policy = binding.getManager().defaultPolicy();
        assertTrue(policy instanceof CachePolicy.TtlPolicy);
        assertEquals(30, ((CachePolicy.TtlPolicy) policy).getTtl().getSeconds());
    }

    @Test
    void codecFollowsBackendFormat() throws IOException {
        setup();

        // A section is an EntitySchema, so its codec is always wrapped for raw-tree migration -
        // a chain may be registered long after the binding is built. Assert the format through
        // contentType(), which the wrapper delegates, instead of the concrete codec class.
        PDSectionBinding<JobsPDSection> yaml = BindingResolver.resolve("FinalJobs",
                jobsCfg().defaultBackend("yaml_files").build(), parsed, registry, refRegistry);
        assertEquals("application/yaml", yaml.getDescriptor().codec().contentType());
        assertTrue(yaml.getDescriptor().codec() instanceof EntitySchemaMigratingCodec);

        PDSectionBinding<PointsPDSection> json = BindingResolver.resolve("FinalPoints",
                PDSectionConfiguration.builder(null, PointsPDSection.class)
                        .defaultBackend("json_files").build(),
                parsed, registry, refRegistry);
        assertEquals("application/json", json.getDescriptor().codec().contentType());
    }

    // ------------------------------------------------------------------
    // The global default codec is the ConfigFactory bridge, per backend type/format
    // ------------------------------------------------------------------

    @Test
    void defaultCodecIsTheConfigFactoryBridgePerBackendFormat() throws IOException {
        setup();

        // every non-file backend: compact JSON bridge
        Codec<JobsPDSection> memory = BindingResolver.defaultCodec(
                parsed.getBackend("main_storage").get(), JobsPDSection.class);
        assertTrue(memory instanceof ConfigFactoryCodec, "the global default must be the bridge codec");
        assertEquals("application/json", memory.contentType());

        // a YAML file backend: YAML bridge
        Codec<JobsPDSection> yaml = BindingResolver.defaultCodec(
                parsed.getBackend("yaml_files").get(), JobsPDSection.class);
        assertTrue(yaml instanceof ConfigFactoryCodec);
        assertEquals("application/yaml", yaml.contentType());

        // a JSON file backend: (pretty) JSON bridge - still application/json
        Codec<JobsPDSection> jsonFile = BindingResolver.defaultCodec(
                parsed.getBackend("json_files").get(), JobsPDSection.class);
        assertTrue(jsonFile instanceof ConfigFactoryCodec);
        assertEquals("application/json", jsonFile.contentType());
    }

    @Test
    void aSectionRoundTripsThroughTheResolvedBridgeCodec() throws IOException {
        setup();
        PDSectionBinding<RoundTripSection> binding = BindingResolver.resolve("FinalJobs",
                PDSectionConfiguration.builder(null, RoundTripSection.class).build(),
                parsed, registry, refRegistry);

        // the descriptor's codec is the bridge (wrapped for schema migration); a section round-trips through it
        Codec<RoundTripSection> codec = binding.getDescriptor().codec();
        RoundTripSection section = new RoundTripSection();
        section.note = "hello-bridge";

        RoundTripSection out = codec.decode(codec.encode(section));
        assertEquals("hello-bridge", out.note, "the bridge codec preserves a section field through storage bytes");
        assertEquals(1, out.getSchemaVersion());
    }

    @Test
    void allowedBackendTypesPassesOnAnAllowedType() throws IOException {
        setup();
        // restricted to MEMORY and resolved (by default) on main_storage, which is a memory backend
        PDSectionBinding<JobsPDSection> binding = BindingResolver.resolve("FinalJobs",
                jobsCfg().allowedBackendTypes(BackendType.MEMORY).build(), parsed, registry, refRegistry);
        assertEquals("main_storage", binding.getBackendName());
    }

    @Test
    void allowedBackendTypesRejectsADisallowedType() throws IOException {
        setup();
        // restricted to MEMORY but pointed at a localfile backend -> fatal
        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> BindingResolver.resolve("FinalJobs",
                        jobsCfg().allowedBackendTypes(BackendType.MEMORY).defaultBackend("yaml_files").build(),
                        parsed, registry, refRegistry));
        assertTrue(error.getMessage().contains("restricted to backend type"));
        assertTrue(error.getMessage().contains("LOCALFILE"));
    }

    // ------------------------------------------------------------------
    // Bind-guard: versioned entity on a lock-unenforcing backend + multi-instance intent -> WARNS
    // (never aborts; intent now comes from an enabled redis block, not the always-on sync flag)
    // ------------------------------------------------------------------

    @Test
    void versionedSectionOnNonEnforcingBackendWithRedisIntentWarns() throws IOException {
        // an enabled redis block declares multi-instance intent; main_storage is memory (non-enforcing)
        setup("multi-server-cache-sync:",
                "  redis:",
                "    enabled: true",
                "    host: localhost");
        PDSectionBinding<JobsPDSection> binding =
                BindingResolver.resolve("FinalJobs", jobsCfg().build(), parsed, registry, refRegistry);
        String warnings = String.join(" | ", binding.getResolutionWarnings());
        assertTrue(warnings.contains("does NOT enforce"), warnings);
        assertTrue(warnings.contains("redis"), warnings);
    }

    @Test
    void versionedSectionOnNonEnforcingBackendWithoutIntentIsAllowed() throws IOException {
        // no enabled redis block -> no multi-instance intent -> the guard is a no-op even on a
        // non-enforcing backend (the common single-server case)
        setup();
        PDSectionBinding<JobsPDSection> binding =
                BindingResolver.resolve("FinalJobs", jobsCfg().build(), parsed, registry, refRegistry);
        assertEquals("main_storage", binding.getBackendName());
        assertTrue(binding.getResolutionWarnings().isEmpty());
    }

    /** Parses a second storage config (same backends) with an enabled/disabled redis block for rebindTo. */
    private ParsedStorageConfig parseWithRedis(boolean redisEnabled) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  main_storage: { enabled: true, type: memory }",
                "  economy_storage: { enabled: true, type: memory }",
                "default-backend: main_storage",
                "multi-server-cache-sync:",
                "  redis:",
                "    enabled: " + redisEnabled,
                "    host: localhost",
                "");
        File file = tempDir.resolve("storage_sync_" + System.nanoTime() + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return StorageYamlParser.parse(file);
    }

    // ------------------------------------------------------------------
    // rebindTo re-runs the bind-guard: moving a versioned entity to a non-enforcing backend under
    // multi-instance intent WARNS (never blocks the transfer)
    // ------------------------------------------------------------------

    @Test
    void rebindSectionToNonEnforcingBackendWithRedisIntentWarns() throws IOException {
        // resolve cleanly with no intent, then attempt a runtime transfer under a redis-enabled config
        setup();
        PDSectionBinding<JobsPDSection> current =
                BindingResolver.resolve("FinalJobs", jobsCfg().build(), parsed, registry, refRegistry);

        ParsedStorageConfig redisOn = parseWithRedis(true);
        //a fresh RefRegistry for the target context: the guard now warns (no longer aborts), so the
        //rebind runs to completion and would otherwise collide with 'current' in the same registry
        PDSectionBinding<JobsPDSection> rebound =
                BindingResolver.rebindTo(current, "economy_storage", redisOn, registry, new RefRegistry());
        String warnings = String.join(" | ", rebound.getResolutionWarnings());
        assertTrue(warnings.contains("does NOT enforce"), warnings);
    }

    @Test
    void rebindPlayerDataToNonEnforcingBackendWithRedisIntentWarns() throws IOException {
        setup();
        PlayerDataBinding current = PlayerDataBinding.resolve(parsed, registry, refRegistry);

        ParsedStorageConfig redisOn = parseWithRedis(true);
        //fresh RefRegistry: the guard now warns (no longer aborts), so the rebind completes and
        //would otherwise collide with 'current' registered in the same registry
        PlayerDataBinding rebound =
                PlayerDataBinding.rebindTo(current, "economy_storage", redisOn, registry, new RefRegistry());
        String warnings = String.join(" | ", rebound.getResolutionWarnings());
        assertTrue(warnings.contains("does NOT enforce"), warnings);
    }

    // ------------------------------------------------------------------
    // base-binding boot guard: redis intent + non-enforcing playerdata backend WARNS at resolve
    // ------------------------------------------------------------------

    @Test
    void playerDataResolveOnNonEnforcingBackendWithRedisIntentWarns() throws IOException {
        setup("multi-server-cache-sync:",
                "  redis:",
                "    enabled: true",
                "    host: localhost");
        PlayerDataBinding binding = PlayerDataBinding.resolve(parsed, registry, refRegistry);
        String warnings = String.join(" | ", binding.getResolutionWarnings());
        assertTrue(warnings.contains("does NOT enforce"), warnings);
    }

}
