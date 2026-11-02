package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import br.com.finalcraft.evernifecore.time.Season;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.CachePolicy;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The boosters a plugin granted - a bought x2, an event's +50% - kept in its storage (collection {@value #COLLECTION},
 * key the booster's id) or in memory only, and a {@link BoosterSource} of its own ({@code <plugin>:manager}) while it
 * is open. One per plugin; a booster past its end is dropped by a sweep once a minute. Any thread; {@code grant} and
 * {@code revoke} complete when the storage has the change.
 */
public final class BoosterManager implements BoosterSource {

    public static final String COLLECTION = "ec_boosters";

    static final Duration SWEEP_INTERVAL = Duration.ofSeconds(60);

    private static final Map<String, BoosterManager> OWNED = new ConcurrentHashMap<>();
    //the running sweep's generation, 0 while none runs; a sweep of an older generation stops instead of rescheduling
    private static final AtomicInteger SWEEP = new AtomicInteger();
    private static final AtomicInteger GENERATIONS = new AtomicInteger();

    private final ECPluginData plugin;
    private final @Nullable ECStorage storage;
    private final @Nullable CachingManager<String, Row> manager;
    private final Map<UUID, Booster> boosters = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> pendingWrites = ConcurrentHashMap.newKeySet();

    private BoosterManager(ECPluginData plugin, @Nullable ECStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        if (storage == null) {
            this.manager = null;
            return;
        }
        EntityDescriptor<String, Row> descriptor = EntityDescriptor.builder(String.class, Row.class)
                .collection(COLLECTION)
                .keyExtractor(row -> row.id)
                .codec(storage.defaultCodec(Row.class))
                .build();
        this.manager = storage.manager(descriptor, CachePolicy.always());
        manager.preloadAll().join(); //a multiplier is read on the caller's thread: the set is warmed once, here
        for (Row row : manager.cachedValues()) {
            Booster booster = row.toBooster(plugin);
            boosters.put(booster.getId(), booster);
        }
    }

    /**
     * {@code owner}'s boosters kept in {@code storage}; one per plugin, added as a source while open. Reopening with
     * another storage waits for the writes of the manager it replaces before loading.
     */
    public static BoosterManager of(ECPluginData owner, ECStorage storage) {
        BoosterManager opened = open(owner, storage);
        int generation = GENERATIONS.incrementAndGet();
        if (SWEEP.compareAndSet(0, generation)) {
            ECScheduler.scheduleAsync(() -> sweep(generation), SWEEP_INTERVAL.toMillis());
        }
        return opened;
    }

    /** {@code owner}'s open boosters, or ones kept in memory only (they end with the server) when none are open. */
    public static BoosterManager of(ECPluginData owner) {
        BoosterManager existing = OWNED.get(owner.getMetaInfo().getName());
        return existing != null ? existing : open(owner, null);
    }

    public static Optional<BoosterManager> ofIfPresent(ECPluginData owner) {
        return Optional.ofNullable(OWNED.get(owner.getMetaInfo().getName()));
    }

    private static BoosterManager open(ECPluginData owner, @Nullable ECStorage storage) {
        BoosterManager[] replaced = new BoosterManager[1];
        BoosterManager opened = OWNED.compute(owner.getMetaInfo().getName(), (name, existing) -> {
            if (existing != null && existing.storage == storage) {
                return existing;
            }
            replaced[0] = existing;
            if (existing != null) {
                existing.awaitWrites(); //the new manager may load what the old one is still writing
            }
            BoosterManager created = new BoosterManager(owner, storage);
            Boosters.addSource(owner, created);
            return created;
        });
        if (replaced[0] != null) {
            Boosters.removeSource(replaced[0]);
        }
        return opened;
    }

    /** Awaits the writes still on their way, stops counting {@code owner}'s boosters and forgets them; called on its shutdown. */
    public static void closeOwnedBy(ECPluginData owner) {
        BoosterManager closed = OWNED.remove(owner.getMetaInfo().getName());
        if (closed != null) {
            Boosters.removeSource(closed);
            closed.awaitWrites();
        }
        if (OWNED.values().stream().noneMatch(boosters -> boosters.manager != null)) {
            SWEEP.set(0);
        }
    }

    private void awaitWrites() {
        CompletableFuture.allOf(pendingWrites.toArray(new CompletableFuture[0])).exceptionally(error -> null).join();
    }

    /**
     * Keeps {@code booster} under a new id, posts a {@link BoosterGrantedEvent} and records it in the plugin's
     * {@link AuditLog} when it has one; completes with the kept booster once stored.
     */
    public CompletableFuture<Booster> grant(Booster booster) {
        Booster kept = booster.withId(UUID.randomUUID());
        boosters.put(kept.getId(), kept);
        CompletableFuture<?> write = manager == null ? CompletableFuture.completedFuture(null) : track(manager.saveAndCache(Row.of(kept)));
        ECEventBus.global().postIfListened(BoosterGrantedEvent.class, () -> new BoosterGrantedEvent(this, kept));
        AuditLog.ofIfPresent(plugin).ifPresent(log -> log.record(AuditEntry.builder(AuditKind.BOOSTER_GRANTED,
                        kept.getOrigin() != null ? kept.getOrigin() : Caller.of(plugin, "booster"))
                .owner(kept.getOwner())
                .subject(kept.getVariableId())
                .amount(kept.getFactor() != null ? kept.getFactor() : kept.getBonus())
                .text(kept.toString())
                .build()));
        return write.thenApply(ignored -> kept);
    }

