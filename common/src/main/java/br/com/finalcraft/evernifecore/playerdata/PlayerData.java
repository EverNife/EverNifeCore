package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.everydatabase.manager.writeback.PersistedState;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchema;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.everydatabase.query.Indexed;
import br.com.finalcraft.everydatabase.versioned.OptimisticLock;
import br.com.finalcraft.everydatabase.util.JsonAutoDetectFieldsOnly;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The persisted player entity: PlayerData is a storage entity
 * and it remains the live in-memory aggregator of the
 * player's sections.
 */
@JsonAutoDetectFieldsOnly
public class PlayerData implements IPlayerData, EntitySchema {

    /**
     * Registers the base entity's schema-upcast chain (idempotent). The controller calls this on
     * every bootstrap, BEFORE any row is decoded, so the chain survives a registry wipe between
     * bootstraps (tests clear the static migration registry on teardown).
     */
    static void registerBaseSchemas(){
        if (EntitySchemaMigrations.currentVersion(PlayerData.class) > EntitySchema.INITIAL_SCHEMA_VERSION){
            return; //already registered in this runtime
        }
        //v1 -> v2: rows written before the account field existed default to accountId == uuid
        EntitySchemaMigrations.register(PlayerData.class, EntitySchema.INITIAL_SCHEMA_VERSION, node -> {
            if (!node.hasNonNull("accountId")){
                node.set("accountId", node.get("uuid"));
            }
        });
    }

    // ---- persisted state (Jackson) ----
    protected UUID uuid;

    /**
     * The canonical account this player belongs to. Equals {@link #uuid} until the player's
     * identities are linked into a shared account; validated (and re-stamped when the stored truth
     * diverged) on login, and stable for the whole session.
     *
     * <p>Indexed, so "which players belong to this account" is a query rather than a full scan -
     * the members of an account are otherwise unlistable, since an {@link AccountSection} holds one
     * row for the account and knows nothing about the identities sharing it.
     */
    @Indexed
    protected UUID accountId;

    @Indexed
    protected String name;

    protected long firstSeen;

    @Indexed
    protected long lastSeen;

    protected long lastSaved;

    /** On-disk schema version; drives the lazy upcast on read (see EntitySchemaMigrations). */
    protected int schemaVersion = EntitySchemaMigrations.currentVersion(PlayerData.class);

    @OptimisticLock
    protected Long lockVersion;

    // ---- runtime state (never persisted) ----
    @JsonIgnore protected transient FPlayer player = null;
    //volatile: markDirty comes from arbitrary plugin threads while the flush machinery reads and
    //clears the flag on its own threads - without it a dirty mark could stay invisible to a flush
    @JsonIgnore protected transient volatile boolean dirty = false;
    @JsonIgnore protected transient ReentrantLock lock = new ReentrantLock();

    public PlayerData() {
        //Jackson no-arg constructor - the framework always calls warnIfStaleSchema() right after decode
    }

    public PlayerData(UUID uuid, String name) {
        this.uuid = Objects.requireNonNull(uuid, "PlayerUUID cannot be null!");
        this.accountId = uuid; //the account "is born" with the player; linking replaces it later
        this.name = Objects.requireNonNull(name, "PlayerName cannot be null!");
        this.firstSeen = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
        this.lastSaved = 0L;
    }

    public PlayerData(UUID uuid, String name, long firstSeen, long lastSeen, long lastSaved) {
        this(uuid, name);
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.lastSaved = lastSaved;
    }

    /**
     * Post-decode diagnostic guard. The schema upcast now happens on the raw payload inside the codec
     * (see EntitySchemaMigratingCodec), so a decoded instance is already at the current version. Reaching here
     * still behind current means the chain grew AFTER this row was decoded (a migration registered too
     * late): warn once and deliberately do NOT stamp/dirty - that would bless un-migrated data; leaving
     * the old version lets the row migrate correctly on its next decode.
     */
    public void warnIfStaleSchema(){
        if (EntitySchemaMigrations.isBehind(this) && EntitySchemaMigrations.firstStaleWarning(PlayerData.class)){
            PDLog.severe("PlayerData decoded at schema v%s but current is v%s - migrations must be"
                    + " registered before the controller bootstrap; row NOT migrated.",
                    schemaVersion, EntitySchemaMigrations.currentVersion(PlayerData.class));
        }
    }

