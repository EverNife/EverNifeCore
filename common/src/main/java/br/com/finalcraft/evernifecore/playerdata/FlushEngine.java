package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.cooldown.server.ServerCooldowns;
import br.com.finalcraft.evernifecore.playerdata.storage.PDSectionBinding;
import br.com.finalcraft.evernifecore.playerdata.storage.SectionLifecycle;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.log.ManagerLog;
import br.com.finalcraft.everydatabase.manager.writeback.ConflictHooks;
import br.com.finalcraft.everydatabase.manager.writeback.FlushMode;
import br.com.finalcraft.everydatabase.manager.writeback.OptimisticConflictException;
import br.com.finalcraft.everydatabase.manager.writeback.WriteBackFlusher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * The flush + conflict pipeline of a {@link PlayerController} instance: decides WHAT to flush
 * (the dirty base entities and every non-frozen section) and hands each set to the generic
 * {@link WriteBackFlusher}, which persists it in ONE batch per manager and resolves an
 * optimistic-lock conflict into the SAME live instance. The per-type {@link ConflictHooks} below
 * are the only playerdata-specific part, so the base and section paths can never drift apart.
 */
final class FlushEngine {

    /** Routes the generic flusher's messages to the playerdata logger, keeping FINE at debug level. */
    private static final ManagerLog PD_LOG = (level, message) -> {
        if (level.intValue() <= Level.FINE.intValue()) {
            PDLog.debug(message);
        } else {
            PDLog.log(level, message);
        }
    };

    private final PlayerController controller;
    /** Owns the batch persist, the conflict resolution and the write health counters. */
    private final WriteBackFlusher flusher = new WriteBackFlusher(PD_LOG);

    /** Throttles the resident "cache too large" DEBUG warning to at most one per manager per flush window. */
    private final Set<String> residentWarned = ConcurrentHashMap.newKeySet();

    FlushEngine(PlayerController controller) {
        this.controller = controller;
    }

    int conflictsAdoptedCount() {
        return flusher.conflictsAdoptedCount();
    }

    long lastWriteFailureAt() {
        return flusher.lastWriteFailureAt();
    }

    /** Failed-write count since the last call; the periodic tick logs ONE aggregate line per tick. */
    int drainWriteFailureCount() {
        return flusher.drainWriteFailureCount();
    }