    /** Drops the booster {@code boosterId}; completes with whether this manager had it. */
    public CompletableFuture<Boolean> revoke(UUID boosterId) {
        Booster removed = boosters.remove(boosterId);
        if (removed == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<?> write = manager == null ? CompletableFuture.completedFuture(null) : track(manager.deleteAndEvict(boosterId.toString()));
        return write.thenApply(ignored -> true);
    }

    /** {@code owner}'s boosters that count now, any gain. */
    public List<Booster> getActive(ProgressOwner owner) {
        Instant now = ECClock.now();
        List<Booster> active = new ArrayList<>();
        for (Booster booster : boosters.values()) {
            if (booster.getOwner().equals(owner) && booster.isActiveAt(now)) {
                active.add(booster);
            }
        }
        return active;
    }

    /** Every booster kept, active or not yet swept. */
    public Collection<Booster> getAll() {
        return new ArrayList<>(boosters.values());
    }

    public ECPluginData getPlugin() {
        return plugin;
    }

    @Override
    public String getId() {
        return plugin.getMetaInfo().getName().toLowerCase(Locale.ROOT) + ":manager";
    }

    @Override
    public Collection<Booster> boostersFor(ExecutionContext ctx, String variableId) {
        List<Booster> found = new ArrayList<>();
        for (Booster booster : boosters.values()) {
            if (booster.getVariableId().equals(variableId) && Boosters.appliesTo(booster.getOwner(), ctx)) {
                found.add(booster);
            }
        }
        return found;
    }

    private CompletableFuture<?> track(CompletableFuture<?> write) {
        pendingWrites.add(write);
        write.whenComplete((ok, error) -> {
            pendingWrites.remove(write);
            if (error != null) {
                EverNifeCore.getLog().warning("A booster of " + plugin.getMetaInfo().getName() + " was not written: " + error);
            }
        });
        return write;
    }

    /** Drops every booster that can never count again; how many went away. */
    int pruneOver(Instant now) {
        int removed = 0;
        for (Booster booster : new ArrayList<>(boosters.values())) {
            if (booster.isOverAt(now) && boosters.remove(booster.getId(), booster)) {
                removed++;
                if (manager != null) {
                    track(manager.deleteAndEvict(booster.getId().toString()));
                }
            }
        }
        return removed;
    }

    static void sweep(int generation) {
        if (SWEEP.get() != generation) {
            return;
        }
        Instant now = ECClock.now();
        boolean stored = false;
        for (BoosterManager owned : OWNED.values()) {
            if (owned.manager != null) {
                stored = true;
                owned.pruneOver(now);
            }
        }
        if (stored) {
            ECScheduler.scheduleAsync(() -> sweep(generation), SWEEP_INTERVAL.toMillis());
        } else {
            SWEEP.compareAndSet(generation, 0);
        }
    }

    /** Internal: one booster as its plugin's storage keeps it. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Row {
        String id;
        String on;
        String owner;
        @Nullable String factor;
        @Nullable String bonus;
        @Nullable Long until;
        @Nullable String seasonId;
        @Nullable String seasonFrom;
        @Nullable String seasonTo;
        @Nullable String origin;

        Row() {
            //Jackson no-arg constructor
        }

        static Row of(Booster booster) {
            Row row = new Row();
            row.id = booster.getId().toString();
            row.on = booster.getVariableId();
            row.owner = booster.getOwner().toString();
            row.factor = booster.getFactor() == null ? null : booster.getFactor().toPlainString();
            row.bonus = booster.getBonus() == null ? null : booster.getBonus().toPlainString();
            row.until = booster.getUntil() == null ? null : booster.getUntil().toEpochMilli();
            Season season = booster.getSeason();
            if (season != null) {
                row.seasonId = season.getId();
                row.seasonFrom = season.getFrom().toString();
                row.seasonTo = season.getTo().toString();
            }
            row.origin = booster.getOrigin() == null ? null : booster.getOrigin().getOrigin();
            return row;
        }

        Booster toBooster(ECPluginData plugin) {
            Booster.Builder builder = Booster.on(on).forOwner(ProgressOwner.parse(owner))
                    .until(until == null ? null : Instant.ofEpochMilli(until))
                    .from(origin == null ? null : Caller.of(plugin, origin));
            if (seasonId != null) {
                builder.during(Season.of(seasonId, ZonedDateTime.parse(seasonFrom), ZonedDateTime.parse(seasonTo)));
            }
            if (factor != null) {
                builder.factor(new BigDecimal(factor));
            } else {
                builder.bonus(new BigDecimal(bonus));
            }
            return builder.build().withId(UUID.fromString(id));
        }
    }
}