    @Override
    public int getSchemaVersion() {
        return schemaVersion;
    }

    @Override
    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    @Override
    public <T extends PDSection> CompletableFuture<T> getPDSection(Class<T> pdSectionClass){
        PlayerController controller = PlayerController.get();
        if (controller == null){
            return PlayerController.failedFuture(new IllegalStateException(
                    "PlayerController is not bootstrapped yet!"));
        }
        return controller.resolveSection(this, pdSectionClass);
    }

    @Override
    public <T extends PDSection> CompletableFuture<Optional<T>> getPDSectionIfPresent(Class<T> pdSectionClass){
        PlayerController controller = PlayerController.get();
        if (controller == null){
            return PlayerController.failedFuture(new IllegalStateException(
                    "PlayerController is not bootstrapped yet!"));
        }
        return controller.resolveSectionIfPresent(this, pdSectionClass);
    }

    @Override
    public CompletableFuture<Boolean> hasPDSection(Class<? extends PDSection> pdSectionClass){
        PlayerController controller = PlayerController.get();
        if (controller == null){
            return PlayerController.failedFuture(new IllegalStateException(
                    "PlayerController is not bootstrapped yet!"));
        }
        return controller.hasSection(this.uuid, pdSectionClass);
    }

    @Override
    public boolean hasPDSectionIfLoaded(Class<? extends PDSection> pdSectionClass){
        return PlayerController.getLoadedSection(this.uuid, pdSectionClass) != null;
    }

    @Override
    public <T extends PDSection> T getPDSectionIfLoaded(Class<T> pdSectionClass){
        return PlayerController.getLoadedSection(this.uuid, pdSectionClass);
    }

    /** Marks a section's cache entry as stale (the next read reloads from the backend). */
    public void invalidateSection(Class<? extends PDSection> pdSectionClass){
        PlayerController.invalidatePDSection(pdSectionClass, this.uuid);
    }

    // ---- account-wide sections (one row per account, shared by every linked identity) ----------

    /**
     * The account-wide section of this player's account (see {@link #getAccountId()}), seeding a
     * transient default on a true miss. Account data is shared by every identity linked into the
     * account and its consistency across the network is EVENTUAL (see {@link AccountSection}).
     */
    public <T extends AccountSection<T>> CompletableFuture<T> getAccountSection(Class<T> sectionClass){
        PlayerController controller = PlayerController.get();
        if (controller == null){
            return PlayerController.failedFuture(new IllegalStateException(
                    "PlayerController is not bootstrapped yet!"));
        }
        return controller.accountEngine().resolve(sectionClass, getAccountId());
    }

    /** Presence-only variant of {@link #getAccountSection(Class)}: no default is seeded. */
    public <T extends AccountSection<T>> CompletableFuture<Optional<T>> getAccountSectionIfPresent(Class<T> sectionClass){
        PlayerController controller = PlayerController.get();
        if (controller == null){
            return PlayerController.failedFuture(new IllegalStateException(
                    "PlayerController is not bootstrapped yet!"));
        }
        return controller.accountEngine().resolveIfPresent(sectionClass, getAccountId());
    }

    /** The cached account-wide section, or null when it is not in memory (never touches storage). */
    public <T extends AccountSection<T>> T getAccountSectionIfLoaded(Class<T> sectionClass){
        PlayerController controller = PlayerController.get();
        if (controller == null) return null;
        return controller.accountEngine().getLoaded(sectionClass, getAccountId());
    }

    // ---- IDirtyable (the CachingManager's dirty contract; the only mark-dirty API) ----

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

