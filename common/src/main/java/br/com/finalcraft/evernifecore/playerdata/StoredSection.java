package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.everydatabase.changefeed.ChangeEvent;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchema;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.CacheEntry;
import br.com.finalcraft.everydatabase.manager.cache.IDirtyable;
import br.com.finalcraft.everydatabase.manager.writeback.PersistedState;
import br.com.finalcraft.everydatabase.util.JsonAutoDetectFieldsOnly;
import br.com.finalcraft.everydatabase.versioned.OptimisticLock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared persisted plumbing of {@link PDSection} (per-player) and {@link AccountSection} (per-account):
 * the on-disk schema version, the optimistic lock, the dirty flag, the transient-default presence
 * bookkeeping and the per-key flush lock. Both section kinds carry exactly this skeleton around their
 * own key field ({@code uuid} / {@code accountId}) and payload; keeping it in one base is what lets the
 * flush/conflict pipeline drive them through the same {@code ConflictHooks}, and keeps the two from
 * drifting apart (the same reason {@link PersistedState} reflects over this whole hierarchy).
 *
 * <p>Package-private and never extended by plugins - they extend {@link PDSection} or
 * {@link AccountSection}. Concrete subclasses inherit this base's fields; the {@code @Indexed}/
 * {@code @OptimisticLock} scanners of {@code EntityDescriptor.build()} walk the class hierarchy, so
 * the {@code lockVersion} declared here is wired for every section type.
 */
@JsonAutoDetectFieldsOnly
abstract class StoredSection implements EntitySchema, IDirtyable {

    /** On-disk schema version; drives the lazy upcast on read (see EntitySchemaMigrations). */
    protected int schemaVersion = EntitySchema.INITIAL_SCHEMA_VERSION;

    @OptimisticLock
    protected Long lockVersion;

    //volatile: markDirty comes from arbitrary plugin threads while the flush machinery reads and
    //clears the flag on its own threads - without it a dirty mark could stay invisible to a flush
    @JsonIgnore
    protected transient volatile boolean dirty = false;

    /** True while this is a framework-created default never persisted nor loaded (cache-only). */
    @JsonIgnore
    private transient volatile boolean transientDefault = false;

    /** Per-key lock guarding the flush/conflict-resolution critical section. */
    @JsonIgnore
    protected transient ReentrantLock lock = new ReentrantLock();

    /**
     * The cache cell this instance was handed out from, memoized at resolution time. Reading it is
     * lock-free (volatile), which is what makes the detached check on every {@code markDirty()} free
     * enough to live on the write path. Null for an instance that never went through the resolution
     * path, and then there is simply no answer to give.
     */
    @JsonIgnore
    private transient volatile CacheEntry<?> cacheCell;

    /** The key {@link #cacheCell} is filed under - carried only so a report can name it. */
    @JsonIgnore
    private transient volatile UUID cacheKey;

    /** A short label of the section kind for diagnostics ({@code "PDSection"} / {@code "AccountSection"}). */
    abstract String sectionKind();

    // ---- schema ------------------------------------------------------------------------------

    @Override
    public int getSchemaVersion() {
        return schemaVersion;
    }

    @Override
    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    /**
     * Post-decode diagnostic guard (called right after a stored instance is resolved, before it is handed
     * out). The schema upcast now happens on the raw payload inside the codec (see EntitySchemaMigratingCodec),
     * so a resolved instance is already current. Reaching here still behind current means the chain grew
     * AFTER this row was decoded: warn once and deliberately do NOT stamp/dirty (stamping without running
     * the steps would bless un-migrated data; leaving the old version lets it migrate on its next decode).
     */
    final void warnIfStaleSchema() {
        if (EntitySchemaMigrations.isBehind(this) && EntitySchemaMigrations.firstStaleWarning(getClass())) {
            EverNifeCore.getLog().severe("{} {} decoded at schema v{} but current is v{} - register migrations"
                    + " before the section is bound; row NOT migrated.",
                    sectionKind(), getClass().getName(), schemaVersion, EntitySchemaMigrations.currentVersion(getClass()));
        }
    }

    // ---- IDirtyable (the CachingManager's dirty contract; the only mark-dirty API) -------------

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void markClean() {
        this.dirty = false;
    }

    @Override
    public void markDirty() {
        this.dirty = true;
        reportIfDetached();
    }

    /**
     * Records which cache cell handed this instance out, so a later write can tell whether it is
     * still the live one. Called by the resolution path, right before the instance reaches the caller.
     */
    final void bindToCache(CachingManager<UUID, ?> manager, UUID key) {
        this.cacheCell = manager.peekCell(key, manager.defaultPolicy());
        this.cacheKey = key;
    }

    /**
     * The flush pipeline persists the CACHED values, so a write on an instance the cache no longer
     * holds is invisible to it. Nothing is thrown - blowing up inside a plugin's tick would trade a
     * lost write for a broken plugin - and the dirty flag is still set, so a conflict adoption can
     * still pick this state up. The point is only that the loss stops being silent.
     */
    private void reportIfDetached() {
        CacheEntry<?> cell = this.cacheCell;
        if (cell == null) return;
        if (!cell.isEvicted() && cell.getValue() == this) return;
        DetachedWrites.report(this, this.cacheKey);
    }

    // ---- presence bookkeeping (framework wiring) -----------------------------------------------
    // A framework-created default lives only in the cache until the plugin dirties it; the presence
    // API must not report it as a stored row. The flag flips off on the first successful save, on a
    // conflict adoption (the winner exists in the backend) and, implicitly, never turns on for a
    // decoded instance.