    private static FlushMode mode(boolean forced) {
        return forced ? FlushMode.FORCED : FlushMode.BACKGROUND;
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Entry points
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * Background flush: the dirty base entities (batch) and every non-frozen section (one batch
     * per manager). A conflict is resolved by ADOPT_WINNER (re-adopt the stored winner into the
     * live instance) and only LOGGED - the returned future never fails on a conflict (it is the
     * periodic/shutdown path, not a caller).
     */
    CompletableFuture<Void> flushAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        CompletableFuture<Void> base = flushBaseEntities(collectDirtyBase(controller.baseManager().cachedValues()), false);
        if (base != null) futures.add(base);

        for (PDSectionBinding<? extends PDSection> binding : controller.sectionBindings()) {
            if (binding.getManager().isFrozen()) continue; //mid-transfer: dirty accumulates
            warnIfResidentTooLarge(binding);
            CompletableFuture<Void> sections = flushDirtyEntities(binding.getManager(), what(binding), false, SECTION_HOOKS);
            if (sections != null) futures.add(sections);
        }

        for (AccountSectionBinding<?> binding : controller.accountEngine().bindings()) {
            CompletableFuture<Void> sections = flushDirtyEntities(binding.getManager(), what(binding), false, ACCOUNT_HOOKS);
            if (sections != null) futures.add(sections);
        }

        //network cooldowns write through on every mutation, so this only retries what did not land
        ServerCooldowns serverCooldowns = ServerCooldowns.get();
        if (serverCooldowns != null) {
            futures.add(serverCooldowns.flushDirty());
        }

        if (futures.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Immediate flush of a single player (its base entity plus its dirty section in each non-frozen
     * manager). Caller-initiated ({@code forceSavePlayerData}): a conflict completes the returned
     * future EXCEPTIONALLY (with {@link OptimisticConflictException}) after the winner is adopted.
     */
    CompletableFuture<Void> flushPlayer(PlayerData playerData) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        CompletableFuture<Void> base = flushBaseEntities(collectDirtyBase(Collections.singletonList(playerData)), true);
        if (base != null) futures.add(base);

        UUID uuid = playerData.getUniqueId();
        for (PDSectionBinding<? extends PDSection> binding : controller.sectionBindings()) {
            if (binding.getManager().isFrozen()) continue;
            CompletableFuture<Void> sectionFuture = flushSingleEntity(binding.getManager(), uuid, what(binding), true, SECTION_HOOKS);
            if (sectionFuture != null) futures.add(sectionFuture);
        }

        UUID accountKey = playerData.getAccountId();
        for (AccountSectionBinding<?> binding : controller.accountEngine().bindings()) {
            CompletableFuture<Void> accountFuture = flushSingleEntity(binding.getManager(), accountKey, what(binding), true, ACCOUNT_HOOKS);
            if (accountFuture != null) futures.add(accountFuture);
        }

        if (futures.isEmpty()) return CompletableFuture.completedFuture(null);
        return allSurfacingEveryFailure(futures);
    }

    /**
     * Immediate flush of a SINGLE account section row - the account-family counterpart of
     * {@link #flushSection(PDSection)}. Caller-initiated: a conflict completes the returned future
     * EXCEPTIONALLY (with {@link OptimisticConflictException}) after the winner is merged in.
     */
    CompletableFuture<Void> flushAccountSection(AccountSection<?> section) {
        AccountSectionBinding<?> binding = controller.accountEngine().bindings().stream()
                .filter(candidate -> candidate.getSectionClass() == section.getClass())
                .findFirst().orElse(null);
        if (binding == null) {
            return PlayerController.failedFuture(PlayerController.notRegisteredAccountSection(section.getClass()));
        }
        CompletableFuture<Void> future = flushSingleEntity(binding.getManager(), section.getAccountId(), what(binding), true, ACCOUNT_HOOKS);
        return future != null ? future : CompletableFuture.completedFuture(null);
    }

    /**
     * Immediate flush of a SINGLE section in isolation ({@code forceSavePDSection}) - the base
     * PlayerData is never touched. Caller-initiated: a conflict completes the returned future
     * EXCEPTIONALLY (with {@link OptimisticConflictException}) after the winner is adopted.
     */
    CompletableFuture<Void> flushSection(PDSection section) {
        Class<? extends PDSection> pdSectionClass = section.getClass();
        PDSectionBinding<? extends PDSection> binding = controller.getBinding(pdSectionClass);
        if (binding == null) {
            return PlayerController.failedFuture(PlayerController.notRegisteredPDSection(pdSectionClass));
        }
        if (binding.getManager().isFrozen()) {
            //mid-transfer: the write stays dirty in memory and drains after the cutover
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = flushSingleEntity(binding.getManager(), section.getStorageKey(), what(binding), true, SECTION_HOOKS);
        return future != null ? future : CompletableFuture.completedFuture(null);
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Dirty collection
    // -----------------------------------------------------------------------------------------------------------------------------//

    private List<PlayerData> collectDirtyBase(Iterable<PlayerData> players) {
        List<PlayerData> dirtyPlayers = new ArrayList<>();
        for (PlayerData playerData : players) {
            if (!playerData.isDirty()) continue;
            if (flusher.refuseAheadWrite(playerData, "PlayerData", playerData.getUniqueId())) continue;
            ReentrantLock lock = playerData.getLock();
            lock.lock();
            try {
                if (!playerData.isDirty()) continue;
                //the freeze is read HERE, under the lock and before markClean(): once the flag is
                //cleared the write is unrecoverable, so a later check would already be too late
                if (!controller.baseManager().isFrozen()) {
                    playerData.materializeTimestampsForSave();
                    playerData.markClean();
                    dirtyPlayers.add(playerData);
                }
                //while the base is frozen (transferPlayerData) the player stays dirty and
                //is re-collected on a later tick; its sections still flush through their managers
            } finally {
                lock.unlock();
            }
        }
        return dirtyPlayers;
    }

    private CompletableFuture<Void> flushBaseEntities(List<PlayerData> dirtyPlayers, boolean forced) {
        if (dirtyPlayers.isEmpty()) return null;
        return flusher.persistBatch(controller.baseManager(), dirtyPlayers, mode(forced), "PlayerData",
                BASE_HOOKS, null);
    }

    /**
     * Collects and persists every dirty cell of one section manager as ONE batch. Drives both the
     * per-player and the account-wide section paths - the key is read through {@code hooks} so the
     * per-player uuid and the accountId both flow through the same code.
     */
    private <E extends StoredSection> CompletableFuture<Void> flushDirtyEntities(
            CachingManager<UUID, E> manager, String what, boolean forced, ConflictHooks<UUID, ? super E> hooks) {
        List<E> dirty = new ArrayList<>();
        for (E section : manager.cachedValues()) {
            if (!section.isDirty()) continue;
            if (flusher.refuseAheadWrite(section, what, hooks.storageKey(section))) continue;
            ReentrantLock lock = hooks.lock(section);
            lock.lock();
            try {
                if (!section.isDirty()) continue;
                //the freeze is read HERE, under the lock and before markClean(): once the flag is
                //cleared the write is unrecoverable, so a later check would already be too late
                if (manager.isFrozen()) continue; //mid-transfer: the cell stays dirty and re-collects on a later tick
                section.markClean(); //clear before persisting; a concurrent change re-sets it (at-least-once)
                dirty.add(section);
            } finally {
                lock.unlock();
            }
        }
        if (dirty.isEmpty()) return null;
        return flusher.persistBatch(manager, dirty, mode(forced), what, hooks, StoredSection::markStoredInBackend);
    }

    /**
     * Persists every dirty cell of ONE section as a single forced batch - the flush a re-registration
     * and a manual release run before dropping that section's cache, without touching the rest of the
     * world. Completes immediately when nothing is dirty.
     */
    CompletableFuture<Void> flushSectionManager(PDSectionBinding<? extends PDSection> binding) {
        CompletableFuture<Void> flush = flushDirtyEntities(binding.getManager(), what(binding), true, SECTION_HOOKS);
        return flush == null ? CompletableFuture.completedFuture(null) : flush;
    }

    /** The account-family counterpart of {@link #flushSectionManager(PDSectionBinding)}. */
    CompletableFuture<Void> flushAccountSectionManager(AccountSectionBinding<?> binding) {
        CompletableFuture<Void> flush = flushDirtyEntities(binding.getManager(), what(binding), true, ACCOUNT_HOOKS);
        return flush == null ? CompletableFuture.completedFuture(null) : flush;
    }

    /** Persists ONE dirty section row of one manager by key (the forced single-entity path). */
    private <E extends StoredSection> CompletableFuture<Void> flushSingleEntity(
            CachingManager<UUID, E> manager, UUID key, String what, boolean forced, ConflictHooks<UUID, ? super E> hooks) {
        E section = manager.peek(key).orElse(null);
        if (section == null || !section.isDirty()) return null;
        if (flusher.refuseAheadWrite(section, what, key)) return null;
        ReentrantLock lock = hooks.lock(section);
        lock.lock();
        try {
            if (!section.isDirty()) return null;
            //the freeze is read HERE, under the lock and before markClean(): a mid-transfer cell
            //stays dirty in memory and drains after the cutover, never persisting to the old backend
            if (manager.isFrozen()) return null;
            section.markClean(); //clear before persisting; a concurrent change re-sets it (at-least-once)
        } finally {
            lock.unlock();
        }
        return flusher.persistBatch(manager, Collections.singletonList(section), mode(forced), what, hooks,
                StoredSection::markStoredInBackend);
    }

    private static String what(PDSectionBinding<?> binding) {
        return "PDSection {" + binding.getPdSectionClass().getSimpleName() + "}";
    }

    private static String what(AccountSectionBinding<?> binding) {
        return "AccountSection {" + binding.getSectionClass().getSimpleName() + "}";
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // The per-type conflict hooks (the only playerdata-specific part of the flush)
    // -----------------------------------------------------------------------------------------------------------------------------//

    private static final ConflictHooks<UUID, PlayerData> BASE_HOOKS = new ConflictHooks<UUID, PlayerData>() {
        @Override public UUID storageKey(PlayerData live) { return live.getUniqueId(); }
        @Override public ReentrantLock lock(PlayerData live) { return live.getLock(); }
        @Override public void adoptStoredState(PlayerData live, PlayerData stored) { live.adoptStoredState(stored); }
        @Override public void adoptStoredLockVersion(PlayerData live, PlayerData stored) { live.adoptStoredLockVersion(stored); }
        @Override public void resetLockForRecreate(PlayerData live) { live.resetLockForRecreate(); }
        @Override public void afterAdopt(PlayerData live) { live.warnIfStaleSchema(); }
    };

    private static final ConflictHooks<UUID, PDSection> SECTION_HOOKS = new ConflictHooks<UUID, PDSection>() {
        @Override public UUID storageKey(PDSection live) { return live.getStorageKey(); }
        @Override public ReentrantLock lock(PDSection live) { return live.getLock(); }
        @Override public void adoptStoredState(PDSection live, PDSection stored) { live.adoptStoredState(stored); }
        @Override public void adoptStoredLockVersion(PDSection live, PDSection stored) { live.adoptStoredLockVersion(stored); }
        @Override public void resetLockForRecreate(PDSection live) { live.resetLockForRecreate(); }
        @Override public void afterAdopt(PDSection live) { live.warnIfStaleSchema(); }
    };

    /**
     * Account rows converge by {@code merge()} instead of ADOPT_WINNER: adopting (or keeping) a
     * whole row would silently drop what the OTHER instance of the network wrote, while merging
     * keeps both sides by the section's own policy.
     */
    private static final ConflictHooks<UUID, AccountSection<?>> ACCOUNT_HOOKS = new ConflictHooks<UUID, AccountSection<?>>() {
        @Override public UUID storageKey(AccountSection<?> live) { return live.getAccountId(); }
        @Override public ReentrantLock lock(AccountSection<?> live) { return live.getLock(); }
        @Override public void adoptStoredState(AccountSection<?> live, AccountSection<?> stored) {
            stored.warnIfStaleSchema(); //the winner is a detached decode and may carry an older payload
            live.mergeStoredState(stored);
        }
        @Override public void adoptStoredLockVersion(AccountSection<?> live, AccountSection<?> stored) {
            live.adoptStoredLockVersion(stored);
        }
        @Override public void resetLockForRecreate(AccountSection<?> live) { live.resetLockForRecreate(); }
        @Override public void afterAdopt(AccountSection<?> live) { live.warnIfStaleSchema(); }
        @Override public boolean mergesOnConflict() { return true; }
    };

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Cache-shape guard
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * Safety net for the HARD "PDSections are small" rule: a section that NEVER releases its cells
     * ({@code RESIDENT}/{@code PRELOADED}) and crossed
     * {@link SectionLifecycle#NEVER_RELEASED_WARN_THRESHOLD} logs a DEBUG warning (it never cuts) -
     * either the section carries data it shouldn't (externalize it to its own collection and keep only
     * the id here) or it should release when idle. Throttled to once per manager until it drops back
     * below the threshold.
     */
    private void warnIfResidentTooLarge(PDSectionBinding<? extends PDSection> binding) {
        if (binding.getLifecycle().releasesWhenIdle()) return;
        String sectionName = binding.getPdSectionClass().getSimpleName();
        int size = binding.getManager().cachedSize();
        if (size >= SectionLifecycle.NEVER_RELEASED_WARN_THRESHOLD) {
            if (residentWarned.add(sectionName)) {
                PDLog.debug("PDSection {%s} is '%s' (never released) and holds %s cached cells (>= %s)."
                        + " PDSections are meant to be small: externalize large data to its own collection"
                        + " and keep only the id here, declare a lifecycle that releases when idle"
                        + " (LAZY/ONLINE), or bound it with .maxCached(...).",
                        sectionName, binding.getLifecycle(), size, SectionLifecycle.NEVER_RELEASED_WARN_THRESHOLD);
            }
        } else {
            residentWarned.remove(sectionName); //re-arm once it drops back under the threshold
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Failure aggregation helpers
    // -----------------------------------------------------------------------------------------------------------------------------//

    /**
     * Like {@code CompletableFuture.allOf}, but when SEVERAL components fail the caller still sees
     * every failure (first as primary, siblings suppressed) - a bare allOf surfaces one arbitrary
     * exception and silently drops the rest, hiding part of what a forced save lost.
     */
    private static CompletableFuture<Void> allSurfacingEveryFailure(List<CompletableFuture<Void>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).handle((ok, error) -> {
            if (error == null) return null;
            Throwable primary = null;
            for (CompletableFuture<Void> future : futures) {
                if (!future.isCompletedExceptionally()) continue;
                Throwable component = unwrapCompletion(future);
                if (component == null) continue;
                if (primary == null) primary = component;
                else if (component != primary) primary.addSuppressed(component);
            }
            if (primary == null) primary = error;
            if (primary instanceof RuntimeException) throw (RuntimeException) primary;
            throw new CompletionException(primary);
        });
    }

    /** The component failure of an already-completed future, unwrapped from its CompletionException. */
    private static Throwable unwrapCompletion(CompletableFuture<?> future) {
        try {
            future.join();
            return null;
        } catch (CompletionException e) {
            return e.getCause() != null ? e.getCause() : e;
        } catch (RuntimeException e) {
            return e;
        }
    }
}
