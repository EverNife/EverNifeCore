package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionAgenda;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.RegionTime;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.everydatabase.Storages;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One plugin that owns regions, for a test: its plugin data, an in-memory storage that counts calls, its
 * {@link RegionManager}, and - shared by every {@code RegionWorld} open at once - a clock that only moves when told
 * and an agenda that only runs when told. Needs a platform with {@code mainThreadInline()} and {@code worldEvents()}.
 *
 * <pre>{@code
 * try (RegionWorld prot = RegionWorld.open("FakeProt", tempDir)) {
 *     Region arena = prot.cuboid("arena", "world", 0, 0, 0, 10, 10, 10);
 *     prot.advance(Duration.ofMinutes(6));
 *     prot.runDue();
 * }
 * }</pre>
 *
 * Two worlds open in the same test are two owners over the one global lookup.
 */
public final class RegionWorld implements AutoCloseable {

    private static final Object SHARED_LOCK = new Object();
    private static Shared shared;

    private final ECPluginData owner;
    private final String ownerName;
    private final ScriptedStorage backend;
    private final ECStorage storage;
    private final RegionManager manager;
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
    private final Map<Class<?>, List<?>> recorded = new HashMap<>();
    private boolean closed;

    private RegionWorld(String ownerName, Path dataFolder) {
        this.ownerName = ownerName;
        acquireShared();
        IECPluginExtractor previous = EverNifeCore.getProviders().getBaseProvider().provideOrNull(IECPluginExtractor.class);
        this.owner = Plugins.fakePluginData(ownerName, dataFolder.toFile());
        if (previous != null) {
            //the owner is built; whoever answered for classes before keeps answering for them
            EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class, previous);
        }
        this.backend = ScriptedStorage.wrapping(Storages.createInMemory());
        backend.init().join();
        this.storage = backend.asECStorage();
        this.manager = RegionManager.of(owner, storage);
    }

    /** Opens the regions of a fake plugin named {@code ownerName} whose data folder is {@code dataFolder}. */
    public static RegionWorld open(String ownerName, Path dataFolder) {
        return new RegionWorld(ownerName, dataFolder);
    }

    public ECPluginData getOwner() {
        return owner;
    }

    public RegionManager getManager() {
        return manager;
    }

    /** The global lookup every manager publishes to. */
    public RegionLookup getLookup() {
        return RegionLookup.get();
    }

    public ECStorage getStorage() {
        return storage;
    }

    /** The backend under {@link #getStorage()}, for {@code callsTo("save")}. */
    public ScriptedStorage getBackend() {
        return backend;
    }

    /**
     * Creates a box in {@code world} through the manager.
     *
     * @throws AssertionError naming the refusal when the manager refused it
     */
    public Region cuboid(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        CreationResult result = manager.create(name, world, CuboidShape.of(BlockPos.of(x1, y1, z1), BlockPos.of(x2, y2, z2)));
        if (!result.isCreated()) {
            throw new AssertionError("The region [" + name + "] was refused: " + result.getRefusal() + " - " + result);
        }
        return result.getRegion();
    }

    /** What {@link RegionTime#now()} answers - fixed until {@link #advance(Duration)}. */
    public Instant now() {
        return shared.clock.instant();
    }

    /** Moves the region clock forward; nothing runs until {@link #runDue()}. */
    public void advance(Duration by) {
        shared.clock.now = shared.clock.now.plus(by);
    }

    /**
     * Runs every task on the region agenda whose delay has passed by the clock, once. A task they schedule waits for
     * the next call, even with no delay - so a self-rescheduling task runs once per call.
     *
     * @return how many tasks ran
     */
    public int runDue() {
        List<Scheduled> due = new ArrayList<>();
        synchronized (shared.pending) {
            Instant now = now();
            for (Iterator<Scheduled> it = shared.pending.iterator(); it.hasNext(); ) {
                Scheduled scheduled = it.next();
                if (!scheduled.dueAt.isAfter(now)) {
                    due.add(scheduled);
                    it.remove();
                }
            }
        }
        for (Scheduled scheduled : due) {
            scheduled.task.run();
        }
        return due.size();
    }

    /** How many tasks wait on the agenda, due or not. */
    public int getPendingTasks() {
        synchronized (shared.pending) {
            return shared.pending.size();
        }
    }

    /** Writes every pending change of this owner now, running the agenda for it. */
    public void flush() {
        CompletableFuture<Void> done = manager.flush();
        for (int round = 0; !done.isDone() && round < 100; round++) {
            runDue();
        }
        done.join();
    }

    /** Every event of {@code type} posted on the global bus since the first call, in order; the list is live. */
    @SuppressWarnings("unchecked")
    public synchronized <T extends IECEvent> List<T> recorded(Class<T> type) {
        List<T> seen = (List<T>) recorded.get(type);
        if (seen == null) {
            List<T> created = new CopyOnWriteArrayList<>();
            subscriptions.add(ECEventBus.global().subscribe(type, created::add));
            recorded.put(type, created);
            seen = created;
        }
        return seen;
    }

    /** Closes the manager (its regions leave the index) and the storage, and forgets the plugin; the last one open restores clock and agenda. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        subscriptions.forEach(ECEventSubscription::unsubscribe);
        CompletableFuture<Void> closing = manager.close();
        for (int round = 0; !closing.isDone() && round < 100; round++) {
            runDue();
        }
        closing.join();
        storage.close().join();
        Plugins.forget(ownerName);
        releaseShared();
    }

    private static void acquireShared() {
        synchronized (SHARED_LOCK) {
            if (shared == null) {
                shared = new Shared();
                RegionTime.setClock(shared.clock);
                RegionAgenda.set((task, delayMillis) -> {
                    synchronized (shared.pending) {
                        shared.pending.add(new Scheduled(task, shared.clock.now.plusMillis(Math.max(0, delayMillis))));
                    }
                });
            }
            shared.users++;
        }
    }

    private static void releaseShared() {
        synchronized (SHARED_LOCK) {
            if (--shared.users == 0) {
                RegionAgenda.set(null);
                RegionTime.setClock(null);
                shared = null;
            }
        }
    }

    private static final class Shared {
        final MovableClock clock = new MovableClock(Instant.parse("2026-10-06T12:00:00Z"));
        final List<Scheduled> pending = new ArrayList<>();
        int users;
    }

    private static final class Scheduled {
        final Runnable task;
        final Instant dueAt;

        Scheduled(Runnable task, Instant dueAt) {
            this.task = task;
            this.dueAt = dueAt;
        }
    }

    private static final class MovableClock extends Clock {
        volatile Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
