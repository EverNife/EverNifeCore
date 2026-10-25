package br.com.finalcraft.evernifecore.action.provenance;

import br.com.finalcraft.evernifecore.EverNifeCore;
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
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.ECSubscribeOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.time.ECClock;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records the origin of blocks placed, creatures spawned and items dropped, as a subscriber of the action events, and
 * answers it later. One per owner, on the owner's storage - or one for everyone when the admin turns
 * {@code Provenance.shared} on, in the core's storage.
 *
 * <pre>{@code
 * ProvenanceTracker origins = ProvenanceTracker.open(getEcPluginData(), storage);
 * ProvenanceHold recording = origins.record();          // while a quest with an origin filter is loaded
 * origins.getOrigin(blockPos).thenAccept(origin -> { if (origin.isOwn(player)) ... });
 * }</pre>
 *
 * <p>Recording is subscribing: while recording, the tracker listens to {@link PlaceActionEvent.Post},
 * {@link SpawnActionEvent.Post}, {@link DropActionEvent.Post}, {@link BreakActionEvent.Post}, {@link KillActionEvent.Post}
 * and {@link PickupActionEvent.Post} - and those subscriptions are what switch the platform's listeners on. A block
 * placed while nobody recorded reads as {@link Provenance#NATURAL}.
 *
 * <p>{@link #getOrigin(UUID)} and {@link #peekOrigin} answer on any thread; {@link #getOrigin(WorldBlockPos)} completes
 * on the storage's thread.
 */
public final class ProvenanceTracker {

    private static final Map<String, ProvenanceTracker> OWNED = new ConcurrentHashMap<>();
    private static final Set<String> TOLD_SHARED = ConcurrentHashMap.newKeySet();
    private static volatile @Nullable ProvenanceTracker shared;
    private static volatile @Nullable ECStorage sharedStorage;

    private final ECPluginData owner;
    private final ECStorage storage;
    private final ProvenanceStore store;
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
    private int holds;
    private @Nullable ProvenanceHold always;

    private ProvenanceTracker(ECPluginData owner, ECStorage storage) {
        this.owner = owner;
        this.storage = storage;
        this.store = new ProvenanceStore(storage, () -> ECSettings.PROVENANCE_RETENTION_PLAYER_PLACED);
        if (ECSettings.PROVENANCE_ALWAYS) {
            always = record();
        }
    }

    /**
     * The tracker {@code owner} uses: the shared one while {@code Provenance.shared} is on (then {@code storage} is
     * ignored), else the owner's own on {@code storage} - the same object on every call with the same storage.
     */
    public static ProvenanceTracker open(ECPluginData owner, ECStorage storage) {
        ProvenanceTracker everyone = shared;
        if (everyone != null) {
            if (TOLD_SHARED.add(owner.getMetaInfo().getName())) {
                EverNifeCore.getLog().info("The provenance of " + owner.getMetaInfo().getName() + " is served by the shared tracker.");
            }
            return everyone;
        }
        ProvenanceTracker[] replaced = new ProvenanceTracker[1];
        ProvenanceTracker tracker = OWNED.compute(owner.getMetaInfo().getName(), (name, existing) -> {
            if (existing != null && existing.storage == storage) {
                return existing;
            }
            replaced[0] = existing;
            return new ProvenanceTracker(owner, storage);
        });
        if (replaced[0] != null) {
            replaced[0].shutdown();
        }
        return tracker;
    }

    /** The tracker {@code owner} opened, or the shared one; null when neither is open. */
    public static @Nullable ProvenanceTracker ofIfPresent(ECPluginData owner) {
        ProvenanceTracker everyone = shared;
        return everyone != null ? everyone : OWNED.get(owner.getMetaInfo().getName());
    }

    /** Stops {@code owner}'s tracker and writes its blocks; called on its shutdown, before its storage closes. */
    public static void closeOwnedBy(ECPluginData owner) {
        TOLD_SHARED.remove(owner.getMetaInfo().getName());
        ProvenanceTracker closed = OWNED.remove(owner.getMetaInfo().getName());
        if (closed != null) {
            closed.shutdown();
        }
    }

    /** Internal: opens the one tracker every plugin shares, on {@code storage}, which it closes with itself. */
    public static synchronized void openShared(ECPluginData core, ECStorage storage) {
        closeShared();
        sharedStorage = storage;
        shared = new ProvenanceTracker(core, storage);
    }

    /** Internal: stops the shared tracker, writes its blocks and closes its storage; nothing when none is open. */
    public static synchronized void closeShared() {
        ProvenanceTracker closing = shared;
        ECStorage storage = sharedStorage;
        shared = null;
        sharedStorage = null;
        TOLD_SHARED.clear();
        if (closing != null) {
            closing.shutdown();
        }
        if (storage != null) {
            storage.close().join();
        }
    }

    /** Keeps this tracker recording until the hold is closed. With {@code Provenance.always} it records regardless. */
    public synchronized ProvenanceHold record() {
        if (holds++ == 0) {
            subscribeAll();
        }
        return new ProvenanceHold(this);
    }

    synchronized void release() {
        if (holds > 0 && --holds == 0) {
            subscriptions.forEach(ECEventSubscription::unsubscribe);
            subscriptions.clear();
        }
    }

    public synchronized boolean isRecording() {
        return holds > 0;
    }

    public ECPluginData getOwner() {
        return owner;
    }

    /** Where the block at {@code block} came from; completes on the storage's thread, the chunk may not be cached. */
    public CompletableFuture<Provenance> getOrigin(WorldBlockPos block) {
        return store.getBlock(block);
    }

    /** The recorded origin of {@code block} if its chunk is cached; null when there is no record in memory. */
    public @Nullable Provenance peekOrigin(WorldBlockPos block) {
        return store.peekBlock(block);
    }

    /** Where the creature or item {@code entity} came from; NATURAL when it was never recorded or left memory. */
    public Provenance getOrigin(UUID entity) {
        return store.getEntity(entity);
    }

    /** Writes the recorded blocks now instead of on the next periodic flush. */
    public CompletableFuture<SVWorldDataManager.FlushReport> flush() {
        return store.flush();
    }

    private void subscribeAll() {
        ECEventBus bus = ECEventBus.global();
        ECSubscribeOptions options = ECSubscribeOptions.ownedBy(owner);
        subscriptions.add(bus.subscribe(PlaceActionEvent.Post.class, options, event -> {
            FPlayer placer = event.getContext().getPlayer();
            if (placer != null) {
                store.setBlock(blockOf(event.getContext()), new Provenance(OriginKind.PLAYER_PLACED, placer.getUniqueId(), ECClock.now(),
                        event.getContext().getBlockKey()));
            }
        }));
        subscriptions.add(bus.subscribe(SpawnActionEvent.Post.class, options, event -> {
            SpawnContext context = event.getContext();
            OriginKind kind = kindOf(context.getReason());
            UUID entity = idOf(context.getEntity());
            if (kind != null && entity != null) {
                store.setEntity(entity, new Provenance(kind, null, ECClock.now(), context.getEntity().getKey()));
            }
        }));
        subscriptions.add(bus.subscribe(DropActionEvent.Post.class, options, event -> {
            ItemContext context = event.getContext();
            FPlayer dropper = context.getPlayer();
            if (dropper != null && context.getItemEntityId() != null) {
                store.setEntity(context.getItemEntityId(), new Provenance(OriginKind.PLAYER_DROP, dropper.getUniqueId(), ECClock.now(),
                        context.getItemKey()));
            }
        }));
        //last of all: every other subscriber reads the origin of the broken block before the record goes
        subscriptions.add(bus.subscribe(BreakActionEvent.Post.class, options.withPriority(Short.MAX_VALUE),
                event -> store.removeBlock(blockOf(event.getContext()))));
        subscriptions.add(bus.subscribe(KillActionEvent.Post.class, options.withPriority(Short.MAX_VALUE), event -> {
            UUID victim = idOf(event.getContext().getVictim());
            if (victim != null) {
                store.forgetEntity(victim);
            }
        }));
        subscriptions.add(bus.subscribe(PickupActionEvent.Post.class, options.withPriority(Short.MAX_VALUE), event -> {
            UUID item = event.getContext().getItemEntityId();
            if (item != null) {
                store.forgetEntity(item);
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

    private void shutdown() {
        ProvenanceHold own = always;
        always = null;
        if (own != null) {
            own.close();
        }
        synchronized (this) {
            holds = 0;
            subscriptions.forEach(ECEventSubscription::unsubscribe);
            subscriptions.clear();
        }
        store.close();
    }
}
