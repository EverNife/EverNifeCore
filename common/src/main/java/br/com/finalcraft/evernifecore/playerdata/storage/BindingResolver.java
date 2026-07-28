package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigratingCodec;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.storage.StorageRegistry;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.storage.config.PDSectionAdminConfig;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CacheOptions;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the dev configuration against the admin's storage.yml into a {@link PDSectionBinding}:
 *
 * <pre>
 * backend  = pdsections[plugin][sectionId].backend ?? cfg.defaultBackend ?? default-backend
 *            (must be declared AND enabled - otherwise, fatal error)
 *            (outside cfg.suggestedBackends -> warning; the admin has the final say)
 * collection = yml.collection ?? cfg.collection ?? "pd_&lt;plugin&gt;_&lt;sectionId&gt;"
 *            (reserved in the registry - a collision is a fatal error)
 * codec    = cfg.codec ?? ConfigFactoryCodec (bridge) - file(yaml->yaml | json->jsonPretty)
 *            ?? compact json  (carries the ConfigFactory type authority + ConfigLifecycle into storage)
 * cache    = always() ?? yml.cache (freshness only) + cfg.maxCached (hard bound, optional)
 *            (WHEN a cell enters/leaves memory is cfg.lifecycle, driven by the controller)
 * lock     = always active via @OptimisticLock on the PDSection base
 *            (detected by the annotation scan in EntityDescriptor.build())
 * </pre>
 *
 * <p>The resolved {@link CachingManager} is created in the supplied {@link RefRegistry} (the
 * plugin's child registry), so two plugins' managers for the same entity type never collide.</p>
 */
public final class BindingResolver {

    private BindingResolver() {
    }

    public static <S extends PDSection> PDSectionBinding<S> resolve(PDSectionConfiguration<S> cfg,
                                                                    ParsedStorageConfig parsed,
                                                                    StorageRegistry registry,
                                                                    RefRegistry refRegistry) {
        return resolve(cfg.getPluginData().getMetaInfo().getName(), cfg, parsed, registry, refRegistry);
    }

    /** Variant with an explicit plugin name (also used by tests, avoiding the need for ECPluginData). */
    public static <S extends PDSection> PDSectionBinding<S> resolve(String pluginName,
                                                                    PDSectionConfiguration<S> cfg,
                                                                    ParsedStorageConfig parsed,
                                                                    StorageRegistry registry,
                                                                    RefRegistry refRegistry) {
        String sectionName = cfg.getSectionId();
        String sectionId = SectionIds.sanitizePlugin(pluginName) + ":" + sectionName;
        List<String> warnings = new ArrayList<>();

        Optional<PDSectionAdminConfig> admin = parsed.getPDSection(pluginName, sectionName);

        // ---- backend (resolution chain + fatal checks) ----
        String backendName = admin.map(PDSectionAdminConfig::getBackendName).orElse(null);
        if (backendName == null) backendName = cfg.getDefaultBackend();
        if (backendName == null) backendName = parsed.getDefaultBackendName();

        BackendDefinition backend = parsed.getBackend(backendName).orElse(null);
        if (backend == null) {
            throw new StorageConfigException("PDSection '" + sectionId + "' points to backend '"
                    + backendName + "', which is not declared under 'storage-backends:' in storage.yml!");
        }
        if (!backend.isEnabled()) {
            throw new StorageConfigException("PDSection '" + sectionId + "' points to backend '"
                    + backendName + "', which is DISABLED - set 'storage-backends." + backendName
                    + ".enabled: true' in storage.yml!");
        }
        // hard developer constraint: the section may only live on certain backend types
        if (!cfg.getAllowedBackendTypes().isEmpty() && !cfg.getAllowedBackendTypes().contains(backend.getType())) {
            throw new StorageConfigException("PDSection '" + sectionId + "' is restricted to backend type(s) "
                    + cfg.getAllowedBackendTypes() + " by the developer, but is configured on backend '"
                    + backendName + "' of type " + backend.getType() + ". Point 'pdsections."
                    + SectionIds.sanitizePlugin(pluginName) + "." + sectionName
                    + ".storage-backend-id' at a backend of an allowed type.");
        }
        Storage storage = registry.get(backendName);

        if (!cfg.getSuggestedBackends().isEmpty() && !cfg.getSuggestedBackends().contains(backendName)) {
            warnings.add("PDSection '" + sectionId + "' is configured on backend '" + backendName
                    + "', outside the backends suggested by the developer "
                    + cfg.getSuggestedBackends() + ". Proceeding anyway (admin decides).");
        }

        // ---- collection (chain + claim) ----
        String collection = admin.map(PDSectionAdminConfig::getCollection).orElse(null);
        if (collection == null) collection = cfg.getCollection();
        if (collection == null) collection = defaultCollection(pluginName, sectionName);

        if (!StorageYamlParser.VALID_COLLECTION.matcher(collection).matches()) {
            throw new StorageConfigException("PDSection '" + sectionId + "' resolved to invalid"
                    + " collection name '" + collection + "' - must match "
                    + StorageYamlParser.VALID_COLLECTION.pattern());
        }
        if (!registry.claimCollection(backendName, collection, sectionId)) {
            throw new StorageConfigException("PDSection '" + sectionId + "' wants collection '"
                    + collection + "' on backend '" + backendName + "', but it is already used by '"
                    + registry.getCollectionOwner(backendName, collection) + "'!"
                    + " Set a different 'collection:' in storage.yml or in the dev configuration.");
        }

        // ---- codec ----
        Codec<S> codec = cfg.getCodec();
        if (codec == null) {
            codec = defaultCodec(backend, cfg.getPdSectionClass(), refRegistry);
        }
        // run the registered entity-schema migration chain on the raw payload before binding (no-op when none);
        // fail-fast here if a custom codec cannot expose an ObjectMapper while a chain is registered
        codec = EntitySchemaMigratingCodec.wrap(cfg.getPdSectionClass(), codec, "uuid");

        // ---- cache options ----
        // The dev declares the LIFECYCLE (when a cell enters and leaves memory), which the controller and
        // the LifecycleEngine drive; the store only needs freshness plus the optional hard bound. An admin
        // cache: override in storage.yml decides freshness alone - it never changes the lifecycle.
        CacheOptions cacheOptions = resolveCacheOptions(sectionId, cfg.getMaxCached(), admin.orElse(null));

        // ---- descriptor + caching manager ----
        EntityDescriptor<UUID, S> descriptor = EntityDescriptor
                .builder(UUID.class, cfg.getPdSectionClass())
                .collection(collection)
                .keyExtractor(PDSection::getStorageKey)
                .codec(codec)
                .build();   // @Indexed / @OptimisticLock are scanned here

        // A versioned section routed to a backend that cannot enforce the optimistic lock is silent
        // data loss under multi-instance writes. Warned (not rejected) when multi-instance intent is
        // declared (an enabled redis block).
        PdSyncBindGuard.check(sectionId, descriptor, storage, parsed, false, warnings);

        CachingManager<UUID, S> manager = refRegistry.manager(descriptor, storage, cacheOptions);

        return new PDSectionBinding<>(cfg, backendName, storage, descriptor, manager, warnings);
    }

    /**
     * The store options a section binds with: {@code always()} freshness (the lifecycle owns
     * releasing, not the cache policy) plus the optional hard {@code maxCached} bound. An admin
     * {@code cache:} entry replaces the freshness policy only.
     */
    private static CacheOptions resolveCacheOptions(String sectionId, int maxCached, PDSectionAdminConfig admin) {
        CachePolicy policy = CachePolicy.always();
        if (admin != null && admin.getCachePolicyName() != null) {
            try {
                policy = CachePolicy.fromAdminConfig(admin.getCachePolicyName(), admin.getCacheTtlSeconds());
            } catch (IllegalArgumentException e) {
                throw new StorageConfigException("PDSection '" + sectionId + "': " + e.getMessage(), e);
            }
        }
        return CacheOptions.builder()
                .policy(policy)
                .maxSize(maxCached > 0 ? maxCached : CacheOptions.UNBOUNDED)
                .build();
    }

    /**
     * Rebinds an existing binding to another backend (already validated and enabled) -
     * the runtime transfer cutover. Same collection and same dev configuration;
     * storage, manager and default codec follow the target backend.
     */
    public static <S extends PDSection> PDSectionBinding<S> rebindTo(PDSectionBinding<S> current,
                                                                     String targetBackendName,
                                                                     ParsedStorageConfig parsed,
                                                                     StorageRegistry registry,
                                                                     RefRegistry refRegistry) {
        PDSectionConfiguration<S> cfg = current.getConfiguration();
        BackendDefinition backend = parsed.getBackend(targetBackendName)
                .orElseThrow(() -> new StorageConfigException("Backend '" + targetBackendName + "' is not declared!"));
        if (!cfg.getAllowedBackendTypes().isEmpty() && !cfg.getAllowedBackendTypes().contains(backend.getType())) {
            throw new StorageConfigException("Cannot transfer PDSection '" + cfg.getPdSectionClass().getSimpleName()
                    + "' to backend '" + targetBackendName + "' of type " + backend.getType()
                    + ": the developer restricted it to backend type(s) " + cfg.getAllowedBackendTypes() + ".");
        }
        Storage storage = registry.get(targetBackendName);
        String collection = current.getCollection();

        //the claim travels with the current owner (same id used in the original resolve)
        String owner = registry.getCollectionOwner(current.getBackendName(), collection);
        if (owner == null) owner = cfg.getPdSectionClass().getName();
        if (!registry.claimCollection(targetBackendName, collection, owner)) {
            throw new StorageConfigException("Cannot transfer PDSection '" + owner + "': collection '"
                    + collection + "' on backend '" + targetBackendName + "' is already used by '"
                    + registry.getCollectionOwner(targetBackendName, collection) + "'!");
        }

        Codec<S> codec = cfg.getCodec();
        if (codec == null) {
            codec = defaultCodec(backend, cfg.getPdSectionClass(), refRegistry);
        }
        codec = EntitySchemaMigratingCodec.wrap(cfg.getPdSectionClass(), codec, "uuid");

        EntityDescriptor<UUID, S> descriptor = EntityDescriptor
                .builder(UUID.class, cfg.getPdSectionClass())
                .collection(collection)
                .keyExtractor(PDSection::getStorageKey)
                .codec(codec)
                .build();
        //same soft-warn the boot-time resolve applies: a runtime transfer onto a lock-unenforcing
        //backend under multi-instance intent is surfaced (not blocked) - the caller logs it
        List<String> warnings = new ArrayList<>();
        PdSyncBindGuard.check(cfg.getPdSectionClass().getSimpleName() + " (transfer target)",
                descriptor, storage, parsed, false, warnings);
        //the runtime transfer keeps the section's declared lifecycle and bound across the cutover
        CachingManager<UUID, S> manager = refRegistry.manager(descriptor, storage,
                resolveCacheOptions(cfg.getPdSectionClass().getSimpleName(), cfg.getMaxCached(), null));

        return new PDSectionBinding<>(cfg, targetBackendName, storage, descriptor, manager, warnings);
    }

    /**
     * A backend-safe collection name: {@code <prefix>_<plugin>_<sectionId>}. The section id is already
     * validated and lowercase ({@link SectionIds}); only the plugin name is sanitized.
     */
    public static String collectionName(String prefix, String pluginName, String sectionId) {
        return prefix + "_" + SectionIds.sanitizePlugin(pluginName) + "_" + sectionId;
    }

    /** Default collection name of a PDSection: {@code pd_<plugin>_<sectionId>}. */
    public static String defaultCollection(String pluginName, String sectionId) {
        return collectionName("pd", pluginName, sectionId);
    }

    /**
     * Default codec per backend - also used by {@link PlayerDataBinding} and the account layer. Delegates
     * to {@link BackendDefinition#defaultCodec(Class)} (the definition owns the type/format mapping); this
     * public entry point is kept so the PlayerData resolution and account layer keep a single call site.
     */
    public static <S> Codec<S> defaultCodec(BackendDefinition backend, Class<S> type) {
        return defaultCodec(backend, type, null);
    }

    /**
     * As {@link #defaultCodec(BackendDefinition, Class)}, but the bridge codec is <b>ref-aware</b>: a
     * {@code Ref} field of {@code type} resolves against {@code refRegistry} once decoded. Pass the same
     * registry the resolved {@link CachingManager} is created in (the plugin's child registry), so a ref
     * inside the section resolves an entity registered in that registry. A {@code null} registry is the
     * plain bridge - the format/type mapping is unchanged either way.
     */
    public static <S> Codec<S> defaultCodec(BackendDefinition backend, Class<S> type, RefRegistry refRegistry) {
        return backend.defaultCodec(type, refRegistry);
    }
}
