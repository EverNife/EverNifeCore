package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlDefaults;
import br.com.finalcraft.evernifecore.storage.config.StorageYamlParser;
import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.Codec;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.RefRegistry;
import br.com.finalcraft.everydatabase.manager.cache.CacheOptions;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A plugin-OWNED storage handle: one live {@link Storage} plus the {@link BackendDefinition} it was
 * opened from, the {@link RefRegistry} that scopes its managers, and the {@link CachingManager}s
 * created on it. A plugin that depends on EverNifeCore opens one to route its OWN entities through a
 * backend its own config declares - the drivers are already downloaded by the core at boot, so any
 * type the admin picks just works.
 *
 * <p>A handle is a self-contained context the plugin {@link #open}s and {@link #close}s itself. Open
 * it ONCE on enable: the shared registry it wires into keeps its identity across core storage
 * reloads, so the handle - and every manager and live {@code Ref} on it - stays valid with no reload
 * callback and no re-open. {@link #openOrReload} exists for the PLUGIN'S own config changing, not for
 * the core's.
 *
 * <pre>{@code
 * // onEnable - one line: seeds a groupedfile default under the plugin's dataFolder if the section is
 * // empty, logs through the plugin, connects (async - join() to block):
 * STORAGE = ECStorage.openOrReload(this.getEcPluginData(), config.getConfigSection("storage"), STORAGE).join();
 * CachingManager<UUID, Snapshot> snaps = STORAGE.manager(SNAPSHOTS, CachePolicy.always());
 *
 * // onReload (of the PLUGIN's config) - the SAME call reuses the live connection when the config
 * // still points at the same DB, else reconnects; either way every cache (dirty included) is wiped
 * // and managers are re-derived. flushManagers() first if you must not lose unsaved writes.
 *
 * // onDisable:
 * STORAGE.close().join();
 * }</pre>
 *
 * <p>It does NOT implement {@link Storage}: a delegating wrapper would hide the concrete storage's own
 * interfaces (e.g. {@code SchemaAwareStorage}) from an {@code instanceof} check. Take the raw storage
 * from {@link #storage()} when you need it.
 */
public final class ECStorage {

    /** The plugin that owns this handle, or {@code null} for a plugin-less open (advanced/tests). Used for logging. */
    private final ECPluginData plugin;
    private final Storage storage;
    private final BackendDefinition definition;

    /**
     * The registry this handle's managers register in: the plugin's SHARED child registry (the one its
     * PDSections resolve through, via {@link ECStorageRegistries}) when opened with an
     * {@link ECPluginData}, a private one otherwise. The shared registry is <b>stable in identity
     * across core reloads</b> - a reload swaps its content, never the object - so a handle opened once
     * on enable stays correctly wired for the life of the plugin. {@link #reset()}/{@link #close()}
     * unregister only THIS handle's own types, never the whole registry.
     */
    private final RefRegistry refRegistry;

    /**
     * Managers created through {@link #manager(EntityDescriptor, CachePolicy)} on the OWNED registry,
     * memoized by entity type (one manager per type, matching the RefRegistry constraint). Managers
     * created on a caller-supplied registry are NOT tracked here - that lifecycle is the caller's.
     */
    private final Map<Class<?>, CachingManager<?, ?>> managers = new ConcurrentHashMap<>();

    /** Flipped by {@link #close()} so a reload can tell a reusable handle from a dead one. */
    private volatile boolean open = true;

    ECStorage(ECPluginData plugin, Storage storage, BackendDefinition definition, RefRegistry refRegistry) {
        this.plugin = plugin;
        this.storage = storage;
        this.definition = definition;
        this.refRegistry = refRegistry;
    }

    // ---------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------

    /** The plugin that owns this handle, or {@code null} for a plugin-less open. */
    public ECPluginData plugin() {
        return plugin;
    }

    /** The live, initialized storage - use it for raw repositories or a concrete-storage {@code instanceof}. */
    public Storage storage() {
        return storage;
    }

    /** The backend definition this storage was opened from (its type and, for file backends, format). */
    public BackendDefinition definition() {
        return definition;
    }

    /**
     * The registry this handle's managers register in - the plugin's SHARED child registry when opened with
     * a plugin (else private). Use it to build a ref-aware codec bound to the SAME registry for a PDSection
     * that holds a {@code Ref} into this storage: {@code refRegistry().codec(MySection.class)}.
     */
    public RefRegistry refRegistry() {
        return refRegistry;
    }

    /** {@code false} once {@link #close()} has been called - a closed handle cannot be reused. */
    public boolean isOpen() {
        return open;
    }

    /**
     * The default codec for {@code type} on this backend, so a plugin's entity honours the configured
     * format exactly as PlayerData does: a file backend picks YAML or (pretty) JSON from its
     * {@code format}, every other backend uses compact JSON.
     *
     * <p>REF-AWARE against {@link #refRegistry()} - this handle's registry, shared with the plugin's
     * PDSections when it was opened with a plugin - so a {@code Ref} field resolves once decoded
     * instead of answering empty. The binding is additive: an entity with no {@code Ref} field decodes
     * exactly as before.</p>
     */
    public <V> Codec<V> defaultCodec(Class<V> type) {
        ensureUsable();
        return definition.defaultCodec(type, refRegistry);
    }

    // ---------------------------------------------------------------------
    // Repositories & managers
    // ---------------------------------------------------------------------

    /**
     * A raw, uncached repository for {@code descriptor} - reads and writes go straight to the backend.
     * The Storage itself memoizes repositories by collection name, so repeat calls return the same one.
     * Prefer {@link #manager(EntityDescriptor, CachePolicy)} when you want caching / {@code Ref} resolution.
     */
    public <K, V> Repository<K, V> repository(EntityDescriptor<K, V> descriptor) {
        ensureUsable();
        return storage.repository(descriptor);
    }

    /** {@link #manager(EntityDescriptor, CacheOptions)} with an unbounded cache under the given policy. */
    public <K, V> CachingManager<K, V> manager(EntityDescriptor<K, V> descriptor, CachePolicy policy) {
        return manager(descriptor, CacheOptions.of(policy));
    }

    /**
     * A {@link CachingManager} for {@code descriptor} on THIS storage, created in this handle's
     * {@link #refRegistry()} (shared with the plugin's PDSections when opened with a plugin) and tracked for
     * {@link #reset()}. Memoized by entity type: a second call for the same type returns the same manager (a
     * distinct one would collide in the registry), so it is safe to call on every access instead of caching
     * the result in a field - which also survives reloads cleanly, since a stale field would point at a
     * manager whose cache {@link #reset()} already wiped.
     */
    @SuppressWarnings("unchecked")
    public <K, V> CachingManager<K, V> manager(EntityDescriptor<K, V> descriptor, CacheOptions options) {
        ensureUsable();
        return (CachingManager<K, V>) managers.computeIfAbsent(descriptor.type(),
                type -> refRegistry.manager(descriptor, storage, options));
    }

    /**
     * As {@link #manager(EntityDescriptor, CacheOptions)} but registered in a CALLER-supplied registry -
     * for advanced setups that share one registry across several handles or parent it to a common one.
     * Such a manager is NOT tracked here and is NOT touched by {@link #reset()}: its lifecycle (including
     * cache clearing on reload) is yours.
     */
    public <K, V> CachingManager<K, V> manager(EntityDescriptor<K, V> descriptor, CacheOptions options,
                                               RefRegistry customRegistry) {
        ensureUsable();
        return customRegistry.manager(descriptor, storage, options);
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    /**
     * Flushes every tracked manager's dirty write-back cells to the backend, in parallel. Call it before a
     * reload that will reuse the connection ({@link #openOrReload}) if you must not lose unsaved writes -
     * {@link #reset()} discards dirty state. Managers created on a caller-supplied registry are not tracked
     * here, so flush those yourself.
     */
    public CompletableFuture<Void> flushManagers() {
        List<CompletableFuture<?>> futures = new ArrayList<>(managers.size());
        for (CachingManager<?, ?> manager : managers.values()) {
            futures.add(manager.flushDirty());
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Wipes this handle back to a just-opened state WITHOUT reconnecting: every tracked manager's cache is
     * hard-cleared (dirty write-back cells INCLUDED and DISCARDED - a reload is a fresh open, not a flush),
     * the managers are dropped, and the owned registry is cleared so the caller can re-{@link #manager}
     * the same types. The connection stays live. Used by {@link #openOrReload} on the same-definition path.
     *
     * <p>Call {@link #flushManagers()} first if you need the dirty data persisted.</p>
     */
    public void reset() {
        for (Map.Entry<Class<?>, CachingManager<?, ?>> entry : managers.entrySet()) {
            entry.getValue().clearCache();          // hard clear: store.clear(), dirty cells included
            refRegistry.unregister(entry.getKey()); // only THIS handle's types - never the shared registry wholesale
        }
        managers.clear();
    }

    /** Closes the owned storage and marks this handle unusable; call it when your plugin disables. */
    public CompletableFuture<Void> close() {
        open = false;
        for (Class<?> type : managers.keySet()) {
            refRegistry.unregister(type); // release only this handle's registrations from the (possibly shared) registry
        }
        managers.clear();
        return storage.close();
    }

    /**
     * Guards everything that would hand out NEW wiring. {@link #flushManagers()} and {@link #close()} do
     * NOT go through here: refusing the plugin's only chance to persist what is dirty would turn a
     * resolution bug into data loss.
     */
    private void ensureUsable() {
        if (!open) {
            throw new IllegalStateException("This ECStorage has been closed and cannot be used -"
                    + " open a new one (or use openOrReload).");
        }
    }

    // ---------------------------------------------------------------------
    // Factories - open (plugin-aware: seeds a default under the plugin dataFolder, logs through it)
    // ---------------------------------------------------------------------

    /**
     * Opens from the plugin's inline {@code storage} section, seeding a groupedfile (YAML) default under
     * {@code plugin.dataFolder/StorageData} when the section is empty. The one-line onEnable path.
     */
    public static CompletableFuture<ECStorage> open(ECPluginData plugin, ConfigSection section) {
        return open(plugin, section, defaultSeed(plugin));
    }

    /** As {@link #open(ECPluginData, ConfigSection)}, but seeding {@code seedIfAbsent} (a factory-built default). */
    public static CompletableFuture<ECStorage> open(ECPluginData plugin, ConfigSection section,
                                                    BackendDefinition seedIfAbsent) {
        return open(plugin, section, seedIfAbsent, StorageLogConfig.defaults());
    }

    /** As {@link #open(ECPluginData, ConfigSection, BackendDefinition)}, with an explicit {@link StorageLogConfig}. */
    public static CompletableFuture<ECStorage> open(ECPluginData plugin, ConfigSection section,
                                                    BackendDefinition seedIfAbsent, StorageLogConfig logConfig) {
        BackendDefinition definition;
        try {
            definition = readOrSeed(plugin, section, seedIfAbsent);
        } catch (RuntimeException failure) {
            return failedFuture(failure);
        }
        return openInternal(plugin, definition, logConfig, section.getPath());
    }

    // ---------------------------------------------------------------------
    // Factories - open (plugin-less: advanced / tests; no seeding, core log fallback)
    // ---------------------------------------------------------------------

    /** Opens a handle from a ready {@link BackendDefinition} (default log config, no owning plugin). */
    public static CompletableFuture<ECStorage> open(BackendDefinition definition) {
        return open(definition, StorageLogConfig.defaults());
    }

    /** As {@link #open(BackendDefinition)}, with an explicit {@link StorageLogConfig}. */
    public static CompletableFuture<ECStorage> open(BackendDefinition definition, StorageLogConfig logConfig) {
        return openInternal(null, definition, logConfig, null);
    }

    /**
     * Opens a handle from an inline single-backend section - the shape
     * {@link StorageYamlParser#parseInlineBackend} reads, where the SINGLE child key IS the backend type.
     * The section must already declare a backend (no seeding here - use the {@link ECPluginData} overload
     * for that). Async: the returned future completes once connected, or exceptionally with a
     * {@link StorageConfigException} (wrapped in a {@link CompletionException} on {@code join()}).
     */
    public static CompletableFuture<ECStorage> open(ConfigSection section) {
        return open(section, StorageLogConfig.defaults());
    }

    /** As {@link #open(ConfigSection)}, with an explicit {@link StorageLogConfig}. */
    public static CompletableFuture<ECStorage> open(ConfigSection section, StorageLogConfig logConfig) {
        BackendDefinition definition;
        try {
            definition = readDefinition(null, section);
        } catch (RuntimeException parseFailure) {
            return failedFuture(parseFailure);
        }
        return openInternal(null, definition, logConfig, section.getPath());
    }

    // ---------------------------------------------------------------------
    // Factories - openOrReload (reload-aware)
    // ---------------------------------------------------------------------

    /**
     * Reload entry point (plugin-aware) for when the PLUGIN'S OWN config may have changed: reuse
     * {@code existing} when it is still open and its definition equals the one just parsed, else close
     * it and open fresh. On the reuse path the connection is kept but the handle is {@link #reset()}
     * (all caches wiped, dirty discarded) - so the result is always a clean, just-opened handle.
     * Managers obtained from the OLD handle are dead afterwards; re-derive them from the returned one
     * (see {@link #manager}). Idempotent to pass the SAME handle back:
     * {@code S = openOrReload(plugin, cfg, S)}. Seeds a groupedfile default under the plugin dataFolder
     * when the section is empty.
     *
     * <p>A CORE storage reload needs none of this: the plugin's shared registry is stable in identity,
     * so a handle opened once on enable - and every manager and live {@code Ref} on it - stays valid
     * across core reloads with no callback and no re-open.
     *
     * @param existing the previous handle (may be {@code null} on first open, or already closed)
     */
    public static CompletableFuture<ECStorage> openOrReload(ECPluginData plugin, ConfigSection section,
                                                            ECStorage existing) {
        return openOrReload(plugin, section, defaultSeed(plugin), existing);
    }

    /** As {@link #openOrReload(ECPluginData, ConfigSection, ECStorage)}, seeding {@code seedIfAbsent}. */
    public static CompletableFuture<ECStorage> openOrReload(ECPluginData plugin, ConfigSection section,
                                                            BackendDefinition seedIfAbsent, ECStorage existing) {
        BackendDefinition definition;
        try {
            definition = readOrSeed(plugin, section, seedIfAbsent);
        } catch (RuntimeException failure) {
            return failedFuture(failure);
        }
        return openOrReload(plugin, definition, StorageLogConfig.defaults(), existing);
    }

    /** Plugin-less reload from an inline section (the section must already declare a backend). */
    public static CompletableFuture<ECStorage> openOrReload(ConfigSection section, ECStorage existing) {
        ECPluginData plugin = existing != null ? existing.plugin : null;
        BackendDefinition definition;
        try {
            definition = readDefinition(plugin, section);
        } catch (RuntimeException parseFailure) {
            return failedFuture(parseFailure);
        }
        return openOrReload(plugin, definition, StorageLogConfig.defaults(), existing);
    }

    /** Plugin-less reload from a ready {@link BackendDefinition} (keeps {@code existing}'s owning plugin). */
    public static CompletableFuture<ECStorage> openOrReload(BackendDefinition definition, ECStorage existing) {
        ECPluginData plugin = existing != null ? existing.plugin : null;
        return openOrReload(plugin, definition, StorageLogConfig.defaults(), existing);
    }


    /**
     * A handle onto the network backend for {@code plugin}. Opens no connection and reads no config of
     * its own: the backend is already up, chosen by the admin under {@code network.storage-backend-id}.
     *
     * @throws StorageUnavailableException when the PlayerController has not bootstrapped yet
     */
    public static ECNetworkStorage network(ECPluginData plugin) {
        return ECNetworkStorage.of(plugin);
    }

    private static CompletableFuture<ECStorage> openOrReload(ECPluginData plugin, BackendDefinition definition,
                                                             StorageLogConfig logConfig, ECStorage existing) {
        // same target on the right registry: keep the live connection, just wipe the caches. The shared
        // registry is identity-stable across core reloads, so the only way the registry can differ is a
        // handle opened BEFORE the core bootstrapped (it fell back to a private one) - reopening is what
        // heals it onto the shared registry, and that anomaly is the one case worth a reconnect.
        if (existing != null && existing.isOpen() && existing.definition.equals(definition)
                && resolveRegistry(plugin, existing.refRegistry) == existing.refRegistry) {
            existing.reset();
            return CompletableFuture.completedFuture(existing);
        }
        // different target, a dead handle, or a pre-bootstrap private registry: drop the old, open fresh
        if (existing != null && existing.isOpen()) {
            return existing.close().thenCompose(ignored -> openInternal(plugin, definition, logConfig, null));
        }
        return openInternal(plugin, definition, logConfig, null);
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /**
     * Creates the storage, connects it, and wraps a failure as a {@link StorageConfigException} - fully
     * async: no {@code join()} on the caller's thread. A failed open closes the half-built storage
     * best-effort so no pool/handle is orphaned, then surfaces the original cause.
     */
    private static CompletableFuture<ECStorage> openInternal(ECPluginData plugin, BackendDefinition definition,
                                                             StorageLogConfig logConfig, String originPath) {
        final Storage storage;
        try {
            storage = definition.createStorage(logConfig); // may fail-fast synchronously (e.g. sql on Hytale)
        } catch (RuntimeException createFailure) {
            return failedFuture(createFailure);
        }
        // the plugin's shared registry when available, else a fresh private one (plugin-less / bootstrap not up)
        RefRegistry registry = resolveRegistry(plugin, new RefRegistry());
        warnIfReloadUnsafe(plugin, registry);
        return storage.init().handle((ignored, initFailure) -> {
            if (initFailure == null) {
                return new ECStorage(plugin, storage, definition, registry);
            }
            try {
                storage.close().join();
            } catch (RuntimeException ignoredTeardown) {
                // best-effort teardown; the init failure below is the error that matters
            }
            Throwable cause = unwrap(initFailure);
            throw new CompletionException(new StorageConfigException("Failed to open the '"
                    + definition.getType().getId() + "' storage backend"
                    + (originPath != null ? " declared at '" + originPath + "'" : "") + "!", cause));
        });
    }

    /**
     * The registry this handle registers its managers in: the plugin's SHARED child registry when available
     * (so a Ref in one of the plugin's PDSections resolves an entity opened here), else {@code fallback}.
     */
    private static RefRegistry resolveRegistry(ECPluginData plugin, RefRegistry fallback) {
        RefRegistry shared = ECStorageRegistries.of(plugin);
        return shared != null ? shared : fallback;
    }

    /** The groupedfile (YAML) default a plugin-aware open seeds when its section is empty. */
    private static BackendDefinition defaultSeed(ECPluginData plugin) {
        String base = plugin.getMetaInfo().getDataFolder().toString() + "/StorageData";
        return BackendDefinition.groupedFile(base, null); // null format = YAML
    }

    /**
     * Reads the inline backend, first seeding {@code seedIfAbsent} (and saving) when the section declares
     * none - so a plugin's onEnable is a single call even on a fresh install.
     */
    private static BackendDefinition readOrSeed(ECPluginData plugin, ConfigSection section,
                                                BackendDefinition seedIfAbsent) {
        if (seedIfAbsent != null && section.getKeys().isEmpty()) {
            StorageYamlDefaults.writeInlineBackendTemplate(section, seedIfAbsent, false);
            section.getConfig().save();
        }
        return readDefinition(plugin, section);
    }

    /** Parses the inline backend and logs any soft warnings through the plugin (or the core). */
    private static BackendDefinition readDefinition(ECPluginData plugin, ConfigSection section) {
        List<String> warnings = new ArrayList<>();
        BackendDefinition definition = StorageYamlParser.parseInlineBackend(section, warnings);
        for (String warning : warnings) {
            logWarning(plugin, warning);
        }
        return definition;
    }

    private static CompletableFuture<ECStorage> failedFuture(Throwable cause) {
        CompletableFuture<ECStorage> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

    private static Throwable unwrap(Throwable throwable) {
        return (throwable instanceof CompletionException && throwable.getCause() != null)
                ? throwable.getCause() : throwable;
    }

    /**
     * Warns, at open time, when the handle could not get the plugin's shared registry and fell back to
     * a private one: a {@code Ref} between this storage and the plugin's PDSections will never resolve.
     * Cheap to fix while the developer is writing the code (open later, after the core bootstraps) and
     * expensive to notice in production - the symptom is a {@code Ref} quietly resolving to nothing.
     *
     * <p>Always a warning, never a refusal: refusing an open over initialization ORDER would be hostile.</p>
     */
    private static void warnIfReloadUnsafe(ECPluginData plugin, RefRegistry registry) {
        if (plugin == null) {
            return; // a plugin-less open owns a private registry by contract, not by accident
        }
        if (ECStorageRegistries.of(plugin) != registry) {
            logWarning(plugin, "This plugin opened an ECStorage before the PlayerData layer was up, so it"
                    + " got a PRIVATE RefRegistry: a Ref between this storage and the plugin's PDSections"
                    + " will not resolve. Open it once the core is ready - a plugin's enable always runs"
                    + " late enough (EverNifeCore is a dependency, so it bootstraps first).");
        }
    }

    private static void logWarning(ECPluginData plugin, String message) {
        if (plugin != null) {
            plugin.getLog().warning(message);
            return;
        }
        try {
            EverNifeCore.getLog().warning(message);
        } catch (Throwable noPluginRuntime) {
            //pure JUnit runtime (no ECPluginData/log configured): falls back to JUL
            Logger.getLogger("EverNifeCore").log(Level.WARNING, message);
        }
    }
}
