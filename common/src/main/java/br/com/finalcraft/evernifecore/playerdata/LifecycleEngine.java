package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.evernifecore.playerdata.storage.PDSectionBinding;
import br.com.finalcraft.everydatabase.manager.CachingManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The runtime lifecycle of a {@link PlayerController} instance: the periodic flush tick
 * (jittered, with a stuck-flush guard), the durable quit-flush (bounded async pool +
 * storage-down retry queue), the post-quit release of idle section cells and the periodic idle
 * sweep that covers the cells no quit ever reaches.
 */
final class LifecycleEngine {

    /** Base flush period. */
    private static final long BASE_PERIOD_MS = 30_000L; //30 seconds
    /** Random spread (+-) around each period so multi-server installs don't flush in lockstep. */
    private static final long MAX_JITTER_MS = 5_000L;
    /** A flush pass exceeding this is considered stuck (hung backend) and stops blocking the tick. */
    private static final long FLUSH_STUCK_AFTER_MS = 120_000L;

    /** Bounded backlog of quit-flushes that failed on a storage outage; drained on the next tick / recovery. */
    private static final int MAX_RETRY_QUEUE = 10_000;
    /** Backlog size at which the admin is warned - well before entries start being dropped at the cap. */
    private static final int RETRY_QUEUE_WARN_THRESHOLD = 100;

    private final PlayerController controller;

