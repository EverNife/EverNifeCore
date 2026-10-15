package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.region.event.RegionChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionCreatedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionFlagChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionGroupChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionLoadFailedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRedefinedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.region.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.region.flag.FlagValue;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.GroupChange;
import br.com.finalcraft.evernifecore.region.group.Membership;
import br.com.finalcraft.evernifecore.region.overlap.Overlap;
import br.com.finalcraft.evernifecore.region.overlap.OverlapProvider;
import br.com.finalcraft.evernifecore.region.overlap.OverlapProviders;
import br.com.finalcraft.evernifecore.region.overlap.OverlapSource;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.storage.RegionRecord;
import br.com.finalcraft.evernifecore.region.storage.RegionStorage;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.util.retry.RetryPolicy;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The regions of one plugin: creates, changes and removes them, keeps them in the plugin's own {@link ECStorage}
 * and posts a region event for every change. The core never creates a region by itself.
 *
 * <pre>{@code
 * RegionManager regions = RegionManager.of(pluginData, storage);
 * regions.load(RetryPolicy.attempts(5).every(Duration.ofSeconds(2))).join();
 * CreationResult result = regions.create("arena", "world", shape, RegionOptions.defaults().withPriority(10));
 * }</pre>
 *
 * <p>Nothing is created before the regions load: {@link #create} refuses, so a stored region is never written over
 * nor an overlap decided against an index still missing half of them.</p>
 *
 * <p><b>Threads:</b> every method may be called from any thread, on every platform. Writers are serialized by one
 * lock, lookups never wait for it, and every event goes out on the thread of whoever made the change. Storage I/O
 * never runs on the caller's thread: changes are written in the background, a tick after they happen.</p>
 *
 * <p>The core closes the manager when its owner shuts down (writing what is pending first) but never the owner's
 * storage: that stays the owner's to close.</p>
 */
public final class RegionManager {

    static final long DEFAULT_FLUSH_DELAY_MILLIS = 50;
    static final long DEFAULT_RETRY_DELAY_MILLIS = 30_000;
    private static final long CLOSE_TIMEOUT_SECONDS = 30;

    private static final Map<ECPluginData, RegionManager> MANAGERS = new ConcurrentHashMap<>();
    private static volatile long flushDelayMillis = DEFAULT_FLUSH_DELAY_MILLIS;
    private static volatile long retryDelayMillis = DEFAULT_RETRY_DELAY_MILLIS;

    private final ECPluginData owner;
    private final @Nullable ECStorage storage;
    private final @Nullable RegionStorage regionStorage;
    private final Set<Region> dirty = ConcurrentHashMap.newKeySet();
    /** Names whose deletion the backend has not confirmed yet. */
    private final Set<String> removed = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private final Object writeChain = new Object();
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
    private volatile boolean closed;
    private volatile boolean loaded;
    private final CompletableFuture<Void> loadedOnce = new CompletableFuture<>();
    private final Object loadLock = new Object();
    private @Nullable LoadRun run;
    private volatile boolean foreignAccess = true;

    /** @param storage null for a manager that writes nothing - tests that only need regions to exist */
    RegionManager(ECPluginData owner, @Nullable ECStorage storage) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.storage = storage;
        this.regionStorage = storage == null ? null : new RegionStorage(storage);
        if (storage == null) {
            loaded = true;
            loadedOnce.complete(null);
        }
    }

    /**
     * The manager of {@code owner}, created on first call; later calls with the same storage return the same
     * manager. Any thread.
     *
     * @throws IllegalStateException when {@code owner} already has a manager on another storage, or another plugin
     *                               already keeps its regions in {@code storage}
     */
    public static RegionManager of(ECPluginData owner, ECStorage storage) {
        Objects.requireNonNull(owner, "'owner' cannot be null when opening a RegionManager!");
        Objects.requireNonNull(storage, "'storage' cannot be null when opening a RegionManager!");
        followWorlds();
        synchronized (MANAGERS) {
            RegionManager existing = MANAGERS.get(owner);
            if (existing != null) {
                if (existing.storage == storage) {
                    return existing;
                }
                throw new IllegalStateException(nameOf(owner) + " already has a RegionManager on another ECStorage - close() it before"
                        + " opening one on a new storage.");
            }
            for (RegionManager other : MANAGERS.values()) {
                if (other.storage == storage) {
                    throw new IllegalStateException("That ECStorage already keeps the regions of " + nameOf(other.owner) + ": every plugin"
                            + " keeps its regions in its own storage, so open a separate ECStorage for " + nameOf(owner) + ".");
                }
            }
            RegionManager created = new RegionManager(owner, storage);
            MANAGERS.put(owner, created);
            return created;
        }
    }

    /** The open manager of {@code owner}; null when it has none. Any thread. */
    public static @Nullable RegionManager ofIfPresent(ECPluginData owner) {
        return MANAGERS.get(owner);
    }

    public ECPluginData getOwner() {
        return owner;
    }

    /** The owner's storage; null only for a manager that writes nothing. */
    public @Nullable ECStorage getStorage() {
        return storage;
    }

    public boolean isClosed() {
        return closed;
    }

    // ------------------------------------------------------------------
    //  Load
    // ------------------------------------------------------------------

    /**
     * {@link #load(RetryPolicy)} that never gives up, retrying every 30 s - for an owner whose regions are optional.
     * The future answers only the first try: it fails as soon as that try fails, while the retries go on in the
     * background; {@link #whenLoaded()} tells when one of them succeeds.
     */
    public CompletableFuture<Void> load() {
        return startLoad(null);
    }

    /**
     * Reads every region of the owner, of every world, and indexes them with one publish per world, trying again as
     * {@code policy} says. Any thread; the reads run in the background. Every failed try leaves the owner with no
     * regions - nothing they would deny is denied - and posts a {@link RegionLoadFailedEvent}. The future completes
     * on the first try that succeeds, or fails once the policy gives up: then its message names the tries, the time
     * they took and the last cause (also its {@code getCause()}), and nothing retries until {@code load} is called
     * again. A call while a load is in progress reads nothing new: it follows that load, under {@code policy} from
     * then on.
     */
    public CompletableFuture<Void> load(RetryPolicy policy) {
        return startLoad(Objects.requireNonNull(policy, "'policy' cannot be null - load() retries forever every 30 s."));
    }

    /** True once a load succeeded; a manager that stores nothing starts loaded. Lock-free. */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Completes when the first load succeeds - after however many failed tries - and fails only when the manager
     * closes before that. What may only run with every region known, like {@link #create}, waits on it.
     */
    public CompletableFuture<Void> whenLoaded() {
        return follow(loadedOnce);
    }

    private CompletableFuture<Void> startLoad(@Nullable RetryPolicy explicit) {
        synchronized (loadLock) {
            if (regionStorage == null || closed) {
                return CompletableFuture.completedFuture(null);
            }
            LoadRun current = run;
            if (current == null || current.done.isDone()) {
                current = new LoadRun(explicit != null ? explicit : RetryPolicy.forever(Duration.ofMillis(retryDelayMillis)));
                run = current;
                scheduleAttempt(current, 0);
            } else if (explicit != null) {
                current.policy = explicit;
            }
            return follow(explicit != null ? current.done : current.nextAttempt);
        }
    }

    /** Holds the load lock; a newer schedule voids this one, so a world load and the timed retry never both read. */
    private void scheduleAttempt(LoadRun current, long delayMillis) {
        long ticket = ++current.ticket;
        RegionScheduler.scheduleAsync(() -> attempt(current, ticket), delayMillis);
    }

    private void attempt(LoadRun current, long ticket) {
        synchronized (loadLock) {
            if (closed || current.done.isDone() || current.ticket != ticket) {
                return;
            }
            current.attempts++;
            current.reading = true;
        }
        CompletableFuture<List<RegionRecord>> read;
        try {
            read = regionStorage.loadAll();
        } catch (RuntimeException failure) {
            read = new CompletableFuture<>();
            read.completeExceptionally(failure);
        }
        read.whenComplete((records, error) -> {
            if (error == null) {
                try {
                    publishLoaded(records);
                    onLoaded(current);
                    return;
                } catch (RuntimeException failure) {
                    error = failure;
                }
            }
            onLoadFailed(current, error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
        });
    }

    private void onLoaded(LoadRun current) {
        CompletableFuture<Void> attemptDone;
        synchronized (loadLock) {
            current.reading = false;
            attemptDone = current.nextAttempt;
        }
        loadedOnce.complete(null);
        attemptDone.complete(null);
        current.done.complete(null);
    }

    private void onLoadFailed(LoadRun current, Throwable error) {
        ECEventBus.global().postIfListened(RegionLoadFailedEvent.class, () -> new RegionLoadFailedEvent(owner, null, error));
        CompletableFuture<Void> attemptDone;
        Duration elapsed;
        Duration retryIn;
        int attempts;
        RetryPolicy policy;
        synchronized (loadLock) {
            current.reading = false;
            attemptDone = current.nextAttempt;
            current.nextAttempt = new CompletableFuture<>();
            attempts = current.attempts;
            policy = current.policy;
            elapsed = Duration.between(current.startedAt, RegionClock.now());
            //a re-read of an owner already loaded keeps what it has: nothing is missing to retry for
            retryIn = closed || loaded ? null : policy.delayBefore(attempts + 1, elapsed);
            if (retryIn != null) {
                scheduleAttempt(current, retryIn.toMillis());
            }
        }
        attemptDone.completeExceptionally(error);
        if (retryIn != null) {
            owner.getLog().warning("Regions of {} are not loaded ({}); nothing they would deny is denied until they are - try {} failed,"
                    + " the next one runs in {} s.", nameOf(owner), error.toString(), attempts, retryIn.toMillis() / 1000.0);
            return;
        }
        String gaveUp = "Regions of " + nameOf(owner) + " did not load after " + attempts + (attempts == 1 ? " try" : " tries") + " in "
                + elapsed.toMillis() / 1000.0 + " s (" + policy + "); last cause: " + error + ". Nothing retries on its own - call"
                + " load again once the storage is reachable.";
        if (!closed) {
            owner.getLog().warning(gaveUp);
        }
        current.done.completeExceptionally(new IllegalStateException(gaveUp, error));
    }

    /** Every future a load handed out fails instead of staying pending; runs once {@code closed} is set. */
    private void failPendingLoads() {
        LoadRun current;
        synchronized (loadLock) {
            current = run;
        }
        IllegalStateException closedEarly = new IllegalStateException("The RegionManager of " + nameOf(owner)
                + " was closed before its regions loaded - RegionManager.of(owner, storage) opens a new one to load them.");
        if (current != null) {
            current.nextAttempt.completeExceptionally(closedEarly);
            current.done.completeExceptionally(closedEarly);
        }
        loadedOnce.completeExceptionally(closedEarly);
    }

    /** A future of the caller's own: completing or cancelling it leaves {@code source} alone. */
    private static CompletableFuture<Void> follow(CompletableFuture<Void> source) {
        CompletableFuture<Void> copy = new CompletableFuture<>();
        source.whenComplete((ignored, error) -> {
            if (error == null) {
                copy.complete(null);
            } else {
                copy.completeExceptionally(error);
            }
        });
        return copy;
    }

    /** One load, from its first try until it loads or its policy gives up. The mutable fields are guarded by the load lock. */
    private static final class LoadRun {
        final Instant startedAt = RegionClock.now();
        final CompletableFuture<Void> done = new CompletableFuture<>();
        RetryPolicy policy;
        CompletableFuture<Void> nextAttempt = new CompletableFuture<>();
        int attempts;
        long ticket;
        boolean reading;

        LoadRun(RetryPolicy policy) {
            this.policy = policy;
        }
    }

    private void publishLoaded(List<RegionRecord> records) {
        Map<String, Region> byName = new HashMap<>();
        Map<Region, String> parents = new HashMap<>();
        for (RegionRecord record : records) {
            try {
                Region region = RegionRecords.toRegion(this, record);
                byName.put(region.getId().getName(), region);
                if (record.parent != null) {
                    parents.put(region, record.parent);
                }
            } catch (RuntimeException unreadable) {
                owner.getLog().warning("Region '{}' of {} does not read ({}); it is skipped and stays in storage untouched.",
                        record.name, nameOf(owner), unreadable.getMessage());
            }
        }
        parents.forEach((region, parentName) -> {
            Region parent = byName.get(parentName);
            try {
                if (parent == null) {
                    throw new IllegalArgumentException("there is no region '" + parentName + "'");
                }
                region.linkParent(parent);
            } catch (IllegalArgumentException noParent) {
                owner.getLog().warning("Region {} loads with no parent: its parent '{}' cannot be linked ({}).", region.getId(), parentName,
                        noParent.getMessage());
            }
        });
        List<Region> fresh = new ArrayList<>();
        synchronized (RegionIndex.WRITE_LOCK) {
            if (closed) {
                return;
            }
            RegionIndex index = index();
            for (Region region : byName.values()) {
                if (index.byId(region.getId()) == null) {
                    fresh.add(region);
                }
            }
            index.addAll(fresh);
            for (Region region : fresh) {
                region.setAttached(true);
            }
            loaded = true;
        }
        ECDebugModule.REGIONS.debug("{} loaded {} region(s) ({} already loaded).", nameOf(owner), fresh.size(), byName.size() - fresh.size());
        if (!fresh.isEmpty()) {
            RegionSweeper.wake();   //a sweep that finds no deadline stops at once
        }
    }

    // ------------------------------------------------------------------
    //  Create, remove, redefine
    // ------------------------------------------------------------------

    /** {@link #create(String, String, Shape, RegionOptions)} with {@link RegionOptions#defaults()}. */
    public CreationResult create(String name, String world, Shape shape) {
        return create(name, world, shape, RegionOptions.defaults());
    }

    /**
     * Creates and indexes a region, then posts {@code RegionCreatedEvent}. A name in use, an invalid name, a parent
     * of another owner or world, or a policy that refuses one of the overlaps is a refusal in the result, never an
     * exception. Any thread; the event goes out on the caller's. Only once {@link #isLoaded() loaded}: before that
     * the index lacks the stored regions, so the name check and the overlap policy would run against half the truth.
     *
     * @throws IllegalStateException when this manager is closed, or its regions are not loaded yet - create from
     *                               {@code whenLoaded().thenRun(...)}, or check {@link #isLoaded()} first
     */
    public CreationResult create(String name, String world, Shape shape, RegionOptions options) {
        requireOpen();
        if (!loaded) {
            throw new IllegalStateException("The regions of " + nameOf(owner) + " are not loaded yet, so '" + name + "' cannot be created:"
                    + " a stored region of that name or one it overlaps may be missing from the index. Create once they load -"
                    + " regions.whenLoaded().thenRun(...) - or check regions.isLoaded() first; a load that keeps failing shows up"
                    + " as RegionLoadFailedEvent.");
        }
        RegionId id;
        try {
            id = RegionId.of(nameOf(owner), name);
        } catch (IllegalArgumentException invalid) {
            return CreationResult.refused(invalid.getMessage(), Collections.emptyList());
        }
        Region parent = options.getParent();
        if (parent != null && (parent.getManager() != this || !parent.isAttached())) {
            return CreationResult.refused("Region " + id + " cannot have " + parent.getId() + " as parent: a parent must be a live region of the"
                    + " same owner (" + nameOf(owner) + ") - pick one of its regions, or none.", Collections.emptyList());
        }
        if (parent != null && !parent.getWorld().equals(world)) {
            return CreationResult.refused("Region " + id + " cannot have " + parent.getId() + " as parent: a parent must be in the same world"
                    + " ('" + world + "', not '" + parent.getWorld() + "').", Collections.emptyList());
        }
        Region region;
        List<Overlap> overlaps;
        synchronized (RegionIndex.WRITE_LOCK) {
            if (index().byId(id) != null) {
                return CreationResult.refused("Region " + id + " already exists - pick another name, or redefine or remove the existing one.",
                        Collections.emptyList());
            }
            overlaps = dryRun(world, shape, options.getPriority());
            String refusal = options.getPolicy().refusal(overlaps);
            if (refusal != null) {
                return CreationResult.refused(refusal, overlaps);
            }
            region = new Region(this, name, world, shape, options);
            index().add(region);
            region.setAttached(true);
            removed.remove(region.getId().getName());
        }
        markDirty(region);
        if (region.getExpiresAt() != null) {
            RegionSweeper.wake();
        }
        ECEventBus.global().postIfListened(RegionCreatedEvent.class, () -> new RegionCreatedEvent(region, overlaps));
        return CreationResult.created(region, overlaps);
    }

    /**
     * What a region with {@code shape} in {@code world} would overlap: our regions (by bounding box beyond cuboids,
     * see {@link RegionLookup#in}) and what every {@link OverlapProvider} reports. Only reads. Any thread.
     */
    public List<Overlap> dryRun(String world, Shape shape) {
        return dryRun(world, shape, 0);
    }

    private List<Overlap> dryRun(String world, Shape shape, int priority) {
        List<Overlap> overlaps = new ArrayList<>();
        for (Region other : RegionLookup.get().in(world, shape)) {
            OverlapSource source = other.getOwner() == owner ? OverlapSource.OURS_SAME_OWNER : OverlapSource.OURS_OTHER_OWNER;
            overlaps.add(new Overlap(source, other.getId().toString(), other.getId().getOwner(), other.getPriority(), other.getPriority() > priority));
        }
        for (OverlapProvider provider : OverlapProviders.all()) {
            try {
                overlaps.addAll(provider.findOverlaps(world, shape));
            } catch (RuntimeException broken) {
                owner.getLog().warning("The {} overlap provider failed and is left out of this check: {}", provider.getSource(), broken.toString());
            }
        }
        return overlaps;
    }

    /**
     * Removes a region that has no children, from the index and from storage, then posts {@code RegionRemovedEvent}
     * with cause {@code API}. Removing a region already removed does nothing. Any thread.
     *
     * @throws IllegalStateException when the region has children - the message names the two ways out
     */
    public void remove(Region region) {
        requireOwn(region);
        int children = childrenOf(region).size();
        if (children > 0) {
            throw new IllegalStateException("Region " + region.getId() + " has " + children + " children - remove(region, RemoveStrategy.ORPHAN_CHILDREN)"
                    + " detaches them, CASCADE removes them too.");
        }
        remove(region, RemoveStrategy.ORPHAN_CHILDREN, RegionRemovedEvent.Cause.API);
    }

    /** Removes a region and handles its children as {@code strategy} says. Any thread. */
    public void remove(Region region, RemoveStrategy strategy) {
        requireOwn(region);
        remove(region, strategy, RegionRemovedEvent.Cause.API);
    }

    void remove(Region region, RemoveStrategy strategy, RegionRemovedEvent.Cause cause) {
        List<Region> gone = new ArrayList<>();
        List<Region> orphans = new ArrayList<>();
        synchronized (RegionIndex.WRITE_LOCK) {
            if (!region.isAttached()) {
                return;
            }
            if (strategy == RemoveStrategy.CASCADE) {
                for (Region other : getAll()) {
                    if (other.descendsFrom(region)) {
                        gone.add(other);
                    }
                }
                gone.sort(Comparator.comparingInt(Region::getDepth).reversed());
            } else {
                for (Region child : childrenOf(region)) {
                    child.linkParent(null);
                    republishSubtree(child);
                    orphans.add(child);
                }
            }
            gone.add(region);
            for (Region leaving : gone) {
                detach(leaving);
            }
        }
        for (Region orphan : orphans) {
            changed(orphan, RegionChangedEvent.Type.PARENT);
        }
        for (Region leaving : gone) {
            ECEventBus.global().postIfListened(RegionRemovedEvent.class, () -> new RegionRemovedEvent(leaving, cause));
        }
        scheduleFlush();
    }

    /** Takes a region out of the index and, when it is stored, schedules its deletion. Holds the write lock. */
    private void detach(Region region) {
        index().remove(region);
        region.setAttached(false);
        dirty.remove(region);
        if (region.getPersistence() == Persistence.PERSISTENT && regionStorage != null) {
            removed.add(region.getId().getName());
        }
    }

    /**
     * Gives a region a new shape and re-indexes it, then posts {@code RegionRedefinedEvent}. Any thread.
     *
     * @throws IllegalStateException when the region was removed
     */
    public void redefine(Region region, Shape shape) {
        requireOwn(region);
        Objects.requireNonNull(shape, "'shape' cannot be null when redefining a region!");
        Shape old;
        synchronized (RegionIndex.WRITE_LOCK) {
            if (!region.isAttached()) {
                throw new IllegalStateException("Region " + region.getId() + " was removed, so it has no shape to change - create it again.");
            }
            old = region.getShape();
            region.setShape(shape);
            index().replace(region);
        }
        markDirty(region);
        ECEventBus.global().postIfListened(RegionRedefinedEvent.class, () -> new RegionRedefinedEvent(region, old, shape));
    }

    // ------------------------------------------------------------------
    //  Queries
    // ------------------------------------------------------------------

    /** The owner's region named {@code name}; null when there is none or the name is invalid. Any thread. */
    public @Nullable Region get(String name) {
        try {
            Region region = RegionLookup.get().byId(RegionId.of(nameOf(owner), name));
            return region != null && region.getManager() == this ? region : null;
        } catch (IllegalArgumentException invalidName) {
            return null;
        }
    }

    /** Every live region of the owner, unordered. Any thread. */
    public Collection<Region> getAll() {
        return mine(RegionLookup.get().byOwner(owner));
    }

    /** The owner's regions in {@code world}, in lookup order. Any thread. */
    public Collection<Region> getByWorld(String world) {
        return mine(RegionLookup.get().byWorld(world));
    }

    public Collection<Region> getByTag(String tag) {
        return mine(RegionLookup.get().byTag(tag));
    }

    private Collection<Region> mine(Collection<Region> regions) {
        List<Region> mine = new ArrayList<>();
        for (Region region : regions) {
            if (region.getManager() == this) {
                mine.add(region);
            }
        }
        return mine.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(mine);
    }

    private List<Region> childrenOf(Region region) {
        List<Region> children = new ArrayList<>();
        for (Region other : getAll()) {
            if (other.getParent() == region) {
                children.add(other);
            }
        }
        return children;
    }

    // ------------------------------------------------------------------
    //  Foreign access
    // ------------------------------------------------------------------

    /**
     * Whether other plugins' region commands may change this owner's regions. Never stored - set it on every
     * enable. The API itself checks nothing: a plugin calling {@code setFlag} on another owner's region writes.
     */
    public void setForeignAccess(boolean allowed) {
        this.foreignAccess = allowed;
    }

    /** True unless the owner said otherwise. */
    public boolean isForeignAccessAllowed() {
        return foreignAccess;
    }

    // ------------------------------------------------------------------
    //  Writes
    // ------------------------------------------------------------------

    /** Writes every pending change and deletion; completes once the backend confirmed them all. Any thread. */
    public CompletableFuture<Void> flush() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        RegionScheduler.scheduleAsync(() -> write().whenComplete((ignored, error) -> {
            if (error == null) {
                done.complete(null);
            } else {
                done.completeExceptionally(error);
            }
        }), 0);
        return done;
    }

    /**
     * Writes what is pending, then takes every region of the owner out of the index ({@code OWNER_DISABLED}) and
     * forgets the manager. The storage stays open. Any thread; the core calls it when the owner shuts down.
     */
    public CompletableFuture<Void> close() {
        synchronized (MANAGERS) {
            if (closed) {
                return CompletableFuture.completedFuture(null);
            }
            closed = true;
            MANAGERS.remove(owner, this);
        }
        failPendingLoads();
        List<Region> gone = takeAllOut();
        for (Region region : gone) {
            ECEventBus.global().postIfListened(RegionRemovedEvent.class, () -> new RegionRemovedEvent(region, RegionRemovedEvent.Cause.OWNER_DISABLED));
        }
        ECDebugModule.REGIONS.debug("{} closed its RegionManager: {} region(s) dropped from the index.", nameOf(owner), gone.size());
        return flush();
    }

    private List<Region> takeAllOut() {
        List<Region> gone;
        synchronized (RegionIndex.WRITE_LOCK) {
            gone = new ArrayList<>(getAll());
            for (Region region : gone) {
                index().remove(region);
                region.setAttached(false);
            }
        }
        return gone;
    }

    void markDirty(Region region) {
        if (region.getPersistence() == Persistence.TRANSIENT || regionStorage == null || closed || !region.isAttached()) {
            return;
        }
        dirty.add(region);
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (regionStorage != null && flushScheduled.compareAndSet(false, true)) {
            RegionScheduler.scheduleAsync(this::write, flushDelayMillis);
        }
    }

    /** Runs after every write already started, so an older state of a region never lands after a newer one. */
    private CompletableFuture<Void> write() {
        synchronized (writeChain) {
            lastWrite = lastWrite.handle((ignored, error) -> null).thenCompose(ignored -> writeNow());
            return lastWrite;
        }
    }

    private CompletableFuture<Void> writeNow() {
        flushScheduled.set(false);
        if (regionStorage == null) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Throwable>> deletes = new ArrayList<>();
        for (String name : new ArrayList<>(removed)) {
            deletes.add(regionStorage.delete(name).handle((ignored, error) -> {
                if (error == null) {
                    removed.remove(name);
                } else {
                    owner.getLog().warning("Region '{}' of {} could not be deleted from storage ({}); the next write tries again.", name,
                            nameOf(owner), error.toString());
                }
                return error;
            }));
        }
        return allErrors(deletes).thenCompose(deleteErrors -> {
            List<CompletableFuture<Throwable>> saves = new ArrayList<>();
            for (Region region : new ArrayList<>(dirty)) {
                if (!dirty.remove(region)) {
                    continue;
                }
                RegionRecord record = RegionRecords.toRecord(region);
                saves.add(regionStorage.save(record).handle((ignored, error) -> {
                    if (error != null) {
                        if (region.isAttached()) {
                            dirty.add(region);
                        }
                        owner.getLog().warning("Region {} could not be written to storage ({}); the next write tries again.", region.getId(),
                                error.toString());
                    }
                    return error;
                }));
            }
            int deleted = deletes.size();
            return allErrors(saves).thenApply(saveErrors -> {
                ECDebugModule.REGIONS.debug("{} wrote {} region(s) and deleted {}.", nameOf(owner), saves.size(), deleted);
                Throwable first = deleteErrors != null ? deleteErrors : saveErrors;
                if (first != null) {
                    throw new CompletionException(first);
                }
                return null;
            });
        });
    }

    /** Completes with the first error among {@code futures}, or null when none failed. */
    private static CompletableFuture<Throwable> allErrors(List<CompletableFuture<Throwable>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(ignored -> {
            for (CompletableFuture<Throwable> future : futures) {
                Throwable error = future.join();
                if (error != null) {
                    return error;
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    //  What regions report
    // ------------------------------------------------------------------

    void reprioritize(Region region, int priority) {
        synchronized (RegionIndex.WRITE_LOCK) {
            region.setPriorityField(priority);
            republishSubtree(region);
        }
        changed(region, RegionChangedEvent.Type.PRIORITY);
    }

    void reparent(Region region, @Nullable Region parent) {
        synchronized (RegionIndex.WRITE_LOCK) {
            region.linkParent(parent);
            republishSubtree(region);
        }
        changed(region, RegionChangedEvent.Type.PARENT);
    }

    /** Priority and depth order the buckets, and a region's depth is its descendants' too: the whole subtree moves. */
    private void republishSubtree(Region region) {
        if (!region.isAttached()) {
            return;
        }
        RegionIndex index = index();
        index.replace(region);
        for (Region other : getAll()) {
            if (other.descendsFrom(region)) {
                index.replace(other);
            }
        }
    }

    void changed(Region region, RegionChangedEvent.Type type) {
        if (!region.isAttached()) {
            return;
        }
        markDirty(region);
        if (type == RegionChangedEvent.Type.EXPIRES_AT && region.getExpiresAt() != null) {
            RegionSweeper.wake();
        }
        ECEventBus.global().postIfListened(RegionChangedEvent.class, () -> new RegionChangedEvent(region, type));
    }

    void flagChanged(Region region, @Nullable AccessGroup group, RegionFlag<?> flag, @Nullable FlagValue<?> oldValue,
                     @Nullable FlagValue<?> newValue, FlagChangeCause cause) {
        if (!region.isAttached()) {
            return;
        }
        markDirty(region);
        if (newValue != null && newValue.getExpiresAt() != null) {
            RegionSweeper.wake();
        }
        if (cause == FlagChangeCause.EXPIRED) {
            ECDebugModule.REGIONS.debug("Region {}{}: the temporary value of {} expired (now {}).", region.getId(),
                    group == null ? "" : "/" + group.getName(), flag.getKey(), newValue == null ? "unset" : newValue);
        }
        ECEventBus.global().postIfListened(RegionFlagChangedEvent.class,
                () -> new RegionFlagChangedEvent(region, group, flag, oldValue, newValue, cause));
    }

    void groupChanged(Region region, AccessGroup group, GroupChange change, @Nullable UUID player) {
        if (!region.isAttached()) {
            return;
        }
        if (change != GroupChange.PREDICATE_SET) {   //the predicate is code, never stored
            markDirty(region);
        }
        if (change == GroupChange.MEMBER_ADDED && player != null) {
            Membership membership = group.getMembership(player);
            if (membership != null && membership.getExpiresAt() != null) {
                RegionSweeper.wake();
            }
        }
        if (change == GroupChange.MEMBER_EXPIRED) {
            ECDebugModule.REGIONS.debug("Region {}/{}: the membership of {} expired.", region.getId(), group.getName(), player);
        }
        ECEventBus.global().postIfListened(RegionGroupChangedEvent.class, () -> new RegionGroupChangedEvent(region, group, change, player));
    }

    // ------------------------------------------------------------------
    //  Lifecycle of owners
    // ------------------------------------------------------------------

    /**
     * Internal: closes the manager of {@code owner}, waiting up to 30 s for its writes, and drops its overlap
     * providers. The core calls it as the owner starts shutting down, while the owner's storage is still open.
     */
    public static void closeOwnedBy(ECPluginData owner) {
        dropHandlersOf(owner);
        RegionManager manager = MANAGERS.get(owner);
        if (manager != null) {
            try {
                manager.close().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException slow) {
                owner.getLog().warning("Regions of {} were not all written within {} s of its shutdown; what was not written is lost.",
                        nameOf(owner), CLOSE_TIMEOUT_SECONDS);
            } catch (ExecutionException failed) {
                owner.getLog().warning("Regions of {} could not all be written on shutdown: {}", nameOf(owner), failed.getCause().toString());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        OverlapProviders.unregisterAll(owner);
    }

    /**
     * Internal: the safety net for an owner whose storage already closed - takes its regions out of the index
     * ({@code OWNER_DISABLED}) and writes nothing; pending changes are dropped and logged.
     */
    public static void discardOwnedBy(ECPluginData owner) {
        dropHandlersOf(owner);
        RegionManager manager;
        synchronized (MANAGERS) {
            manager = MANAGERS.remove(owner);
            if (manager != null) {
                manager.closed = true;
            }
        }
        if (manager != null) {
            manager.failPendingLoads();
            List<Region> gone = manager.takeAllOut();
            for (Region region : gone) {
                ECEventBus.global().postIfListened(RegionRemovedEvent.class, () -> new RegionRemovedEvent(region, RegionRemovedEvent.Cause.OWNER_DISABLED));
            }
            if (!manager.dirty.isEmpty() || !manager.removed.isEmpty()) {
                ECDebugModule.REGIONS.debug("{} disabled before its regions were written: {} change(s) and {} deletion(s) are dropped. A plugin"
                        + " implementing IECPluginBootstrap gets them written before its storage closes.", nameOf(owner),
                        manager.dirty.size(), manager.removed.size());
            }
            manager.dirty.clear();
            manager.removed.clear();
        }
        OverlapProviders.unregisterAll(owner);
    }

    /** The handlers a plugin set on any region - its own or another's - hold its classes: they go with it. */
    private static void dropHandlersOf(ECPluginData owner) {
        for (Region region : RegionLookup.get().all()) {
            region.removeFlagHandlersOf(owner);
        }
    }

    /**
     * Internal: ends every open selection, closes every open manager and stops everything the region system hangs on
     * the global bus - the sweeper, the event-source count and the movement tracker - the core's own shutdown. Each
     * starts again on its next use.
     */
    public static void closeAll() {
        SelectionTool.cancelAll();   //first: a session holds INTERACT on the count about to stop
        for (ECPluginData owner : new ArrayList<>(MANAGERS.keySet())) {
            closeOwnedBy(owner);
        }
        RegionSweeper.reset();
        EventSourceUsage.shutdown();
        RegionMovementTracker.shutdown();
    }

    // ------------------------------------------------------------------
    //  Worlds
    // ------------------------------------------------------------------

    private static volatile @Nullable IPlatform followedPlatform;

    /** Hooks the platform's world load and unload once - again only if the platform itself was replaced. */
    private static void followWorlds() {
        IPlatform platform = EverNifeCore.getPlatform();
        if (platform == null || platform == followedPlatform) {
            return;
        }
        synchronized (MANAGERS) {
            if (platform == followedPlatform) {
                return;
            }
            platform.onWorldLoaded(world -> {
                for (RegionManager manager : all()) {
                    manager.onWorldLoaded(world);
                }
            });
            platform.onWorldUnloaded(world -> {
                for (RegionManager manager : all()) {
                    manager.onWorldUnloaded(world);
                }
            });
            followedPlatform = platform;
        }
    }

    /**
     * Every region is indexed by {@link #load()} whatever its world - the platform cannot tell which worlds are
     * loaded, and a world nobody stands in costs nothing - so a world load only brings forward the retry of a load
     * that failed, as one more try of its policy. A load whose policy gave up stays given up.
     */
    private void onWorldLoaded(String world) {
        synchronized (loadLock) {
            LoadRun current = run;
            if (current != null && current.attempts > 0 && !current.reading && !current.done.isDone() && !loaded && !closed) {
                scheduleAttempt(current, 0);
            }
        }
    }

    /** Transient regions die with their world; the persistent ones are written and stay indexed. */
    private void onWorldUnloaded(String world) {
        for (Region region : getByWorld(world)) {
            if (region.getPersistence() == Persistence.TRANSIENT) {
                remove(region, RemoveStrategy.ORPHAN_CHILDREN, RegionRemovedEvent.Cause.WORLD_UNLOADED);
            }
        }
        flush();
    }

    // ------------------------------------------------------------------
    //  Internals
    // ------------------------------------------------------------------

    static Collection<RegionManager> all() {
        return MANAGERS.values();
    }

    static void setFlushDelayMillis(long millis) {
        flushDelayMillis = millis;
    }

    static void setRetryDelayMillis(long millis) {
        retryDelayMillis = millis;
    }

    private static RegionIndex index() {
        return RegionLookup.get().getIndex();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("The RegionManager of " + nameOf(owner) + " is closed - RegionManager.of(owner, storage) opens a new one.");
        }
    }

    private void requireOwn(Region region) {
        if (region.getManager() != this) {
            throw new IllegalArgumentException("Region " + region.getId() + " belongs to the RegionManager of " + nameOf(region.getOwner())
                    + " - change it through region.getManager().");
        }
    }

    private static String nameOf(ECPluginData owner) {
        return owner.getMetaInfo().getName();
    }

    @Override
    public String toString() {
        return "RegionManager[" + nameOf(owner) + (closed ? ", closed" : "") + "]";
    }
}
