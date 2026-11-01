package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.DeferredEffects;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import br.com.finalcraft.evernifecore.time.Window;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import br.com.finalcraft.everydatabase.query.Query;
import br.com.finalcraft.everydatabase.query.QueryOptions;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The pending deliveries of every player - the one piece of engine state the core keeps for plugins: what an effect
 * could not hand over (a full inventory, an offline recipient) waits in the recipient's {@link DeliveriesSection} as
 * raw effect nodes, and is handed out on login ({@link #claimAll}), by a claim, or dropped by its expiry (swept once a
 * minute) or an admin's {@link #purge}. Any thread: every verb completes a future, and a claim applies its effects on
 * the player's thread.
 */
public final class Deliveries {

    static final Duration SWEEP_INTERVAL = Duration.ofSeconds(60);

    /**
     * The context attribute a claim runs its effects with: a {@code List<JsonNode>} where an effect puts what it still
     * could not hand over, so it stays in the claimed delivery instead of becoming a new one.
     */
    public static final String CLAIM_LEFTOVERS = "evernifecore:delivery-leftovers";

    private static final Deliveries INSTANCE = new Deliveries();

    //the running sweep's generation, 0 while none runs; a sweep of an older generation stops instead of rescheduling
    private static final AtomicInteger SWEEP = new AtomicInteger();
    private static final AtomicInteger GENERATIONS = new AtomicInteger();
    private static volatile @Nullable ECEventSubscription<ECPlayerFullyLoggedInEvent> loginSubscription;

    private Deliveries() {
    }

    public static Deliveries get() {
        return INSTANCE;
    }

    /** Claims every delivery of each player who logs in and sweeps the expired ones once a minute, until {@link #closeAll()}. */
    public static synchronized void start() {
        if (loginSubscription == null) {
            loginSubscription = ECEventBus.global().subscribe(ECPlayerFullyLoggedInEvent.class, event -> onLogin(event.getPlayer()));
        }
        //a delayed effect whose player left waits here instead of being dropped
        DeferredEffects.register(Deliveries::keepDeferred);
        int generation = GENERATIONS.incrementAndGet();
        SWEEP.set(generation);
        ECScheduler.scheduleAsync(() -> sweep(generation), SWEEP_INTERVAL.toMillis());
    }

    /** Stops the login claim and the sweep; the pending deliveries stay in the players' data. */
    public static synchronized void closeAll() {
        ECEventSubscription<ECPlayerFullyLoggedInEvent> subscription = loginSubscription;
        loginSubscription = null;
        if (subscription != null) {
            subscription.unsubscribe();
        }
        SWEEP.set(0);
        DeferredEffects.register(null);
    }

    private static void keepDeferred(ExecutionContext ctx, Effect effect, JsonNode node, UUID absentPlayer) {
        get().enqueueSelfContained(ctx, absentPlayer, Collections.singletonList(node)).exceptionally(failure -> {
            EverNifeCore.getLog().warning("[" + ctx.getCaller() + "] " + effect + " for " + absentPlayer + ", who left before it fired, was not"
                    + " kept as a delivery: " + failure);
            return null;
        });
    }

    private static void onLogin(@Nullable FPlayer player) {
        if (player == null || !PlayerController.getConfiguredPDSections().containsKey(DeliveriesSection.class)) {
            return;
        }
        get().claimAll(player).exceptionally(failure -> {
            EverNifeCore.getLog().warning("The pending deliveries of " + player.getName() + " were not claimed on login: " + failure);
            return null;
        });
    }

    // ------------------------------------------------------------------
    //  Keeping
    // ------------------------------------------------------------------

    /**
     * Keeps {@code effects} for {@code recipient}, online or not, and posts {@link DeliveryEvent} ENQUEUED.
     *
     * @param ttl how long it waits to be claimed; null keeps it until claimed or purged
     */
    public CompletableFuture<Delivery> enqueue(UUID recipient, DeliveryOrigin origin, DeliveryPolicy policy, @Nullable Duration ttl,
                                               List<JsonNode> effects) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(policy, "policy");
        if (effects.isEmpty()) {
            throw new IllegalArgumentException("A delivery to " + recipient + " from " + origin + " owes nothing: give it at least one effect.");
        }
        Instant now = ECClock.now();
        Delivery delivery = new Delivery(UUID.randomUUID(), recipient, origin, now, ttl == null ? null : now.plus(ttl), policy, effects);
        return section(recipient).thenApply(section -> {
            section.add(delivery);
            post(DeliveryEvent.Kind.ENQUEUED, delivery, null);
            return delivery;
        });
    }

    /**
     * What an effect does with {@code node}, the part of it that did not reach the context's player: inside a claim it
     * stays in the claimed delivery; anywhere else it becomes a new delivery from the context's caller.
     *
     * @return the new delivery, or a completed null when it stayed in the claim
     * @throws IllegalStateException when the context has neither a player nor a player owner to keep it for
     */
    public CompletableFuture<Delivery> keepFor(ExecutionContext ctx, DeliveryPolicy policy, @Nullable Duration ttl, JsonNode node) {
        @SuppressWarnings("unchecked")
        List<JsonNode> leftovers = ctx.getAttribute(CLAIM_LEFTOVERS, List.class);
        if (leftovers != null) {
            leftovers.add(node);
            return CompletableFuture.completedFuture(null);
        }
        return enqueue(recipientOf(ctx), DeliveryOrigin.of(ctx.getCaller(), ""), policy, ttl, Collections.singletonList(node));
    }

    /**
     * The path of a delayed effect whose player left: keeps only the nodes whose type is the core's, so they can be
     * handed out whatever plugins are loaded later, and audits each other node as {@code DELIVERY_DISCARDED} (posting
     * DISCARDED) - a plugin's effect is only safe to replay by that plugin.
     *
     * @return the delivery kept, or a completed null when nothing was self-contained
     */
    public CompletableFuture<Delivery> enqueueSelfContained(ExecutionContext ctx, UUID recipient, List<JsonNode> effects) {
        List<JsonNode> kept = new ArrayList<>();
        DeliveryOrigin origin = DeliveryOrigin.of(ctx.getCaller(), "");
        for (JsonNode effect : effects) {
            if (Delivery.isCoreEffect(effect)) {
                kept.add(effect);
                continue;
            }
            ctx.getAudit().add(ctx, AuditKind.DELIVERY_DISCARDED, effect + " for " + recipient + " was discarded: its player left before"
                    + " it applied, and only the core's own effects can wait in a delivery.");
            post(DeliveryEvent.Kind.DISCARDED, new Delivery(UUID.randomUUID(), recipient, origin, ECClock.now(), null,
                    DeliveryPolicy.KEEP_SELF_CONTAINED, Collections.singletonList(effect)), null);
        }
        if (kept.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return enqueue(recipient, origin, DeliveryPolicy.KEEP_SELF_CONTAINED, null, kept);
    }

    /** Every delivery {@code recipient} has pending, oldest first, expired ones included until the sweep drops them. */
    public CompletableFuture<List<Delivery>> listOf(UUID recipient) {
        return section(recipient).thenApply(DeliveriesSection::getDeliveries);
    }

    // ------------------------------------------------------------------
    //  Claiming
    // ------------------------------------------------------------------

    /** Hands out what it can of the delivery {@code deliveryId} of {@code player}; what still does not fit stays pending. */
    public CompletableFuture<ClaimResult> claim(FPlayer player, UUID deliveryId) {
        return claimWhere(player, delivery -> delivery.getId().equals(deliveryId));
    }

    /** {@link #claim} of every delivery {@code player} has - the login path. */
    public CompletableFuture<ClaimResult> claimAll(FPlayer player) {
        return claimWhere(player, delivery -> true);
    }

    private CompletableFuture<ClaimResult> claimWhere(FPlayer player, Predicate<Delivery> which) {
        return section(player.getUniqueId()).thenCompose(section -> onPlayerThread(player, () -> claimNow(player, section, which)));
    }

    private ClaimResult claimNow(FPlayer player, DeliveriesSection section, Predicate<Delivery> which) {
        List<Delivery> delivered = new ArrayList<>();
        List<Delivery> kept = new ArrayList<>();
        List<Delivery> expired = section.removeIf(delivery -> which.test(delivery) && delivery.isExpiredAt(ECClock.now()));
        for (Delivery delivery : expired) {
            post(DeliveryEvent.Kind.EXPIRED, delivery, null);
        }
        for (Delivery delivery : section.getDeliveries()) {
            if (!which.test(delivery)) {
                continue;
            }
            Delivery after = claimOne(player, delivery);
            if (after == null) {
                section.removeIf(each -> each.getId().equals(delivery.getId()));
                delivered.add(delivery);
                post(DeliveryEvent.Kind.DELIVERED, delivery, null);
            } else {
                if (after != delivery) {
                    section.replace(after);
                }
                kept.add(after);
            }
        }
        return new ClaimResult(delivered, kept, expired);
    }

    /** Applies what of {@code delivery} can be applied now; null when nothing is left, else the delivery still owing the rest. */
    private @Nullable Delivery claimOne(FPlayer player, Delivery delivery) {
        ECPluginData plugin = pluginOf(delivery.getOrigin());
        Caller caller = Caller.of(plugin, delivery.getOrigin().getEmitter());
        List<Effect> parsed = new ArrayList<>();
        List<JsonNode> unread = new ArrayList<>();
        for (JsonNode node : delivery.getEffects()) {
            try {
                Effect effect = Effects.parse(plugin, node, Window.getServerZone());
                if (Effects.getPendingType(effect) == null) {
                    parsed.add(effect);
                    continue;
                }
            } catch (TypeRegistryException unreadable) {
                EverNifeCore.getLog().warning("An effect of delivery " + delivery.getId() + " from " + delivery.getOrigin() + " does not"
                        + " read and stays pending: " + unreadable.getMessage());
            }
            unread.add(node);
        }
        if (!unread.isEmpty() && delivery.getPolicy() == DeliveryPolicy.REQUIRES_ORIGIN) {
            return delivery; //one piece: it waits for every effect's plugin
        }
        List<JsonNode> leftovers = new ArrayList<>();
        ExecutionContext ctx = ExecutionContext.builder(caller).player(player).owner(ProgressOwner.player(player.getUniqueId()))
                .attribute(CLAIM_LEFTOVERS, leftovers).build();
        for (Effect effect : parsed) {
            Effects.apply(ctx, effect); //on the player's thread already: in place
        }
        List<JsonNode> remaining = new ArrayList<>(unread);
        remaining.addAll(leftovers);
        if (remaining.isEmpty()) {
            return null;
        }
        return remaining.equals(delivery.getEffects()) ? delivery : delivery.withEffects(remaining);
    }

    /** The origin's plugin when it is loaded; the core otherwise, which still reads every effect of its own. */
    private static ECPluginData pluginOf(DeliveryOrigin origin) {
        for (Map.Entry<String, ECPluginData> entry : ECPluginManager.getECPluginsMap().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(origin.getPlugin())) {
                return entry.getValue();
            }
        }
        return EverNifeCore.getEcPluginData();
    }

    // ------------------------------------------------------------------
    //  Admin
    // ------------------------------------------------------------------

    /**
     * The deliveries from {@code pluginName} of every player who has one, loaded or not: the loaded players as they
     * are now, the others as last stored.
     */
    public CompletableFuture<Map<UUID, List<Delivery>>> listFrom(String pluginName) {
        String plugin = pluginName.toLowerCase(Locale.ROOT);
        Map<UUID, List<Delivery>> found = new LinkedHashMap<>();
        for (PlayerData loaded : PlayerController.getAllLoaded()) {
            DeliveriesSection section = loaded.getPDSectionIfLoaded(DeliveriesSection.class);
            if (section != null) {
                addFrom(found, loaded.getUniqueId(), section, plugin);
            }
        }
        // ponytail: reads every stored deliveries row; an index on the origin plugin when servers grow past a quick scan
        return PlayerController.querySections(DeliveriesSection.class, Query.all(), QueryOptions.none())
                .handle((rows, failure) -> {
                    if (failure != null) {
                        EverNifeCore.getLog().warning("The stored deliveries could not be read, so only the loaded players count: " + failure);
                        return found;
                    }
                    for (DeliveriesSection row : rows) {
                        if (PlayerController.getLoaded(row.getUniqueId()) == null) {
                            addFrom(found, row.getUniqueId(), row, plugin);
                        }
                    }
                    return found;
                });
    }

    private static void addFrom(Map<UUID, List<Delivery>> found, UUID player, DeliveriesSection section, String plugin) {
        List<Delivery> from = new ArrayList<>();
        for (Delivery delivery : section.getDeliveries()) {
            if (delivery.getOrigin().getPlugin().equals(plugin)) {
                from.add(delivery);
            }
        }
        if (!from.isEmpty()) {
            found.put(player, from);
        }
    }

    /**
     * Removes every pending delivery from {@code pluginName}, of every player, posting PURGED for each and logging how
     * many on the core's log; completes with how many went away.
     */
    public CompletableFuture<Integer> purge(String pluginName, Caller by) {
        String plugin = pluginName.toLowerCase(Locale.ROOT);
        return listFrom(plugin).thenCompose(found -> {
            List<CompletableFuture<Integer>> each = new ArrayList<>();
            for (UUID player : found.keySet()) {
                each.add(section(player).thenApply(section -> {
                    List<Delivery> removed = section.removeIf(delivery -> delivery.getOrigin().getPlugin().equals(plugin));
                    for (Delivery delivery : removed) {
                        post(DeliveryEvent.Kind.PURGED, delivery, by.toString());
                    }
                    return removed.size();
                }));
            }
            return CompletableFuture.allOf(each.toArray(new CompletableFuture[0])).thenApply(ignored -> {
                int total = 0;
                for (CompletableFuture<Integer> count : each) {
                    total += count.join();
                }
                EverNifeCore.getLog().info("[" + by + "] purged " + total + " pending deliveries from " + plugin + " of " + found.size()
                        + " players.");
                return total;
            });
        });
    }

    // ------------------------------------------------------------------
    //  Expiry
    // ------------------------------------------------------------------

    /** Drops the expired deliveries of every loaded player, posting EXPIRED for each; how many went away. */
    public int sweepExpired() {
        Instant now = ECClock.now();
        int removed = 0;
        for (PlayerData loaded : PlayerController.getAllLoaded()) {
            DeliveriesSection section = loaded.getPDSectionIfLoaded(DeliveriesSection.class);
            if (section == null) {
                continue;
            }
            for (Delivery delivery : section.removeIf(each -> each.isExpiredAt(now))) {
                post(DeliveryEvent.Kind.EXPIRED, delivery, null);
                removed++;
            }
        }
        return removed;
    }

    static void sweep(int generation) {
        if (SWEEP.get() != generation) {
            return;
        }
        try {
            get().sweepExpired();
        } catch (RuntimeException failure) {
            EverNifeCore.getLog().warning("The sweep of expired deliveries failed; it runs again in a minute: " + failure);
        }
        ECScheduler.scheduleAsync(() -> sweep(generation), SWEEP_INTERVAL.toMillis());
    }

    // ------------------------------------------------------------------
    //  Plumbing
    // ------------------------------------------------------------------

    private static CompletableFuture<DeliveriesSection> section(UUID player) {
        return PlayerController.getOrCreate(player).thenCompose(data -> data.getPDSection(DeliveriesSection.class));
    }

    static UUID recipientOf(ExecutionContext ctx) {
        if (ctx.getPlayer() != null) {
            return ctx.getPlayer().getUniqueId();
        }
        ProgressOwner owner = ctx.getOwner();
        if (owner.isKind(ProgressOwner.PLAYER)) {
            return UUID.fromString(owner.getId());
        }
        throw new IllegalStateException("A delivery is a player's, and the context of " + ctx.getCaller() + " has no player and is owned by "
                + owner + ": run it for a player, or send it to a destination that is not a player's.");
    }

    /** {@code task} on {@code player}'s world thread - in place when already there, on the main thread for a player nowhere. */
    static <T> CompletableFuture<T> onPlayerThread(FPlayer player, Supplier<T> task) {
        FLocation location = player.getLocation();
        if (location == null) {
            return ECScheduler.onMainThread(task);
        }
        IPlatform platform = EverNifeCore.getPlatform();
        String world = location.getWorldName();
        if (platform.isWorldThread(world)) {
            return CompletableFuture.completedFuture(task.get());
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        platform.runOnWorldThread(world, () -> {
            try {
                result.complete(task.get());
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        }).exceptionally(failure -> {
            result.completeExceptionally(failure);
            return null;
        });
        return result;
    }

    private static void post(DeliveryEvent.Kind kind, Delivery delivery, @Nullable String by) {
        ECEventBus.global().postIfListened(DeliveryEvent.class, () -> new DeliveryEvent(kind, delivery, by));
    }
}