    /** Immediately flushes this player's dirty state (base entity + dirty sections). */
    public CompletableFuture<Void> forceSavePlayerData(){
        markDirty();
        PlayerController controller = PlayerController.get();
        if (controller == null){
            return PlayerController.failedFuture(new IllegalStateException(
                    "PlayerController is not bootstrapped yet!"));
        }
        return controller.flushPlayer(this);
    }

    /**
     * ADOPT_WINNER conflict handling: re-adopts the stored winning state into the live
     * object (plugins keep their references; local changes are discarded). Copies every
     * persisted field reflectively (shared with {@link PDSection}); the schema upcast of the
     * adopted payload is run separately by the conflict pipeline ({@link #warnIfStaleSchema()}).
     */
    void adoptStoredState(PlayerData stored){
        PersistedState.copyInto(this, stored);
    }

    /** Adopts ONLY the winner's lock version - used when re-dirtied local values must be kept. */
    void adoptStoredLockVersion(PlayerData stored){
        this.lockVersion = stored.lockVersion;
    }

    /** Resets the optimistic lock so the next flush re-creates a row that vanished mid-conflict. */
    void resetLockForRecreate(){
        this.lockVersion = null;
    }

    /** Called by the flush pipeline (under this player's lock) right before the encode. */
    protected void materializeTimestampsForSave(){
        if (this.player != null){
            this.lastSeen = System.currentTimeMillis();
        }
        this.lastSaved = System.currentTimeMillis();
    }

    /**
     * Records the end of a session so a durable {@code lastSeen} survives a clean login/logout with no
     * other mutation. The quit path calls this (then flushes once): it stamps {@code lastSeen} now and
     * marks the base dirty so the value is persisted - the ONE full-entity write per session, instead of
     * one every flush tick while online.
     */
    protected void materializeSessionEnd(){
        this.lastSeen = System.currentTimeMillis();
        this.markDirty();
    }

    protected ReentrantLock getLock() {
        return lock;
    }

    /**
     * Attaches/detaches the live platform player. Deliberately does NOT mark the base dirty and does
     * NOT stamp {@code lastSeen}: presence is a volatile heartbeat, not durable state, so a bare
     * login/logout must not force a full-entity write every session. {@link #getLastSeen()} reports
     * {@code now} while online; the field is materialized only when the entity is flushed for a real
     * change ({@link #materializeTimestampsForSave()}), and a quit forces one durable flush.
     */
    public void setPlayer(FPlayer player){
        this.player = player;
    }

    /** Framework wiring: rename flow from handleLogin (the backend key stays the UUID). */
    protected void setName(String name){
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getFirstSeen() {
        return firstSeen;
    }

    @Override
    public long getLastSeen(){
        return player != null ? System.currentTimeMillis() : lastSeen;
    }

    @Override
    public long getLastSaved() {
        return lastSaved;
    }

    @Override
    public FPlayer getPlayer(){
        return player;
    }

    @Override
    public boolean isPlayerOnline(){
        return player != null && player.isOnline();
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    /**
     * The canonical account this player belongs to - the key every account-wide row of this player
     * is stored under. Equals {@link #getUniqueId()} while the player's identities were never
     * linked. Stable for the whole session: it is only re-validated on login.
     */
    public UUID getAccountId() {
        //never null even for an entity that skipped the schema upcast (defensive: the field is
        //the storage key of account-wide data, and a null would leak into keying)
        return accountId != null ? accountId : uuid;
    }

    /** Framework wiring: adopts the account resolved on login; a real change is re-persisted. */
    void stampAccountId(UUID resolvedAccountId){
        Objects.requireNonNull(resolvedAccountId, "accountId cannot be null");
        if (!resolvedAccountId.equals(this.accountId)){
            this.accountId = resolvedAccountId;
            markDirty();
        }
    }

    @Override
    public PlayerData getPlayerData() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true; //Only equal when it is the same object, otherwise different
        return false;
    }

    @Override
    public int hashCode() {
        return uuid.hashCode(); //Uses the UUID as the hashcode
    }
}
