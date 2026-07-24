package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.everydatabase.Storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps logical backend names (storage.yml) to live EveryDatabase
 * {@link Storage} instances. Only ENABLED backends are registered.
 *
 * <p>Also tracks collection-name claims per backend: EveryDatabase caches
 * repositories BY COLLECTION NAME, so two different descriptors using the same
 * collection on the same Storage would silently share the first repository -
 * {@link #claimCollection} turns that mistake into a deterministic hard error.</p>
 *
 * <p>No init timeout here: each backend's connect timeout (HikariCP pool /
 * Mongo connect timeout) already bounds {@code initAll()}.</p>
 */
public final class StorageRegistry {

    // Insertion-ordered on purpose: getNames() and the get() error list are admin-facing. Mutation
    // (register) is boot-only and single-threaded; readers see the fully-built map through the
    // happens-before of the volatile ECStorage.registry publish, so a non-concurrent map is safe here.
    private final Map<String, Storage> storages = new LinkedHashMap<>();
    // same keys as storages; absent when a backend was registered without a definition (tests)
    private final Map<String, BackendDefinition> definitions = new LinkedHashMap<>();
    private final String defaultBackendName;

    /** backendName -> (collection -> owner) */
    private final Map<String, Map<String, String>> claimedCollections = new ConcurrentHashMap<>();

    public StorageRegistry(String defaultBackendName) {
        this.defaultBackendName = defaultBackendName;
    }

    public void register(String name, Storage storage) {
        if (storages.containsKey(name)) {
            throw new StorageConfigException("Backend '" + name + "' is already registered!");
        }
        storages.put(name, storage);
    }

    /** Registers a backend along with the definition it was built from (its type and target for the boot report). */
    public void register(String name, Storage storage, BackendDefinition definition) {
        register(name, storage);
        definitions.put(name, definition);
    }

    /** The definition a backend was opened from, or {@code null} when registered without one (tests). */
    public BackendDefinition getDefinition(String name) {
        return definitions.get(name);
    }

    /**
     * @throws StorageConfigException if the backend is not registered (not
     *                                declared or disabled in storage.yml)
     */
    public Storage get(String name) {
        Storage storage = storages.get(name);
        if (storage == null) {
            throw new StorageConfigException("Backend '" + name + "' is not available!"
                    + " Either it is not declared under 'storage-backends:' in storage.yml or it is"
                    + " disabled (enabled: false). Available backends: " + storages.keySet());
        }
        return storage;
    }

    public Optional<Storage> tryGet(String name) {
        return Optional.ofNullable(storages.get(name));
    }

    public Set<String> getNames() {
        return Collections.unmodifiableSet(storages.keySet());
    }

    public String getDefaultBackendName() {
        return defaultBackendName;
    }

    public Storage getDefaultBackend() {
        return get(defaultBackendName);
    }

    /**
     * Claims a collection name on a backend for the given owner.
     *
     * @return true if the claim succeeded (or the same owner re-claims);
     *         false if another owner already holds that collection on this backend
     */
    public boolean claimCollection(String backendName, String collection, String owner) {
        Map<String, String> ofBackend = claimedCollections
                .computeIfAbsent(backendName, k -> new ConcurrentHashMap<>());
        String existingOwner = ofBackend.putIfAbsent(collection, owner);
        return existingOwner == null || existingOwner.equals(owner);
    }

    /** @return the owner of a claimed collection, or null when not claimed. */
    public String getCollectionOwner(String backendName, String collection) {
        Map<String, String> ofBackend = claimedCollections.get(backendName);
        return ofBackend == null ? null : ofBackend.get(collection);
    }

    /**
     * Releases a collection claim (a plugin's PDSections being unregistered at runtime), so a
     * later re-registration - e.g. the plugin re-enabled with a fresh classloader - can claim it
     * again instead of colliding with the stale owner.
     */
    public void releaseCollection(String backendName, String collection) {
        Map<String, String> ofBackend = claimedCollections.get(backendName);
        if (ofBackend != null) {
            ofBackend.remove(collection);
        }
    }

    /**
     * Calls {@code init()} on each registered Storage. Every backend is attempted; the returned
     * future completes exceptionally with a {@link StorageUnavailableException} listing ALL the ones
     * that failed - reporting only the first would send the admin through one reboot per broken
     * database.
     */
    public CompletableFuture<Void> initAll() {
        Map<String, CompletableFuture<Void>> started = new LinkedHashMap<>();
        for (Map.Entry<String, Storage> entry : storages.entrySet()) {
            started.put(entry.getKey(), entry.getValue().init());
        }
        return CompletableFuture.allOf(started.values().toArray(new CompletableFuture[0]))
                .handle((ignored, anyFailure) -> {
                    if (anyFailure == null) {
                        return null;
                    }
                    List<StorageInitFailure> failures = new ArrayList<>();
                    for (Map.Entry<String, CompletableFuture<Void>> entry : started.entrySet()) {
                        try {
                            entry.getValue().join();
                        } catch (Throwable failed) {
                            BackendDefinition definition = definitions.get(entry.getKey());
                            failures.add(new StorageInitFailure(
                                    entry.getKey(),
                                    definition != null ? definition.getType() : null,
                                    definition != null ? definition.describeTarget() : "(unknown target)",
                                    unwrap(failed)));
                        }
                    }
                    // no ParsedStorageConfig down here: the usages/file context is attached later by enrich()
                    throw new StorageUnavailableException(summarize(failures, storages.size()),
                            failures, Collections.emptyMap(), null);
                });
    }

    /** One line, naming every failed backend - the platform log shows THIS, not the banner. */
    private static String summarize(List<StorageInitFailure> failures, int total) {
        StringBuilder sb = new StringBuilder("Failed to initialize ")
                .append(failures.size()).append(" of ").append(total).append(" storage backend(s): ");
        for (int i = 0; i < failures.size(); i++) {
            StorageInitFailure failure = failures.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("backend '").append(failure.getBackendName()).append('\'')
                    .append(" -> ").append(failure.getRootCauseSummary());
        }
        return sb.toString();
    }

    private static Throwable unwrap(Throwable throwable) {
        return (throwable instanceof CompletionException && throwable.getCause() != null)
                ? throwable.getCause() : throwable;
    }

    /** Closes each registered Storage (best-effort: tries all even if one fails). */
    public CompletableFuture<Void> closeAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>(storages.size());
        for (Storage storage : storages.values()) {
            futures.add(storage.close().exceptionally(error -> null));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}
