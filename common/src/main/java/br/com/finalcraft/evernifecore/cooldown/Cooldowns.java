package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.cooldown.server.ServerCooldowns;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Where a counter lives, by its owner. A player's goes to the player's {@code PlayerCooldownsLocal} or
 * {@code PlayerCooldownsNetwork} row by the policy's reach; the server's to the {@code Cooldowns.yml} of its plugin's
 * folder (local) or
 * {@code ServerCooldowns} (network); a region's, a world's or a group's to the {@code ec_cooldowns} collection of the
 * caller plugin's own storage, opened with {@link #of(ECPluginData, ECStorage)}.
 *
 * <p>A player's and the server's counters are named {@code <plugin>#<counterId>} ({@link #keyOf}), so two plugins never
 * share one; an owned row is keyed {@code <owner>|<counterId>} in its plugin's storage. The owned rows are pruned by
 * retention once a minute (no login prunes them on read, as it does a player's row).</p>
 */
public final class Cooldowns {

    /** The collection the owned counters live in, in their plugin's storage. */
    public static final String COLLECTION = "ec_cooldowns";

    static final Duration SWEEP_INTERVAL = Duration.ofSeconds(60);

    private static final Map<String, Cooldowns> OWNED = new ConcurrentHashMap<>();
    //the running sweep's generation, 0 while none runs; a sweep of an older generation stops instead of rescheduling
    private static final AtomicInteger SWEEP = new AtomicInteger();
    private static final AtomicInteger GENERATIONS = new AtomicInteger();

    private final ECPluginData plugin;
    private final @Nullable ECStorage storage;
    private final @Nullable CachingManager<String, Row> manager;
    private final Map<String, Row> rows = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> pendingWrites = ConcurrentHashMap.newKeySet();

    private Cooldowns(ECPluginData plugin, @Nullable ECStorage storage) {
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
        manager.preloadAll().join(); //a counter is read on the caller's thread: the set is warmed once, here
        for (Row row : manager.cachedValues()) {
            rows.put(row.key, row);
        }
    }

    /** The counters of region, world and group owners of {@code owner}, kept in {@code storage}; one per plugin. */
    public static Cooldowns of(ECPluginData owner, ECStorage storage) {
        Cooldowns opened = OWNED.compute(owner.getMetaInfo().getName(), (name, existing) ->
                existing != null && existing.storage == storage ? existing : new Cooldowns(owner, storage));
        int generation = GENERATIONS.incrementAndGet();
        if (SWEEP.compareAndSet(0, generation)) {
            ECScheduler.scheduleAsync(() -> sweep(generation), SWEEP_INTERVAL.toMillis());
        }
        return opened;
    }

    /** The counters of {@code owner} kept in memory only: a persistent policy is refused with what to open instead. */
    public static Cooldowns of(ECPluginData owner) {
        return OWNED.computeIfAbsent(owner.getMetaInfo().getName(), name -> new Cooldowns(owner, null));
    }

    /**
     * Awaits the writes still on their way and forgets {@code owner}'s counters, its server cooldowns and its retention;
     * called on its shutdown.
     */
    public static void closeOwnedBy(ECPluginData owner) {
        closeServerFile(owner);
        CooldownRetention.forget(owner);
        Cooldowns closed = OWNED.remove(owner.getMetaInfo().getName());
        if (closed != null) {
            CompletableFuture.allOf(closed.pendingWrites.toArray(new CompletableFuture[0])).exceptionally(error -> null).join();
        }
        if (OWNED.values().stream().noneMatch(cooldowns -> cooldowns.manager != null)) {
            SWEEP.set(0);
        }
    }

    /**
     * The counter {@code id} of {@code owner} under {@code policy}, for the plugin of {@code caller}. A player's call
     * {@code .join()}s the player's row - a cache hit while the player is online.
     */
    public static Cooldown resolve(Caller caller, ProgressOwner owner, String id, RepeatPolicy policy) {
        ECPluginData plugin = caller.getPlugin();
        boolean network = policy.getReach() == RepeatPolicy.Reach.NETWORK;
        if (owner.isKind(ProgressOwner.PLAYER)) {
            UUID player = UUID.fromString(owner.getId());
            return (network ? Cooldown.network(plugin, player, id, policy) : Cooldown.of(plugin, player, id, policy)).join();
        }
        if (owner.isKind(ProgressOwner.GLOBAL)) {
            ServerCooldowns store = network ? ServerCooldowns.get() : null;
            return store != null ? store.resolve(keyOf(plugin, id), policy) : server(plugin, id).withPolicy(policy);
        }
        Cooldowns cooldowns = OWNED.get(caller.getPlugin().getMetaInfo().getName());
        return (cooldowns != null ? cooldowns : of(caller.getPlugin())).resolve(owner, id, policy);
    }

    /** The counter {@code id} of {@code owner} under {@code policy}; a player or the server goes to its own route. */
    public Cooldown resolve(ProgressOwner owner, String id, RepeatPolicy policy) {
        if (owner.isKind(ProgressOwner.PLAYER) || owner.isKind(ProgressOwner.GLOBAL)) {
            return resolve(Caller.of(plugin, "cooldowns"), owner, id, policy);
        }
        if (manager == null && policy.isPersistent()) {
            throw new IllegalStateException("The counter '" + id + "' of " + owner + " is persistent, but " + plugin.getMetaInfo().getName()
                    + " keeps its counters in memory only. Open them in its storage first - Cooldowns.of(plugin, storage) - or"
                    + " make the policy .persistent(false).");
        }
        String key = owner + "|" + id;
        Row row = rows.computeIfAbsent(key, Row::new);
        return new Cooldown(key, row.entry, policy, manager == null ? null : new Cooldown.Route() {
            @Override
            public void store(Cooldown cooldown) {
                track(cooldown.isPersistent() ? manager.saveAndCache(row) : manager.deleteAndEvict(key));
            }
        });
    }

    /** The name a player's or the server's counter {@code id} of {@code owner} is stored under: {@code <plugin>#<id>}. */
    public static String keyOf(ECPluginData owner, String id) {
        return owner.getMetaInfo().getName() + "#" + Objects.requireNonNull(id, "id");
    }

    /**
     * The plugin that owns the counter stored under {@code key} ({@code <plugin>#<id>}, plugin name in any case), or
     * null when the key names no loaded plugin.
     */
    public static @Nullable ECPluginData ownerOf(String key) {
        int hash = key.indexOf('#');
        if (hash <= 0) {
            return null;
        }
        String name = key.substring(0, hash);
        for (ECPluginData plugin : ECPluginManager.getECPluginsMap().values()) {
            if (plugin.getMetaInfo().getName().equalsIgnoreCase(name)) {
                return plugin;
            }
        }
        return null;
    }

    /** The plugin these counters belong to. */
    public ECPluginData getPlugin() {
        return plugin;
    }

    private void track(CompletableFuture<?> write) {
        pendingWrites.add(write);
        write.whenComplete((ok, error) -> {
            pendingWrites.remove(write);
            if (error != null) {
                EverNifeCore.getLog().warning("A cooldown of " + plugin.getMetaInfo().getName() + " was not written: " + error);
            }
        });
    }

    /** Drops every owned row past its retention horizon; how many went away. */
    private int pruneExpired(long now) {
        int removed = 0;
        for (Iterator<Row> it = rows.values().iterator(); it.hasNext(); ) {
            Row row = it.next();
            //the key is <owner>|<id>, and an owner (kind:id) holds no '|'
            String id = row.key.substring(row.key.indexOf('|') + 1);
            if (CooldownRetention.isExpired(row.entry, now, CooldownRetention.retentionMillisFor(plugin.getMetaInfo().getName(), id))) {
                it.remove();
                removed++;
                if (manager != null) {
                    track(manager.deleteAndEvict(row.key));
                }
            }
        }
        return removed;
    }

    static void sweep(int generation) {
        if (SWEEP.get() != generation) {
            return;
        }
        long now = ECClock.now().toEpochMilli();
        boolean stored = false;
        for (Cooldowns cooldowns : OWNED.values()) {
            if (cooldowns.manager != null) {
                stored = true;
                cooldowns.pruneExpired(now);
            }
        }
        if (stored) {
            ECScheduler.scheduleAsync(() -> sweep(generation), SWEEP_INTERVAL.toMillis());
        } else {
            SWEEP.compareAndSet(generation, 0);
        }
    }

    /** Internal: one owned counter as its plugin's storage keeps it. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Row {
        String key;
        CooldownEntry entry = new CooldownEntry();

        Row() {
            //Jackson no-arg constructor
        }

        Row(String key) {
            this.key = key;
        }
    }

    // ------------------------------------------------------------------
    // The server's cooldowns: Cooldowns.yml in each owner's folder
    // ------------------------------------------------------------------

    /** The file the server's local cooldowns of a plugin are kept in, in that plugin's folder. */
    public static final String SERVER_FILE_NAME = "Cooldowns.yml";

    private static final Map<String, Cooldown> SERVER_COOLDOWNS = Collections.synchronizedMap(new LinkedHashMap<>());
    //owner name -> its open file; a file is opened on the owner's first server cooldown, or by a reload
    private static final Map<String, Config> SERVER_FILES = new ConcurrentHashMap<>();

    private static Cooldown.Route serverRoute(String ownerName) {
        return cooldown -> {
            Config file = SERVER_FILES.get(ownerName);
            if (file != null) { //null once the owner left: its handles no longer reach a file
                file.setValue("AllCooldowns." + cooldown.getIdentifier(), cooldown.isPersistent() ? cooldown : null);
                file.saveAsync();
            }
        };
    }

    private static Config serverFile(ECPluginData owner) {
        return SERVER_FILES.computeIfAbsent(owner.getMetaInfo().getName(), name -> {
            Config config = ConfigFactory.open(owner, SERVER_FILE_NAME);
            Cooldown.Route route = serverRoute(name);
            for (String key : config.getKeys("AllCooldowns")) {
                try {
                    Cooldown read = config.getValue("AllCooldowns." + key, Cooldown.class);
                    SERVER_COOLDOWNS.put(read.getIdentifier(), new Cooldown(read.getIdentifier(), read.getEntry(), RepeatPolicy.unlimited(), route));
                } catch (Exception e) {
                    EverNifeCore.getLog().warning("Failed to load the cooldown [{}] from the {} of {}", key, SERVER_FILE_NAME, name, e);
                }
            }
            return config;
        });
    }

    private static void closeServerFile(ECPluginData owner) {
        String name = owner.getMetaInfo().getName();
        if (SERVER_FILES.remove(name) != null) {
            String prefix = name + "#";
            SERVER_COOLDOWNS.keySet().removeIf(key -> key.startsWith(prefix));
        }
    }

    /**
     * Reads again the {@code Cooldowns.yml} of every loaded plugin that has one; what {@code /eccooldown reload} and the
     * core's reload run.
     */
    public static void reloadServerFiles() {
        for (ECPluginData plugin : ECPluginManager.getECPluginsMap().values()) {
            closeServerFile(plugin);
        }
        openServerFiles();
    }

    /** Opens the {@code Cooldowns.yml} of every loaded plugin that has one and is not open yet; what a listing runs first. */
    public static void openServerFiles() {
        for (ECPluginData plugin : ECPluginManager.getECPluginsMap().values()) {
            if (new File(plugin.getMetaInfo().getDataFolder(), SERVER_FILE_NAME).exists()) {
                serverFile(plugin);
            }
        }
    }

    /** The server's local cooldowns of every open file, by {@code <plugin>#<id>}: the ones read and every one asked for since. */
    public static Map<String, Cooldown> getMapOfCooldowns() {
        return SERVER_COOLDOWNS;
    }

    /** The server's local cooldown {@code id} of {@code owner}; the handle behind {@link Cooldown#of(ECPluginData, String)}. */
    static Cooldown server(ECPluginData owner, String id) {
        serverFile(owner);
        return SERVER_COOLDOWNS.computeIfAbsent(keyOf(owner, id),
                key -> new Cooldown(key, new CooldownEntry(), RepeatPolicy.unlimited(), serverRoute(owner.getMetaInfo().getName())));
    }

    /** A server cooldown's form in {@code Cooldowns.yml}: identifier, start, duration, mutation clock, uses and window. */
    public static Map<String, Object> toConfigMap(Cooldown cooldown) {
        CooldownEntry entry = cooldown.getEntry();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("identifier", cooldown.getIdentifier());
        map.put("timeStart", entry.getTimeStart());
        map.put("timeDuration", entry.getTimeDuration());
        map.put("updatedAt", entry.getUpdatedAt());
        if (entry.getUses() != 0 || entry.getWindowStart() != 0) {
            map.put("uses", entry.getUses());
            map.put("windowStart", entry.getWindowStart());
            map.put("windowEnd", entry.getWindowEnd());
        }
        return map;
    }

    /**
     * Reads the form {@link #toConfigMap} writes into a persistent cooldown routed nowhere: the file that read it routes
     * it back to itself. A file
     * written before the mutation clock existed takes the start for it; one written before uses existed reads 0.
     */
    public static Cooldown fromConfigMap(Map<String, Object> map) {
        long timeStart = asLong(map.get("timeStart"));
        Object rawUpdatedAt = map.get("updatedAt");
        long updatedAt = rawUpdatedAt == null ? timeStart : asLong(rawUpdatedAt);
        CooldownEntry entry = new CooldownEntry(timeStart, asLong(map.get("timeDuration")), updatedAt, true,
                (int) asLong(map.get("uses")), asLong(map.get("windowStart")), asLong(map.get("windowEnd")));
        return new Cooldown(String.valueOf(map.get("identifier")), entry, RepeatPolicy.unlimited(), null);
    }

    /** Coerce a stored numeric (Integer/Long/Double or a stringified number) to a long, 0 when absent. */
    private static long asLong(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        if (raw == null) {
            return 0L;
        }
        return Long.parseLong(raw.toString().trim());
    }
}