    /** True while this is a never-persisted framework default (cache-only). */
    public final boolean isTransientDefault() {
        return transientDefault && !dirty;
    }

    final void markTransientDefault() {
        this.transientDefault = true;
    }

    final void markStoredInBackend() {
        this.transientDefault = false;
    }

    /** The flush's per-row success callback: the row exists in the backend now, and the plugin is told. */
    final void markPersisted() {
        markStoredInBackend();
        try {
            onFlushed();
        } catch (Throwable hookFailure) {
            //one plugin's hook must not break the batch for the other rows
            EverNifeCore.getLog().severe("{} {}.onFlushed() threw - ignored, the row is persisted: {}",
                    sectionKind(), getClass().getName(), hookFailure);
        }
    }

    // ---- network hooks and reads (what a plugin running on several servers builds on) ----------

    /**
     * This row was just WRITTEN to its backend, successfully. Runs on the flush completion thread (storage
     * async, never the main thread), holding no lock, after the dirty flag was cleared - the instance may
     * already be dirty again (at-least-once). Not called for a row that lost an optimistic race: the conflict
     * path adopts or merges the winner instead, and the later write that lands calls it. Keep it cheap; a
     * throw is logged and swallowed.
     */
    protected void onFlushed() {
    }

    /**
     * Another server wrote ({@code SAVE}) or deleted ({@code DELETE}) this row, as reported by the
     * multi-server cache-sync. Runs on the sync's delivery thread while THIS instance is still the one in the
     * cache, right before the cell is marked stale (SAVE) or evicted (DELETE). After it, a clean cell reloads
     * on its next read and hands out a NEW instance; a dirty cell is kept and its flush resolves the conflict.
     * {@code event.version()} is the version after the remote write, or {@code ChangeEvent.UNKNOWN_VERSION}.
     *
     * <p>Never fires for this server's own writes, nor without cache-sync (file or H2 backends, a SQL backend
     * without a {@code redis:} block): there the flush conflict is the only signal. Keep it cheap and
     * non-blocking - it holds up every later event; a throw is logged and swallowed.
     */
    protected void onRemoteChange(ChangeEvent event) {
    }

    /** The optimistic-lock version this instance last adopted; null when never persisted. Any thread. */
    public final @Nullable Long getLockVersion() {
        return lockVersion;
    }

    /**
     * The version the BACKEND holds for this row now - one key+version read, never the body. Completes with
     * null when the row does not exist. Async: never {@code join()} it on the main thread.
     */
    public CompletableFuture<Long> readStoredVersion() {
        PlayerController controller = PlayerController.get();
        if (controller == null) {
            return PlayerController.failedFuture(new IllegalStateException("PlayerController is not bootstrapped yet!"));
        }
        return controller.readStoredVersion(this);
    }

    /**
     * Immediately flushes ONLY this row to its backend; nothing else is touched. A lost optimistic race fails
     * the future with {@code OptimisticConflictException} after the winner was adopted (or merged).
     */
    public CompletableFuture<Void> forceSave() {
        markDirty();
        PlayerController controller = PlayerController.get();
        if (controller == null) {
            return PlayerController.failedFuture(new IllegalStateException("PlayerController is not bootstrapped yet!"));
        }
        return controller.flushAlone(this);
    }

    // ---- flush/conflict wiring (called by the flush pipeline, always under the lock) -----------

    final ReentrantLock getLock() {
        return lock;
    }

    /** Resets the optimistic lock so the next flush re-creates a row that vanished mid-conflict. */
    final void resetLockForRecreate() {
        this.lockVersion = null;
    }

    /** Adopts ONLY the winner's lock version - used when re-dirtied local values must be kept. */
    final void adoptStoredLockVersion(StoredSection stored) {
        this.lockVersion = stored.lockVersion;
    }

    // ---- shared framework helpers (one implementation for both section kinds) ------------------

    /**
     * Instantiates a fresh framework default of {@code sectionClass}: no-arg constructor, stamped at
     * the current code schema version and marked transient (cache-only until the plugin dirties it).
     * The caller attaches the key afterwards ({@code attachPlayerData} / {@code attachAccountId}).
     */
    static <S extends StoredSection> S newDefault(Class<S> sectionClass) {
        try {
            Constructor<S> constructor = sectionClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            S section = constructor.newInstance();
            //a brand-new default is already at the current code version - nothing to migrate later
            section.setSchemaVersion(EntitySchemaMigrations.currentVersion(sectionClass));
            //cache-only until the plugin dirties it: the presence API must not report it as stored
            section.markTransientDefault();
            return section;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Section [" + sectionClass.getName()
                    + "] must declare a no-arg constructor!", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate section ["
                    + sectionClass.getName() + "]!", e);
        }
    }

    /**
     * Runs the lazy schema upcast guard without ever leaving a half-migrated instance behind: when it
     * throws, the cell is evicted (peek/getLoaded stop serving it, the next access reloads and retries)
     * and the failure is rethrown so the caller's future surfaces it.
     */
    static <S extends StoredSection> void upcastOrEvict(CachingManager<UUID, S> manager, UUID key, S section) {
        try {
            section.warnIfStaleSchema();
        } catch (Throwable migrationFailure) {
            manager.evict(key);
            EverNifeCore.getLog().severe("Schema migration of {} [{}] failed - the half-migrated entity was evicted:",
                    section.getClass().getSimpleName(), key);
            migrationFailure.printStackTrace();
            if (migrationFailure instanceof RuntimeException) throw (RuntimeException) migrationFailure;
            if (migrationFailure instanceof Error) throw (Error) migrationFailure;
            throw new IllegalStateException(migrationFailure);
        }
    }
}
