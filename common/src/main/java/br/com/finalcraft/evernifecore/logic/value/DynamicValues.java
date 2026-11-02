package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Variable;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A plugin's {@link DynamicValue}s - a price that rises with each sale and drifts back, a pay rate - one state per
 * {@link br.com.finalcraft.evernifecore.logic.ProgressOwner}, kept in the plugin's storage (collection
 * {@value #COLLECTION}, key {@code <id>@<owner>}) or in memory only. One per plugin. Any thread.
 */
public final class DynamicValues {

    public static final String COLLECTION = "ec_dynamic_values";

    private static final Map<String, DynamicValues> OWNED = new ConcurrentHashMap<>();

    private final ECPluginData plugin;
    private final @Nullable ECStorage storage;
    private final @Nullable CachingManager<String, Row> manager;
    final Map<String, Row> rows = new ConcurrentHashMap<>();
    private final Map<String, DynamicValue> defined = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> pendingWrites = ConcurrentHashMap.newKeySet();

    private DynamicValues(ECPluginData plugin, @Nullable ECStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        if (storage == null) {
            this.manager = null;
            return;
        }
        EntityDescriptor<String, Row> descriptor = EntityDescriptor.builder(String.class, Row.class)
                .collection(COLLECTION)
                .keyExtractor(row -> row.key)
                .codec(storage.defaultCodec(Row.class))
                .build();
        this.manager = storage.manager(descriptor, CachePolicy.always());
        manager.preloadAll().join(); //a value is read on the caller's thread: the set is warmed once, here
        for (Row row : manager.cachedValues()) {
            rows.put(row.key, row);
        }
    }

    /**
     * {@code owner}'s values kept in {@code storage}; reopening with the same storage returns the same instance, and
     * with another one waits for the writes of the instance it replaces before loading.
     */
    public static DynamicValues of(ECPluginData owner, ECStorage storage) {
        return open(owner, storage);
    }

    /** {@code owner}'s open values, or ones kept in memory only when none are open. */
    public static DynamicValues of(ECPluginData owner) {
        DynamicValues existing = OWNED.get(owner.getMetaInfo().getName());
        return existing != null ? existing : open(owner, null);
    }

    private static DynamicValues open(ECPluginData owner, @Nullable ECStorage storage) {
        return OWNED.compute(owner.getMetaInfo().getName(), (name, existing) -> {
            if (existing != null && existing.storage == storage) {
                return existing;
            }
            if (existing != null) {
                existing.awaitWrites(); //the new instance may load what the old one is still writing
            }
            return new DynamicValues(owner, storage);
        });
    }

    private void awaitWrites() {
        CompletableFuture.allOf(pendingWrites.toArray(new CompletableFuture[0])).exceptionally(error -> null).join();
    }

    /** Awaits the writes still on their way and forgets {@code owner}'s values; called on its shutdown. */
    public static void closeOwnedBy(ECPluginData owner) {
        DynamicValues closed = OWNED.remove(owner.getMetaInfo().getName());
        if (closed != null) {
            closed.awaitWrites();
        }
    }

    /**
     * Defines the value {@code id} ({@code name}, or {@code <plugin>:name}) and registers it as the variable of that id.
     * Defining it again with another spec replaces the rule and keeps every owner's state.
     *
     * @throws IllegalArgumentException when the id names another plugin
     */
    public DynamicValue define(String id, DynamicValueSpec spec) {
        String pluginName = RegionId.requireOwner(plugin.getMetaInfo().getName());
        int colon = id.indexOf(':');
        if (colon >= 0 && !id.substring(0, colon).toLowerCase(Locale.ROOT).equals(pluginName)) {
            throw new IllegalArgumentException("The dynamic value '" + id + "' names another plugin: " + plugin.getMetaInfo().getName()
                    + " defines its own values, written 'name' or '" + pluginName + ":name'.");
        }
        String fullId = pluginName + ":" + RegionId.requireName("dynamic value", colon >= 0 ? id.substring(colon + 1) : id);
        DynamicValue value = defined.compute(fullId, (key, existing) -> {
            if (existing == null) {
                return new DynamicValue(this, key, spec);
            }
            existing.setSpec(spec);
            return existing;
        });
        if (!Variables.registry().find(fullId).isPresent()) {
            Variables.registry().register(plugin, TypeDescriptor.<Variable<?>>of(fullId)
                    .describe("A dynamic value of " + plugin.getMetaInfo().getName() + ", one per owner.")
                    .factory(params -> defined.get(fullId))
                    .build());
        }
        return value;
    }

    /** The value defined under {@code id} ({@code <plugin>:name} or the bare name). */
    public Optional<DynamicValue> find(String id) {
        String key = id.indexOf(':') < 0 ? plugin.getMetaInfo().getName().toLowerCase(Locale.ROOT) + ":" + id : id.toLowerCase(Locale.ROOT);
        return Optional.ofNullable(defined.get(key));
    }

    public ECPluginData getPlugin() {
        return plugin;
    }

    /** Stores {@code snapshot}, a {@link Row#copy()} the caller no longer touches: the backend serializes it on its own thread. */
    void write(Row snapshot) {
        if (manager == null) {
            return;
        }
        CompletableFuture<?> write = manager.saveAndCache(snapshot);
        pendingWrites.add(write);
        write.whenComplete((ok, error) -> {
            pendingWrites.remove(write);
            if (error != null) {
                EverNifeCore.getLog().warning("A dynamic value of " + plugin.getMetaInfo().getName() + " was not written: " + error);
            }
        });
    }

    /** Internal: one owner's state of one value - the value and the instant the decay counts from. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Row {
        String key;
        @Nullable String value;
        long at;

        Row() {
            //Jackson no-arg constructor
        }

        Row(String key) {
            this.key = key;
        }

        Row copy() {
            Row copy = new Row(key);
            copy.value = value;
            copy.at = at;
            return copy;
        }
    }
}
