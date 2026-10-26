package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A plugin's {@link Contribution}s - a clan's warehouse filling up, a town's donation goal - kept in the plugin's
 * storage (collection {@value #COLLECTION}, key {@code <id>@<target>}) or in memory only. One per plugin. Any thread.
 */
public final class Contributions {

    public static final String COLLECTION = "ec_contributions";

    private static final Map<String, Contributions> OWNED = new ConcurrentHashMap<>();

    private final ECPluginData plugin;
    private final @Nullable ECStorage storage;
    private final @Nullable CachingManager<String, Row> manager;
    final Map<String, Row> rows = new ConcurrentHashMap<>();
    private final Map<String, Contribution<?>> defined = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> pendingWrites = ConcurrentHashMap.newKeySet();

    private Contributions(ECPluginData plugin, @Nullable ECStorage storage) {
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
        manager.preloadAll().join(); //a deposit runs on the player's thread: the set is warmed once, here
        for (Row row : manager.cachedValues()) {
            rows.put(row.key, row);
        }
    }

    /** {@code owner}'s contributions kept in {@code storage}; reopening with the same storage returns the same instance. */
    public static Contributions of(ECPluginData owner, ECStorage storage) {
        return open(owner, storage);
    }

    /** {@code owner}'s contributions kept in memory only. */
    public static Contributions of(ECPluginData owner) {
        Contributions existing = OWNED.get(owner.getMetaInfo().getName());
        return existing != null ? existing : open(owner, null);
    }

    private static Contributions open(ECPluginData owner, @Nullable ECStorage storage) {
        return OWNED.compute(owner.getMetaInfo().getName(), (name, existing) ->
                existing != null && existing.storage == storage ? existing : new Contributions(owner, storage));
    }

    /** Awaits the writes still on their way and forgets {@code owner}'s contributions; called on its shutdown. */
    public static void closeOwnedBy(ECPluginData owner) {
        Contributions closed = OWNED.remove(owner.getMetaInfo().getName());
        if (closed != null) {
            CompletableFuture.allOf(closed.pendingWrites.toArray(new CompletableFuture[0])).exceptionally(error -> null).join();
        }
    }

    /**
     * The contribution {@code id} toward {@code target} (a group, a region, the server) collecting {@code requirement};
     * defining it again replaces the requirement and keeps what was deposited.
     */
    public <T> Contribution<T> define(String id, ProgressOwner target, ContributionRequirement<T> requirement) {
        String key = RegionId.requireName("contribution", id) + "@" + target;
        Contribution<T> contribution = new Contribution<>(this, key, id, target, requirement);
        defined.put(key, contribution);
        return contribution;
    }

    public ECPluginData getPlugin() {
        return plugin;
    }

    void write(Row row) {
        if (manager == null) {
            return;
        }
        CompletableFuture<?> write = manager.saveAndCache(row);
        pendingWrites.add(write);
        write.whenComplete((ok, error) -> {
            pendingWrites.remove(write);
            if (error != null) {
                EverNifeCore.getLog().warning("A contribution of " + plugin.getMetaInfo().getName() + " was not written: " + error);
            }
        });
    }

    /** Internal: what one contribution collected, in total and by member. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Row {
        String key;
        String total = "0";
        Map<String, String> members = new LinkedHashMap<>();
        boolean complete;

        Row() {
            //Jackson no-arg constructor
        }

        Row(String key) {
            this.key = key;
        }
    }
}
