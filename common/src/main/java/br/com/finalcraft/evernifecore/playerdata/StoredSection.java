package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchema;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import br.com.finalcraft.everydatabase.manager.cache.IDirtyable;
import br.com.finalcraft.everydatabase.manager.writeback.PersistedState;
import br.com.finalcraft.everydatabase.util.JsonAutoDetectFieldsOnly;
import br.com.finalcraft.everydatabase.versioned.OptimisticLock;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.lang.reflect.Constructor;
import java.util.UUID;
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
            PDLog.severe("%s %s decoded at schema v%s but current is v%s - register migrations"
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
            PDLog.severe("Schema migration of %s [%s] failed - the half-migrated entity was evicted:",
                    section.getClass().getSimpleName(), key);
            migrationFailure.printStackTrace();
            if (migrationFailure instanceof RuntimeException) throw (RuntimeException) migrationFailure;
            if (migrationFailure instanceof Error) throw (Error) migrationFailure;
            throw new IllegalStateException(migrationFailure);
        }
    }
}
