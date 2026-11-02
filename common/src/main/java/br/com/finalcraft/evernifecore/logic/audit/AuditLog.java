package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.economy.BalanceChangeEvent;
import br.com.finalcraft.evernifecore.economy.TransferEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryEvent;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.query.IndexHint;
import br.com.finalcraft.everydatabase.query.Query;
import br.com.finalcraft.everydatabase.query.QueryOptions;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a plugin's gates, wallets, boosters and deliveries did, kept in its own storage (collection
 * {@value #COLLECTION}, indexed by {@code at}, {@code player}, {@code kind} and {@code counterparty}) for as long as its
 * {@link AuditRetention} says, swept once an hour. While open it records, from the global bus, only what the owner
 * itself moved - its gates paid or denied, refunds that failed, balance changes, transfers and deliveries; a deposit
 * and a booster grant are recorded by their manager. One per plugin. Any thread: writes never wait.
 */
public final class AuditLog {

    public static final String COLLECTION = "ec_audit";

    static final Duration SWEEP_INTERVAL = Duration.ofHours(1);
    static final int SWEEP_SLICE = 500;

    /** The kinds of an {@link AuditTrail} that {@link #record(AuditTrail)} keeps. */
    private static final Set<AuditKind> TRAIL_KINDS = EnumSet.of(AuditKind.COST_TAKEN, AuditKind.COST_REFUNDED, AuditKind.REFUND_FAILED,
            AuditKind.EFFECT_APPLIED, AuditKind.EFFECT_FAILED);

    private static final Map<String, AuditLog> OWNED = new ConcurrentHashMap<>();

    private final ECPluginData plugin;
    private final String pluginName;
    private final ECStorage storage;
    private final AuditRetention retention;
    private final Repository<String, Row> repository;
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
    private final Set<CompletableFuture<?>> pendingWrites = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private volatile boolean listening;

    private AuditLog(ECPluginData plugin, ECStorage storage, AuditRetention retention) {
        this.plugin = plugin;
        this.pluginName = plugin.getMetaInfo().getName();
        this.storage = storage;
        this.retention = retention;
        this.repository = storage.repository(EntityDescriptor.builder(String.class, Row.class)
                .collection(COLLECTION)
                .keyExtractor(row -> row.id)
                .codec(storage.defaultCodec(Row.class))
                .index(IndexHint.bigInt("at"))
                .index(IndexHint.string("player"))
                .index(IndexHint.string("kind"))
                .index(IndexHint.string("counterparty"))
                .build());
    }

    /** {@code owner}'s log in {@code storage}, keeping 30 days. */
    public static AuditLog of(ECPluginData owner, ECStorage storage) {
        return of(owner, storage, AuditRetention.days(30));
    }

    /** {@code owner}'s log in {@code storage}; opening it again on another storage closes the one before. */
    public static AuditLog of(ECPluginData owner, ECStorage storage, AuditRetention retention) {
        AuditLog[] replaced = new AuditLog[1];
        AuditLog opened = OWNED.compute(owner.getMetaInfo().getName(), (name, existing) -> {
            if (existing != null && existing.storage == storage && existing.retention.equals(retention)) {
                return existing;
            }
            replaced[0] = existing;
            return new AuditLog(owner, storage, retention);
        });
        if (replaced[0] != null) {
            replaced[0].close();
        }
        opened.start(); //once per log: listens and schedules its sweep
        return opened;
    }

    public static Optional<AuditLog> ofIfPresent(ECPluginData owner) {
        return Optional.ofNullable(OWNED.get(owner.getMetaInfo().getName()));
    }

    /** By plugin name, ignoring case - the {@code /ecore logic audit <plugin>} lookup. */
    public static Optional<AuditLog> ofIfPresent(String pluginName) {
        for (AuditLog log : OWNED.values()) {
            if (log.pluginName.equalsIgnoreCase(pluginName)) {
                return Optional.of(log);
            }
        }
        return Optional.empty();
    }

    /** Stops recording for {@code owner}, waits for the writes on their way and forgets its log; called on its shutdown. */
    public static void closeOwnedBy(ECPluginData owner) {
        AuditLog closed = OWNED.remove(owner.getMetaInfo().getName());
        if (closed != null) {
            closed.close();
        }
    }

    public ECPluginData getPlugin() {
        return plugin;
    }

    public AuditRetention getRetention() {
        return retention;
    }

    // ------------------------------------------------------------------
    //  Recording
    // ------------------------------------------------------------------

    /** Keeps {@code entry}; written off the calling thread, never waited for. */
    public void record(AuditEntry entry) {
        if (closed) {
            return;
        }
        track(repository.save(Row.of(entry)));
    }

    /** One entry per cost taken or refunded, refund failed, effect applied or failed of {@code trail}, in order. */
    public void record(AuditTrail trail) {
        for (AuditEntry entry : trail.getEntries()) {
            if (TRAIL_KINDS.contains(entry.getKind())) {
                record(entry);
            }
        }
    }

    private synchronized void start() {
        if (listening) {
            return;
        }
        listening = true;
        listen();
        scheduleSweep();
    }

    private void listen() {
        ECEventBus bus = ECEventBus.global();
        subscriptions.add(bus.subscribe(GatePaidEvent.class, event -> {
            if (mine(event.getCaller())) {
                record(fromContext(AuditKind.GATE_PAID, event.getContext(), event.getResult().explain().toPlainText()));
            }
        }));
        subscriptions.add(bus.subscribe(GateDeniedEvent.class, event -> {
            if (mine(event.getCaller())) {
                record(fromContext(AuditKind.GATE_DENIED, event.getContext(), event.getResult().explain().toPlainText()));
            }
        }));
        subscriptions.add(bus.subscribe(CostRefundEvent.class, event -> {
            if (!event.isRefunded() && mine(event.getCaller())) {
                record(fromContext(AuditKind.REFUND_FAILED, event.getContext(), event.getCost() + " was taken and its refund failed"));
            }
        }));
        subscriptions.add(bus.subscribe(BalanceChangeEvent.class, event -> {
            //a transfer's two legs are its TRANSFER entry; a refund of one is a change of its own
            if (!mine(event.getCaller()) || event.getCause() == BalanceChangeEvent.Cause.TRANSFER_OUT
                    || event.getCause() == BalanceChangeEvent.Cause.TRANSFER_IN) {
                return;
            }
            record(AuditEntry.builder(AuditKind.BALANCE_CHANGE, event.getCaller())
                    .player(event.getOwner().getUuid())
                    .subject(event.getCurrency().getId())
                    .amount(event.getDelta())
                    .text(event.getCause().name().toLowerCase(Locale.ROOT) + " " + event.getOwner() + ": " + event.getBefore().toPlainString()
                            + " -> " + event.getAfter().toPlainString())
                    .build());
        }));
        subscriptions.add(bus.subscribe(TransferEvent.class, event -> {
            if (mine(event.getCaller())) {
                record(AuditEntry.builder(AuditKind.TRANSFER, event.getCaller())
                        .player(event.getFrom().getUuid())
                        .counterparty(event.getTo().getUuid())
                        .subject(event.getCurrency().getId())
                        .amount(event.getAmount())
                        .text(transferText(event))
                        .build());
            }
        }));
        subscriptions.add(bus.subscribe(DeliveryEvent.class, event -> {
            Delivery delivery = event.getDelivery();
            if (!delivery.getOrigin().getPlugin().equalsIgnoreCase(pluginName)) {
                return;
            }
            record(AuditEntry.builder(deliveryKind(event.getKind()), Caller.of(plugin, delivery.getOrigin().getEmitter()))
                    .player(delivery.getRecipient())
                    .subject(delivery.getId().toString())
                    .text(event.getKind().name().toLowerCase(Locale.ROOT) + " " + delivery.getEffects()
                            + (event.getBy() == null ? "" : " by " + event.getBy()))
                    .build());
        }));
    }

    private boolean mine(Caller caller) {
        return caller.getPlugin().getMetaInfo().getName().equalsIgnoreCase(pluginName);
    }

    private static AuditEntry fromContext(AuditKind kind, ExecutionContext ctx, String text) {
        AuditEntry.Builder entry = AuditEntry.builder(kind, ctx.getCaller()).owner(ctx.getOwner()).text(text);
        if (ctx.getPlayer() != null) {
            entry.player(ctx.getPlayer().getUniqueId());
        }
        return entry.build();
    }

    private static String transferText(TransferEvent event) {
        return event.getFrom() + " -> " + event.getTo() + " " + event.getAmount().toPlainString() + " " + event.getCurrency().getId()
                + (event.isSuccess() ? "" : " failed: " + event.getTake() + (event.getGive() == null ? "" : ", " + event.getGive()));
    }

    private static AuditKind deliveryKind(DeliveryEvent.Kind kind) {
        switch (kind) {
            case ENQUEUED:
                return AuditKind.DELIVERY_ENQUEUED;
            case DELIVERED:
                return AuditKind.DELIVERY_CLAIMED;
            case EXPIRED:
                return AuditKind.DELIVERY_EXPIRED;
            case PURGED:
                return AuditKind.DELIVERY_PURGED;
            default:
                return AuditKind.DELIVERY_DISCARDED;
        }
    }

    private void track(CompletableFuture<?> write) {
        pendingWrites.add(write);
        write.whenComplete((ok, error) -> {
            pendingWrites.remove(write);
            if (error != null) {
                plugin.getLog().warning("An audit entry of " + pluginName + " was not written: " + error);
            }
        });
    }

    // ------------------------------------------------------------------
    //  Reading
    // ------------------------------------------------------------------

    /** The entries {@code query} accepts, newest first. */
    public CompletableFuture<List<AuditEntry>> query(AuditQuery query) {
        int limit = query.getLimit();
        if (query.getBetweenA() != null) {
            //the query has no OR: one indexed query per direction, joined by time
            Query ab = narrowed(query, Query.eq("player", query.getBetweenA().toString()).and(Query.eq("counterparty", query.getBetweenB().toString())));
            Query ba = narrowed(query, Query.eq("player", query.getBetweenB().toString()).and(Query.eq("counterparty", query.getBetweenA().toString())));
            CompletableFuture<List<Row>> first = repository.query(ab, options(limit));
            CompletableFuture<List<Row>> second = repository.query(ba, options(limit));
            return first.thenCombine(second, (a, b) -> {
                Map<String, Row> joined = new LinkedHashMap<>();
                for (Row row : a) {
                    joined.put(row.id, row);
                }
                for (Row row : b) {
                    joined.put(row.id, row);
                }
                return toEntries(new ArrayList<>(joined.values()), limit);
            });
        }
        Query base = query.getPlayer() == null ? null : Query.eq("player", query.getPlayer().toString());
        return repository.query(narrowed(query, base), options(limit)).thenApply(rows -> toEntries(rows, limit));
    }

    private static Query narrowed(AuditQuery query, @Nullable Query base) {
        List<Query> parts = new ArrayList<>();
        if (base != null) {
            parts.add(base);
        }
        if (query.getCounterparty() != null) {
            parts.add(Query.eq("counterparty", query.getCounterparty().toString()));
        }
        if (query.getKind() != null) {
            parts.add(Query.eq("kind", query.getKind().name()));
        }
        if (query.getSince() != null) {
            parts.add(Query.range("at", query.getSince().toEpochMilli(), Long.MAX_VALUE));
        }
        Query joined = parts.isEmpty() ? Query.all() : parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            joined = joined.and(parts.get(i));
        }
        return joined;
    }

    private static QueryOptions options(int limit) {
        return QueryOptions.builder().descending("at").limit(limit).build();
    }

    private List<AuditEntry> toEntries(List<Row> rows, int limit) {
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingLong((Row row) -> row.at).reversed());
        List<AuditEntry> entries = new ArrayList<>();
        for (Row row : sorted) {
            if (entries.size() == limit) {
                break;
            }
            entries.add(row.toEntry(plugin));
        }
        return entries;
    }

    // ------------------------------------------------------------------
    //  Retention
    // ------------------------------------------------------------------

    private void scheduleSweep() {
        if (retention.getKeep() != null && !closed) {
            ECScheduler.scheduleAsync(() -> {
                if (!closed) {
                    sweepRetention().whenComplete((deleted, error) -> scheduleSweep());
                }
            }, SWEEP_INTERVAL.toMillis());
        }
    }

    /** Deletes every entry older than the retention, in slices of {@value #SWEEP_SLICE}; completes with how many went. */
    CompletableFuture<Integer> sweepRetention() {
        Duration keep = retention.getKeep();
        if (keep == null) {
            return CompletableFuture.completedFuture(0);
        }
        long cutoff = ECClock.now().minus(keep).toEpochMilli();
        return sweepFrom(cutoff, 0).whenComplete((deleted, error) -> {
            if (error != null) {
                plugin.getLog().warning("The audit retention sweep of " + pluginName + " failed; it runs again in an hour: " + error);
            } else {
                ECDebugModule.AUDIT.debug("The audit log of {} deleted {} entries older than {}.", pluginName, deleted, retention);
            }
        });
    }

    private CompletableFuture<Integer> sweepFrom(long cutoff, int deletedSoFar) {
        // ponytail: no deleteWhere in EveryDatabase, so a slice is read and deleted by key
        return repository.query(Query.range("at", 0L, cutoff), QueryOptions.builder().limit(SWEEP_SLICE).build()).thenCompose(rows -> {
            List<CompletableFuture<Boolean>> deletes = new ArrayList<>();
            for (Row row : rows) {
                deletes.add(repository.delete(row.id));
            }
            return CompletableFuture.allOf(deletes.toArray(new CompletableFuture[0])).thenCompose(ignored -> rows.size() < SWEEP_SLICE
                    ? CompletableFuture.completedFuture(deletedSoFar + rows.size())
                    : sweepFrom(cutoff, deletedSoFar + rows.size()));
        });
    }

    /**
     * For tests and shutdown: completes when every write started so far landed (a failed one too - it was logged). A write
     * starts when the entry is recorded, on the thread that recorded it.
     */
    public CompletableFuture<Void> awaitWrites() {
        return CompletableFuture.allOf(pendingWrites.toArray(new CompletableFuture[0])).exceptionally(error -> null);
    }

    private void close() {
        closed = true;
        subscriptions.forEach(ECEventSubscription::unsubscribe);
        subscriptions.clear();
        CompletableFuture.allOf(pendingWrites.toArray(new CompletableFuture[0])).exceptionally(error -> null).join();
    }

    /** Internal: one entry as the plugin's storage keeps it. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Row {
        String id;
        long at;
        String kind;
        String origin;
        String owner;
        @Nullable String player;
        @Nullable String counterparty;
        @Nullable String subject;
        @Nullable String amount;
        String text;

        Row() {
            //Jackson no-arg constructor
        }

        static Row of(AuditEntry entry) {
            Row row = new Row();
            row.id = entry.getId().toString();
            row.at = entry.getInstant().toEpochMilli();
            row.kind = entry.getKind().name();
            row.origin = entry.getCaller().getOrigin();
            row.owner = entry.getOwner().toString();
            row.player = entry.getPlayer() == null ? null : entry.getPlayer().toString();
            row.counterparty = entry.getCounterparty() == null ? null : entry.getCounterparty().toString();
            row.subject = entry.getSubject();
            row.amount = entry.getAmount() == null ? null : entry.getAmount().toPlainString();
            row.text = entry.getText();
            return row;
        }

        AuditEntry toEntry(ECPluginData plugin) {
            return AuditEntry.builder(AuditKind.valueOf(kind), Caller.of(plugin, origin))
                    .id(UUID.fromString(id))
                    .at(Instant.ofEpochMilli(at))
                    .owner(ProgressOwner.parse(owner))
                    .player(player == null ? null : UUID.fromString(player))
                    .counterparty(counterparty == null ? null : UUID.fromString(counterparty))
                    .subject(subject)
                    .amount(amount == null ? null : new BigDecimal(amount))
                    .text(text)
                    .build();
        }
    }
}