    /** Bounded async pool for quit-flushes (never blocks the platform's quit event). */
    private final ExecutorService quitFlushExecutor = Executors.newFixedThreadPool(
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())),
            daemonFactory("ec-playerdata-quit-flush"));
    /** Single-thread timer for the deferred idle eviction (post-quit grace), the idle sweep and the login timeout. */
    private final ScheduledExecutorService lifecycleScheduler = Executors.newSingleThreadScheduledExecutor(
            daemonFactory("ec-playerdata-lifecycle"));
    /** Dedicated tick thread: the tick BLOCKS on the flush (bounded), which must never stall the scheduler above. */
    private final ScheduledExecutorService flushTickExecutor = Executors.newSingleThreadScheduledExecutor(
            daemonFactory("ec-playerdata-flush-tick"));

    /** Players whose quit-flush hit a storage outage (enqueue-not-drop); re-flushed when storage returns. */
    private final ConcurrentLinkedQueue<UUID> flushRetryQueue = new ConcurrentLinkedQueue<>();
    private final Set<UUID> flushRetryPending = ConcurrentHashMap.newKeySet();
    /** One backlog warning per outage: re-armed when the retry queue fully drains. */
    private final AtomicBoolean retryBacklogWarned = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> idleSweepTask;
    /**
     * Per section class, when each cached key was FIRST seen unused - the clock the idle sweep
     * measures the grace against (a cell whose owner never logs in here gets no quit event).
     */
    private final Map<Class<?>, Map<UUID, Long>> idleSince = new ConcurrentHashMap<>();
    /** The flush pass that exceeded the stuck bound and is still running; no new pass starts over it. */
    private volatile CompletableFuture<Void> stuckFlush;
    private volatile boolean stopped;

    LifecycleEngine(PlayerController controller) {
        this.controller = controller;
    }

    static ThreadFactory daemonFactory(String namePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, namePrefix + "-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /** The shared single-thread scheduler (working-set eviction, ttl purge, login timeout). */
    ScheduledExecutorService scheduler() {
        return lifecycleScheduler;
    }

    int retryBacklogSize() {
        return flushRetryPending.size();
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Periodic flush tick
    // -----------------------------------------------------------------------------------------------------------------------------//

    /** Starts the jittered periodic flush tick for this controller instance. */
    void startPeriodicFlush() {
        //the unit suite drives flushes explicitly - a background tick mid-test would race the
        //scripted repositories and the save-count assertions
        if ("false".equalsIgnoreCase(System.getProperty("evernifecore.playerdata.periodic-flush"))) {
            return;
        }
        scheduleNextTick();
    }

    private void scheduleNextTick() {
        if (stopped) return;
        //jitter: spread the tick so multiple servers sharing a backend don't collide every cycle
        long delay = BASE_PERIOD_MS - MAX_JITTER_MS + ThreadLocalRandom.current().nextLong(2 * MAX_JITTER_MS);
        try {
            flushTickExecutor.schedule(this::runFlushTick, delay, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException alreadyStopped) {
            //stop() raced the reschedule - the executor is gone, nothing left to do
        }
    }

    private void runFlushTick() {
        try {
            CompletableFuture<Void> stuck = stuckFlush;
            if (stuck != null && !stuck.isDone()) {
                //never start an overlapping pass; keep telling the admin the pipeline is stalled
                EverNifeCore.getLog().severe("PlayerData flush is STILL stuck (storage hung?) -"
                        + " nothing is being persisted until the backend answers!");
                return;
            }
            stuckFlush = null;

            drainFlushRetryQueue(); //re-flush players whose quit-flush hit a storage outage
            CompletableFuture<Void> flush = controller.flushAll();
            try {
                //bounded wait: a backend that hangs (instead of failing) must not freeze this
                //thread forever - flushing, retry draining and the reaper all live here
                flush.get(FLUSH_STUCK_AFTER_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException stuckNow) {
                stuckFlush = flush;
                EverNifeCore.getLog().severe("PlayerData flush did not finish within " + (FLUSH_STUCK_AFTER_MS / 1000)
                        + "s (storage hung?) - the tick continues and will resume once the stuck pass completes.");
            }
            //one aggregate line per tick instead of one WARN per player during an outage
            int failures = controller.drainWriteFailureCount();
            if (failures > 0) {
                EverNifeCore.getLog().warning("PlayerData flush: " + failures + " write(s) failed this tick (storage"
                        + " down?) - re-marked dirty and retried next tick. Per-key detail is logged at DEBUG.");
            }
            controller.maybeReapOrphans(); //no-op unless enabled and its interval elapsed (async)
            //first tick: every plugin has enabled by now, so an unclaimed storage.yml entry is real
            controller.reportOrphanSectionEntries();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return; //stop() interrupted the bounded wait - do not reschedule
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("Failed to flush PlayerData to storage, this is a serious problem:");
            e.printStackTrace();
        } finally {
            scheduleNextTick();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Quit: durable flush + storage-down retry
    // -----------------------------------------------------------------------------------------------------------------------------//

    /** Detaches the live player, records the session end and flushes off the quit thread. */
    void handleQuit(PlayerData playerData) {
        UUID uuid = playerData.getUniqueId();
        playerData.setPlayer(null);         //detach the live reference (no dirty, no lastSeen stamp)
        playerData.materializeSessionEnd(); //the one durable write per session: stamp lastSeen + dirty
        //flush off the quit thread; on a storage outage enqueue for a later retry instead of dropping
        quitFlushExecutor.execute(() -> flushPlayerWithRetry(uuid));
        scheduleIdleEviction(uuid);
        controller.accountEngine().scheduleQuitRelease(playerData);
    }

    /**
     * Flushes one player and, on a storage outage, enqueues its uuid for a bounded retry drained on the
     * next tick / when storage returns. The flush future itself does NOT fail on a transient write error
     * (the flush pipeline re-marks the cell dirty and only logs), so the outage is detected by the cell
     * still being dirty after the join - and a re-dirty leaves the retry queued too (at-least-once).
     * Idempotent per uuid while pending. Called from the quit pool and {@link #drainFlushRetryQueue()}.
     */
    private void flushPlayerWithRetry(UUID uuid) {
        PlayerData playerData = controller.baseManager().peek(uuid).orElse(null);
        if (playerData == null) return; //evicted already (working-set) - its dirty state was flushed or re-queued
        boolean stillDirty;
        try {
            controller.flushPlayer(playerData).join();
            stillDirty = isPlayerStillDirty(playerData);
        } catch (Throwable flushFailure) {
            //an unexpected failure (not the logged transient re-dirty): treat as an outage too
            stillDirty = isPlayerStillDirty(playerData);
            if (stillDirty) {
                EverNifeCore.getLog().warning("Quit-flush of PlayerData [{}] failed (storage down?) - queued for retry: {}",
                        uuid, String.valueOf(flushFailure.getMessage()));
            }
        }
        if (stillDirty) {
            //the write did not land (transient storage error) - re-queue instead of dropping
            enqueueFlushRetry(uuid);
        } else {
            flushRetryPending.remove(uuid); //persisted cleanly: clear any pending retry
        }
    }

    /**
     * A player is "still dirty" when its base OR any of its cached sections failed to persist.
     * Ahead-schema entities are excluded: they are deliberately read-only (the flush refuses them
     * forever on this instance), so counting them would poison this retry queue with a player
     * that can never drain.
     */
    private boolean isPlayerStillDirty(PlayerData playerData) {
        if (playerData.isDirty() && !EntitySchemaMigrations.isAhead(playerData)) return true;
        UUID uuid = playerData.getUniqueId();
        for (PDSectionBinding<? extends PDSection> binding : controller.sectionBindings()) {
            PDSection cell = binding.getManager().peek(uuid).orElse(null);
            if (cell != null && cell.isDirty() && !EntitySchemaMigrations.isAhead(cell)) return true;
        }
        UUID accountKey = playerData.getAccountId();
        for (AccountSectionBinding<?> binding : controller.accountEngine().bindings()) {
            AccountSection<?> cell = binding.getManager().peek(accountKey).orElse(null);
            if (cell != null && cell.isDirty() && !EntitySchemaMigrations.isAhead(cell)) return true;
        }
        return false;
    }

    private void enqueueFlushRetry(UUID uuid) {
        if (flushRetryPending.size() >= MAX_RETRY_QUEUE) {
            EverNifeCore.getLog().severe("PlayerData quit-flush retry queue is full ({}) - DROPPING the retry for [{}]."
                    + " Storage has been unavailable for too long; investigate the backend.", MAX_RETRY_QUEUE, uuid);
            return;
        }
        if (flushRetryPending.add(uuid)) {
            flushRetryQueue.add(uuid);
            //warn while there is still room to act - the cap-drop above must never be the first signal
            if (flushRetryPending.size() >= RETRY_QUEUE_WARN_THRESHOLD
                    && retryBacklogWarned.compareAndSet(false, true)) {
                EverNifeCore.getLog().warning("PlayerData quit-flush retry backlog reached {} players (storage down?)."
                                + " Entries start being DROPPED at {} - investigate the backend.",
                        RETRY_QUEUE_WARN_THRESHOLD, MAX_RETRY_QUEUE);
            }
        }
    }

    /**
     * Drains the storage-down quit-flush backlog (called each tick): re-flushes every queued player
     * through the bounded pool. A still-failing flush re-enqueues itself, so the backlog persists
     * across ticks until storage returns. A no-op when the queue is empty.
     */
    void drainFlushRetryQueue() {
        UUID uuid;
        List<UUID> batch = new ArrayList<>();
        while ((uuid = flushRetryQueue.poll()) != null) {
            flushRetryPending.remove(uuid); //re-added by flushPlayerWithRetry if it still fails
            batch.add(uuid);
        }
        if (batch.isEmpty() && flushRetryPending.isEmpty()) {
            retryBacklogWarned.set(false); //backlog fully drained: re-arm the warning for the next outage
        }
        for (UUID queued : batch) {
            quitFlushExecutor.execute(() -> flushPlayerWithRetry(queued));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Idle release (post-quit eviction + the sweep that covers cells whose owner never was online)
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * Schedules the release of a quitting player's cell from every section that releases when idle,
     * after that section's grace - without dropping in-flight saves or a reconnect that lands inside
     * the grace. A {@code RESIDENT}/{@code PRELOADED} section is never released here.
     *
     * <p>This is the FAST path, driven by the quit event. A cell can also be loaded for a player who
     * is not online at all (an admin lookup, a bulk read), and that one never gets a quit - it is
     * {@link #sweepIdleSections()} that releases those.</p>
     */
    private void scheduleIdleEviction(UUID uuid) {
        for (PDSectionBinding<? extends PDSection> binding : controller.sectionBindings()) {
            if (!binding.getLifecycle().releasesWhenIdle()) continue;
            long graceMillis = binding.getIdleGrace().toMillis();
            CachingManager<UUID, ? extends PDSection> manager = binding.getManager();
            Runnable evict = () -> evictIdleCell(uuid, manager, graceMillis);
            if (graceMillis <= 0) {
                evict.run();
            } else {
                lifecycleScheduler.schedule(evict, graceMillis, TimeUnit.MILLISECONDS);
            }
        }
    }

    /** Flushes a player off the caller's thread, through the same bounded pool the quit-flush uses. */
    void flushPlayerOffThread(UUID uuid) {
        quitFlushExecutor.execute(() -> flushPlayerWithRetry(uuid));
    }

    /** Evicts an idle section cell unless the player came back online or the cell is still dirty. */
    private void evictIdleCell(UUID uuid, CachingManager<UUID, ? extends PDSection> manager, long graceMillis) {
        PlayerData playerData = controller.baseManager().peek(uuid).orElse(null);
        if (playerData != null && playerData.isPlayerOnline()) return; //reconnected inside the grace - keep it
        PDSection cell = manager.peek(uuid).orElse(null);
        if (cell != null && cell.isDirty()) {
            //an unflushed write is still pending: don't drop it - flush then re-check after the
            //section's OWN configured grace (not the factory default)
            quitFlushExecutor.execute(() -> flushPlayerWithRetry(uuid));
            lifecycleScheduler.schedule(
                    () -> evictIdleCell(uuid, manager, graceMillis),
                    Math.max(graceMillis, 1000L), TimeUnit.MILLISECONDS);
            return;
        }
        manager.evict(uuid);
    }

    /**
     * Schedules the periodic idle sweep. Re-callable: it cancels/reschedules on the fly (single-thread
     * scheduler, tolerant of a rebind). A no-op while nothing bound releases when idle. The cadence
     * is the shortest configured grace, clamped to [10s, 60s], so a released cell does not linger far
     * past its grace - note one section on a short grace therefore speeds the sweep up for all of them.
     */
    void scheduleIdleSweep() {
        ScheduledFuture<?> previous = idleSweepTask;
        if (previous != null) previous.cancel(false);

        long periodSeconds = 60L;
        boolean anyReleases = false;
        for (IdleReleaseTarget target : controller.idleReleaseTargets()) {
            anyReleases = true;
            periodSeconds = Math.min(periodSeconds, Math.max(10L, target.getGraceMillis() / 1000L));
        }
        if (!anyReleases) return;
        final long period = periodSeconds;
        idleSweepTask = lifecycleScheduler.scheduleWithFixedDelay(
                this::sweepIdleSections, period, period, TimeUnit.SECONDS);
    }

    /**
     * Releases every cached cell - player section or account row - that has been unused for longer
     * than its grace. The quit path already covers whoever just left; this covers the cell loaded for
     * someone who was never online here (an offline lookup, a bulk read, an account aggregate) and
     * therefore has no quit event to key off.
     *
     * <p>A dirty cell is never dropped: it is queued for a flush and re-checked on the next sweep.
     * Best-effort - a failure is logged and never propagates.</p>
     */
    void sweepIdleSections() {
        long now = System.currentTimeMillis();
        Set<Class<?>> swept = new HashSet<>();
        for (IdleReleaseTarget target : controller.idleReleaseTargets()) {
            swept.add(target.getSectionClass());
            try {
                sweepTarget(target, now);
            } catch (Throwable sweepFailure) {
                EverNifeCore.getLog().warning("Idle sweep of section {{}} failed: {}",
                        target.getSectionClass().getSimpleName(), String.valueOf(sweepFailure.getMessage()));
            }
        }
        idleSince.keySet().retainAll(swept);
    }

    private void sweepTarget(IdleReleaseTarget target, long now) {
        CachingManager<UUID, ? extends StoredSection> manager = target.getManager();
        long graceMillis = target.getGraceMillis();
        Map<UUID, Long> unusedSince = idleSince.computeIfAbsent(
                target.getSectionClass(), key -> new ConcurrentHashMap<>());

        List<UUID> release = new ArrayList<>();
        Set<UUID> cached = manager.cachedKeys();
        for (UUID key : cached) {
            if (target.isStillInUse(key)) {
                unusedSince.remove(key);
                continue;
            }
            StoredSection cell = manager.peek(key).orElse(null);
            if (cell != null && cell.isDirty()) {
                //never drop an unflushed write - persist it and re-check on the next sweep
                target.flushDirty(key);
                continue;
            }
            Long since = unusedSince.putIfAbsent(key, now);
            if (since != null && now - since >= graceMillis) {
                release.add(key);
            }
        }
        unusedSince.keySet().retainAll(cached);
        if (!release.isEmpty()) {
            manager.evictAll(release);
            unusedSince.keySet().removeAll(release);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Shutdown
    // -----------------------------------------------------------------------------------------------------------------------------//

    /** Stops the flush tick, the quit-flush pool and the lifecycle scheduler (idle sweep / eviction). */
    void stop() {
        stopped = true;
        ScheduledFuture<?> sweep = idleSweepTask;
        if (sweep != null) sweep.cancel(false);
        flushTickExecutor.shutdownNow();
        lifecycleScheduler.shutdownNow();
        quitFlushExecutor.shutdown();
        try {
            quitFlushExecutor.awaitTermination(5, TimeUnit.SECONDS); //let in-flight quit flushes finish
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
