package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.everydatabase.Storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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

    private final Map<String, Storage> storages = new LinkedHashMap<>();
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
     * Calls {@code init()} on each registered Storage. Fail-fast: the returned future
     * completes exceptionally if ANY backend fails to initialize.
     */
    public CompletableFuture<Void> initAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>(storages.size());
        for (Map.Entry<String, Storage> entry : storages.entrySet()) {
            futures.add(entry.getValue().init().exceptionally(error -> {
                throw new StorageConfigException("Failed to initialize backend '"
                        + entry.getKey() + "'!", error);
            }));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
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
