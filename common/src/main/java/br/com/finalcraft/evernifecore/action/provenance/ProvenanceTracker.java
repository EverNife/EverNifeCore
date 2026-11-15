package br.com.finalcraft.evernifecore.action.provenance;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.SpawnContext;
import br.com.finalcraft.evernifecore.action.event.BreakActionEvent;
import br.com.finalcraft.evernifecore.action.event.DropActionEvent;
import br.com.finalcraft.evernifecore.action.event.KillActionEvent;
import br.com.finalcraft.evernifecore.action.event.PickupActionEvent;
import br.com.finalcraft.evernifecore.action.event.PlaceActionEvent;
import br.com.finalcraft.evernifecore.action.event.SpawnActionEvent;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.blockdata.SVWorldDataManager;
import br.com.finalcraft.evernifecore.config.plugin.ProvenanceSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.ECSubscribeOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.time.ECClock;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A plugin's access to the one record of where blocks placed, creatures spawned and items dropped came from. The record
 * lives in EverNifeCore's storage and exists only while some plugin has it open: the first {@link #open} opens it, the
 * last {@link #closeOwnedBy} closes it, and the core never records on its own.
 *
 * <pre>{@code
 * ProvenanceTracker origins = ProvenanceTracker.open(getEcPluginData(), settings.getProvenance());
 * ProvenanceHold recording = origins.record();          // from the enable on: every placed block is remembered
 * origins.getOrigin(blockPos).thenAccept(origin -> { if (origin.isOwn(player)) ... });
 * }</pre>
 *
 * <p>Each plugin declares in its {@link ProvenanceSettings} how long a placed block is remembered; the record keeps the
 * longest of the plugins open ({@link #getRetention()}), so when the longest leaves, older records read as natural and go
 * on their next read - nothing is deleted the moment it leaves.</p>
 *
 * <p>Recording is subscribing: while any plugin holds a recording, the record listens to {@link PlaceActionEvent.Post},
 * {@link SpawnActionEvent.Post}, {@link DropActionEvent.Post}, {@link BreakActionEvent.Post}, {@link KillActionEvent.Post}
 * and {@link PickupActionEvent.Post} - and those subscriptions are what switch the platform's listeners on. A block
 * placed while nobody recorded reads as {@link Provenance#NATURAL}.</p>
 *
 * <p>{@link #getOrigin(UUID)} and {@link #peekOrigin} answer on any thread; {@link #getOrigin(WorldBlockPos)} completes
 * on the storage's thread.</p>
 */
public final class ProvenanceTracker {

    private static final Object LOCK = new Object();
    private static final Supplier<ECStorage> CORE_STORAGE = ProvenanceTracker::openCoreStorage;

    // guarded by LOCK: who has the record open, and the record itself while anyone does
    private static final Map<ECPluginData, ProvenanceTracker> OPEN = new LinkedHashMap<>();
    private static final List<ECEventSubscription<?>> SUBSCRIPTIONS = new ArrayList<>();
    private static @Nullable ECStorage storage;
    private static volatile @Nullable ProvenanceStore store;
    private static int holds;
    private static volatile Supplier<ECStorage> storageOpener = CORE_STORAGE;

    private final ECPluginData owner;
    private volatile ProvenanceSettings settings;
    private final Set<ProvenanceHold> ownHolds = ConcurrentHashMap.newKeySet();

    private ProvenanceTracker(ECPluginData owner, ProvenanceSettings settings) {
        this.owner = owner;
        this.settings = settings;
    }

    /**
     * {@code owner}'s access to the one record, opening the record when nobody had it open. Called again (a reload), the
     * same access takes {@code settings} in place of the ones it had. Any thread.
     *
     * @throws IllegalStateException when EverNifeCore's storage is not loaded yet - open it in the plugin's enable
     */
    public static ProvenanceTracker open(ECPluginData owner, ProvenanceSettings settings) {
        Objects.requireNonNull(owner, "'owner' cannot be null when opening the provenance record!");
        Objects.requireNonNull(settings, "'settings' cannot be null when opening the provenance record!");
        synchronized (LOCK) {
            ProvenanceTracker existing = OPEN.get(owner);
            if (existing != null) {
                existing.settings = settings;
                return existing;
            }
            if (OPEN.isEmpty()) {
                ECStorage opened = storageOpener.get();
                storage = opened;
                store = new ProvenanceStore(opened, ProvenanceTracker::getRetention);
            }
            ProvenanceTracker access = new ProvenanceTracker(owner, settings);
            OPEN.put(owner, access);
            return access;
        }
    }

    /** {@code owner}'s access to the record; null when it has none open. */
    public static @Nullable ProvenanceTracker ofIfPresent(ECPluginData owner) {
        synchronized (LOCK) {
            return OPEN.get(owner);
        }
    }

    /** Whether the record is open: some plugin opened it and has not left. */
    public static boolean isOpen() {
        synchronized (LOCK) {
            return !OPEN.isEmpty();
        }
    }

    /**
     * How long a placed block is remembered now: the longest retention among the plugins with the record open; zero when
     * none has it open.
     */
    public static Duration getRetention() {
        Duration longest = Duration.ZERO;
        synchronized (LOCK) {
            for (ProvenanceTracker access : OPEN.values()) {
                Duration own = access.settings.getRetention().getPlayerPlaced();
                if (own.compareTo(longest) > 0) {
                    longest = own;
                }
            }
        }
        return longest;
    }

    /**
     * Closes {@code owner}'s recordings and its access; the last plugin to leave closes the record, writing its blocks and
     * dropping its subscriptions. Called on the owner's shutdown. Any thread.
     */
    public static void closeOwnedBy(ECPluginData owner) {
        ProvenanceTracker leaving;
        synchronized (LOCK) {
            leaving = OPEN.get(owner);
        }
        if (leaving == null) {
            return;
        }
        for (ProvenanceHold hold : new ArrayList<>(leaving.ownHolds)) {
            hold.close();
        }
        synchronized (LOCK) {
            OPEN.remove(owner);
            if (OPEN.isEmpty()) {
                closeRecord();
            }
        }
    }

    /** Internal: closes the record and every access to it - the core's shutdown, for what no plugin shutdown closed. */
    public static void closeAll() {
        List<ECPluginData> owners;
        synchronized (LOCK) {
            owners = new ArrayList<>(OPEN.keySet());
        }
        owners.forEach(ProvenanceTracker::closeOwnedBy);
    }

    /**
     * Internal: where the next record opens its storage - EverNifeCore's own backend when {@code opener} is null. A test
     * points it at an in-memory storage; the record closes what the opener gave it.
     */
    public static void useStorage(@Nullable Supplier<ECStorage> opener) {
        storageOpener = opener != null ? opener : CORE_STORAGE;
    }

    private static ECStorage openCoreStorage() {
        try {
            //its own handle on the core's backend: a core reload rebuilds the PlayerController's storages, the record outlives it
            return ECStorage.open(PlayerController.getDefaultBackendDefinition()).join();
        } catch (IllegalStateException notYet) {
            throw new IllegalStateException("The provenance record lives in EverNifeCore's storage, which is not loaded yet: open the"
                    + " tracker in your plugin's enable (EverNifeCore loads before every plugin that depends on it), not in its load"
                    + " or a static initializer.", notYet);
        }
    }

    private static void closeRecord() {
        SUBSCRIPTIONS.forEach(ECEventSubscription::unsubscribe);
        SUBSCRIPTIONS.clear();
        holds = 0;
        ProvenanceStore closing = store;
        ECStorage closingStorage = storage;
        store = null;
        storage = null;
        if (closing != null) {
            closing.close();
        }
        if (closingStorage != null) {
            closingStorage.close().join();
        }
    }

    /** Keeps the record recording until the hold is closed, or until this plugin leaves. */
    public ProvenanceHold record() {
        synchronized (LOCK) {
            if (OPEN.get(owner) != this) {
                throw new IllegalStateException(owner.getMetaInfo().getName() + " left the provenance record (closeOwnedBy): open it again"
                        + " with ProvenanceTracker.open(owner, settings) before recording.");
            }
            if (holds++ == 0) {
                subscribeAll();
            }
            ProvenanceHold hold = new ProvenanceHold(this);
            ownHolds.add(hold);
            return hold;
        }
    }

    void release(ProvenanceHold hold) {
        synchronized (LOCK) {
            if (ownHolds.remove(hold) && holds > 0 && --holds == 0) {
                SUBSCRIPTIONS.forEach(ECEventSubscription::unsubscribe);
                SUBSCRIPTIONS.clear();
            }
        }
    }

    /** Whether the record is recording now, for any plugin. */
    public boolean isRecording() {
        synchronized (LOCK) {
            return holds > 0;
        }
    }

    public ECPluginData getOwner() {
        return owner;
    }

    /** The settings this plugin opened the record with. */
    public ProvenanceSettings getSettings() {
        return settings;
    }

    /** Where the block at {@code block} came from; completes on the storage's thread, the chunk may not be cached. */
    public CompletableFuture<Provenance> getOrigin(WorldBlockPos block) {
        return store().getBlock(block);
    }

    /** The recorded origin of {@code block} if its chunk is cached; null when there is no record in memory. */
    public @Nullable Provenance peekOrigin(WorldBlockPos block) {
        return store().peekBlock(block);
    }

    /** Where the creature or item {@code entity} came from; NATURAL when it was never recorded or left memory. */
    public Provenance getOrigin(UUID entity) {
        return store().getEntity(entity);
    }

    /** Writes the recorded blocks now instead of on the next periodic flush. */
    public CompletableFuture<SVWorldDataManager.FlushReport> flush() {
        return store().flush();
    }

    private ProvenanceStore store() {
        ProvenanceStore open = store;
        if (open == null || ofIfPresent(owner) != this) {
            throw new IllegalStateException(owner.getMetaInfo().getName() + " left the provenance record (closeOwnedBy): open it again"
                    + " with ProvenanceTracker.open(owner, settings) before reading origins.");
        }
        return open;
    }

    private static void subscribeAll() {
        ECEventBus bus = ECEventBus.global();
        ECSubscribeOptions options = ECSubscribeOptions.defaults();
        SUBSCRIPTIONS.add(bus.subscribe(PlaceActionEvent.Post.class, options, event -> {
            FPlayer placer = event.getContext().getPlayer();
            ProvenanceStore open = store;
            if (placer != null && open != null) {
                open.setBlock(blockOf(event.getContext()), new Provenance(OriginKind.PLAYER_PLACED, placer.getUniqueId(), ECClock.now(),
                        event.getContext().getBlockKey()));
            }
        }));
        SUBSCRIPTIONS.add(bus.subscribe(SpawnActionEvent.Post.class, options, event -> {
            SpawnContext context = event.getContext();
            OriginKind kind = kindOf(context.getReason());
            UUID entity = idOf(context.getEntity());
            ProvenanceStore open = store;
            if (kind != null && entity != null && open != null) {
                open.setEntity(entity, new Provenance(kind, null, ECClock.now(), context.getEntity().getKey()));
            }
        }));
        SUBSCRIPTIONS.add(bus.subscribe(DropActionEvent.Post.class, options, event -> {
            ItemContext context = event.getContext();
            FPlayer dropper = context.getPlayer();
            ProvenanceStore open = store;
            if (dropper != null && context.getItemEntityId() != null && open != null) {
                open.setEntity(context.getItemEntityId(), new Provenance(OriginKind.PLAYER_DROP, dropper.getUniqueId(), ECClock.now(),
                        context.getItemKey()));
            }
        }));
        //last of all: every other subscriber reads the origin of the broken block before the record goes
        SUBSCRIPTIONS.add(bus.subscribe(BreakActionEvent.Post.class, options.withPriority(Short.MAX_VALUE), event -> {
            ProvenanceStore open = store;
            if (open != null) {
                open.removeBlock(blockOf(event.getContext()));
            }
        }));
        SUBSCRIPTIONS.add(bus.subscribe(KillActionEvent.Post.class, options.withPriority(Short.MAX_VALUE), event -> {
            UUID victim = idOf(event.getContext().getVictim());
            ProvenanceStore open = store;
            if (victim != null && open != null) {
                open.forgetEntity(victim);
            }
        }));
        SUBSCRIPTIONS.add(bus.subscribe(PickupActionEvent.Post.class, options.withPriority(Short.MAX_VALUE), event -> {
            UUID item = event.getContext().getItemEntityId();
            ProvenanceStore open = store;
            if (item != null && open != null) {
                open.forgetEntity(item);
            }
        }));
    }

    private static WorldBlockPos blockOf(ActionContext context) {
        return WorldBlockPos.from(context.getPosition(), context.getWorld());
    }

    private static @Nullable UUID idOf(Actor actor) {
        NativeEntity entity = actor.getNativeEntity();
        return entity == null ? null : entity.getUniqueId();
    }

    /** The kind a spawn reason records, or null for one that leaves the creature natural (Hytale has only those). */
    static @Nullable OriginKind kindOf(String reason) {
        switch (reason) {
            case "SPAWNER":
                return OriginKind.SPAWNER;
            case "EGG":
            case "SPAWNER_EGG":
            case "DISPENSE_EGG":
                return OriginKind.EGG;
            case "BREEDING":
                return OriginKind.BREEDING;
            case "COMMAND":
            case "CUSTOM":
                return OriginKind.COMMAND;
            default:
                return null;
        }
    }
}
