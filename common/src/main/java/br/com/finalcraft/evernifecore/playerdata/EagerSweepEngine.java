package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.playerdata.storage.PDSectionBinding;
import br.com.finalcraft.evernifecore.playerdata.storage.PlayerDataBinding;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchema;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaSweepMarker;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaSweeper;
import br.com.finalcraft.everydatabase.manager.entityschema.SweepOptions;
import br.com.finalcraft.everydatabase.manager.log.ManagerLog;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * The playerdata-side glue for the entity-schema eager sweep: owns the executor, the kill-switch and
 * the per-collection mutual exclusion, and delegates the actual scan/rewrite to
 * {@link EntitySchemaSweeper}. Nothing in here understands migration itself; it only decides WHEN a
 * sweep may run and provides the abort hook the sweeper polls.
 *
 * <p>Runs asynchronously on a dedicated single thread, after the {@code ready} gate, one
 * collection at a time. The marker (on the data's own backend) records completion and doubles as a
 * cross-instance CAS lease on enforcing backends.
 */
final class EagerSweepEngine {

    private static final String KILL_SWITCH_PATH = "schema.eager-sweep-enabled";
    private static final String BATCH_SIZE_PATH  = "schema.sweep-batch-size";
    private static final int    DEFAULT_BATCH    = 256;

    private final PlayerController controller;
    private final String runnerId = UUID.randomUUID().toString();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ECore-SchemaSweep");
        t.setDaemon(true);
        return t;
    });
    private final Set<String> sweeping = ConcurrentHashMap.newKeySet();
    private final Set<Class<?>> warnedDisabled = ConcurrentHashMap.newKeySet();
    private volatile boolean shuttingDown = false;

    /** Routes the sweeper's log seam to {@link PDLog} (INFO -> info, WARNING -> warning). */
    private static final ManagerLog LOGGER = (level, message) -> {
        if (level == Level.WARNING || level == Level.SEVERE) {
            PDLog.warning("%s", message);
        } else {
            PDLog.info("%s", message);
        }
    };

    EagerSweepEngine(PlayerController controller) {
        this.controller = controller;
    }

    // ------------------------------------------------------------------
    //  Scheduling (async, post-ready)
    // ------------------------------------------------------------------

    <S extends PDSection> void maybeSweep(PDSectionBinding<S> binding) {
        if (!enabled(binding.getPdSectionClass())) return;
        schedule(binding.getManager());
    }

    <S extends AccountSection<S>> void maybeSweepAccount(AccountSectionBinding<S> binding) {
        if (!enabled(binding.getSectionClass())) return;
        schedule(binding.getManager());
    }

    void maybeSweepBase(PlayerDataBinding binding) {
        if (!enabled(PlayerData.class)) return;
        schedule(binding.getManager());
    }

    private <V extends EntitySchema> void schedule(CachingManager<UUID, V> manager) {
        String collection = manager.collection();
        controller.whenReady().thenRunAsync(() -> {
            sweeping.add(collection);
            try {
                EntitySchemaSweeper.sweep(manager, SweepOptions.builder()
                        .runnerId(runnerId)
                        .batchSize(controller.storageYml().getInt(BATCH_SIZE_PATH, DEFAULT_BATCH))
                        //a sweep rewrites rows through this manager, so stop at the next batch
                        //boundary once its writes are frozen (a transfer took it over) or we shut down
                        .abortCheck(() -> manager.isFrozen() || shuttingDown)
                        .logger(LOGGER)
                        .build());
            } catch (Throwable t) {
                PDLog.warning("[SchemaSweep] %s: sweep failed - %s", collection, String.valueOf(t.getMessage()));
            } finally {
                sweeping.remove(collection);
            }
        }, executor);
    }

    /**
     * True when {@code collection} is currently being swept (mutual exclusion with runtime
     * transfers). Tracked here rather than read back from the sweeper: this covers the whole
     * scheduled task - including the stretch before the sweeper claims its lease - and a collection
     * name is all a caller has to ask with.
     */
    boolean isSweeping(String collection) {
        return sweeping.contains(collection);
    }

    void shutdown() {
        shuttingDown = true; // makes any in-flight sweep abort at its next batch boundary
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------
    //  Gate
    // ------------------------------------------------------------------

    private boolean enabled(Class<? extends EntitySchema> type) {
        if (EntitySchemaMigrations.eagerTargetVersion(type) <= EntitySchema.INITIAL_SCHEMA_VERSION) {
            return false; // no eager step pending
        }
        if (!controller.storageYml().getBoolean(KILL_SWITCH_PATH, true)) {
            if (warnedDisabled.add(type)) {
                PDLog.warning("[SchemaSweep] eager steps are pending for %s but '%s' is disabled -"
                        + " relying on lazy migration.", type.getName(), KILL_SWITCH_PATH);
            }
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  Test hook
    // ------------------------------------------------------------------

    /** The marker repository on {@code storage} - for same-package integration tests. */
    Repository<String, EntitySchemaSweepMarker> markerRepository(Storage storage) {
        return storage.repository(EntitySchemaSweeper.MARKER_DESCRIPTOR);
    }
}
