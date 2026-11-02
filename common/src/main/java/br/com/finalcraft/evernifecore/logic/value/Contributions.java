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
    private final RowWriter<Row> writer = new RowWriter<>(Row::copy, this::save, this::writeFailed);

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

    /**
     * {@code owner}'s contributions kept in {@code storage}; reopening with the same storage returns the same instance,
     * and with another one waits for the writes of the instance it replaces before loading.
     */
    public static Contributions of(ECPluginData owner, ECStorage storage) {
        return open(owner, storage);
    }

    /** {@code owner}'s open contributions, or ones kept in memory only when none are open. */
    public static Contributions of(ECPluginData owner) {
        Contributions existing = OWNED.get(owner.getMetaInfo().getName());
        return existing != null ? existing : open(owner, null);
    }

    private static Contributions open(ECPluginData owner, @Nullable ECStorage storage) {
        return OWNED.compute(owner.getMetaInfo().getName(), (name, existing) -> {
            if (existing != null && existing.storage == storage) {
                return existing;
            }
            if (existing != null) {
                existing.awaitWrites(); //the new instance may load what the old one is still writing
            }
            return new Contributions(owner, storage);
        });
    }

    private void awaitWrites() {
        writer.drained().join();
    }

    /** Awaits the writes still on their way and forgets {@code owner}'s contributions; called on its shutdown. */
    public static void closeOwnedBy(ECPluginData owner) {
        Contributions closed = OWNED.remove(owner.getMetaInfo().getName());
        if (closed != null) {
            closed.awaitWrites();
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

    /** Stores {@code row} as it is now, once the write of its key on its way lands; call it after the change, outside its monitor. */
    void write(Row row) {
        if (manager == null) {
            return;
        }
        writer.write(row.key, row);
    }

    private CompletableFuture<?> save(Row snapshot) {
        return manager.saveAndCache(snapshot);
    }

    private void writeFailed(Throwable error) {
        EverNifeCore.getLog().warning("A contribution of " + plugin.getMetaInfo().getName() + " was not written: " + error);
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

        Row copy() {
            Row copy = new Row(key);
            copy.total = total;
            copy.members = new LinkedHashMap<>(members);
            copy.complete = complete;
            return copy;
        }
    }
}
