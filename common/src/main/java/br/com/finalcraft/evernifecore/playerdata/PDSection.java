package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.everydatabase.manager.writeback.PersistedState;
import br.com.finalcraft.everydatabase.util.JsonAutoDetectFieldsOnly;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A pluggable per-player data section. Each section is an independent entity persisted on the
 * backend the admin configured in storage.yml (see PDSectionConfiguration / registerPDSectionCfg),
 * keyed by the player's platform uuid. Data that belongs to the ACCOUNT (shared across linked
 * identities) is an {@link AccountSection} instead.
 *
 * <p>The persisted skeleton (schema version, optimistic lock, dirty flag, transient-default
 * bookkeeping, flush lock) lives in {@link StoredSection}; this class adds the per-player key and
 * the {@link IPlayerData} delegation to the attached {@link PlayerData}.
 *
 * <p>Contract for subclasses:</p>
 * <ol>
 *   <li>Declare a no-arg constructor (Jackson decodes through it).</li>
 *   <li>Persistence is automatic on flush (every 30 seconds) when marked dirty via {@code markDirty()}.</li>
 *   <li>Runtime-only fields: mark them as {@code @JsonIgnore}.</li>
 *   <li>Persisted fields are picked up directly by Jackson (field access - getters
 *       are never serialized).</li>
 * </ol>
 */
@JsonAutoDetectFieldsOnly
public abstract class PDSection extends StoredSection implements IPlayerData {

    protected UUID uuid;

    //Runtime state - wired by the framework after decode or default creation
    @JsonIgnore
    private PlayerData playerData;

    protected PDSection() {
        //Jackson no-arg constructor - the framework attaches the PlayerData afterwards
    }

    @Override
    String sectionKind() {
        return "PDSection";
    }

    /**
     * Framework wiring: links this section to its loaded PlayerData and stamps its storage key
     * (the player's platform uuid, used by the {@code keyExtractor}).
     */
    public final void attachPlayerData(PlayerData playerData) {
        this.playerData = playerData;
        this.uuid = playerData.getUniqueId();
    }

    /** Marks the cache entry as stale: the next read reloads from the backend. */
    public void invalidate(){
        if (playerData != null){
            playerData.invalidateSection(this.getClass());
        }
    }

    /**
     * ADOPT_WINNER conflict handling: re-adopts the stored winning state into THIS live instance
     * (plugins keep their references; local changes are discarded), symmetric to the base
     * {@link PlayerData#adoptStoredState(PlayerData)} - both share the same reflective copy of
     * every persisted field ({@link PersistedState}), leaving the runtime wiring
     * ({@link #playerData}, the lock, the dirty flag) untouched.
     */
    void adoptStoredState(PDSection stored) {
        PersistedState.copyInto(this, stored);
        markStoredInBackend(); //the adopted winner exists in the backend
    }

    /**
     * Immediately flushes the WHOLE player (base PlayerData + this section + every other dirty
     * section), same pipeline as the periodic flush. Use {@link #forceSavePDSection()} to persist
     * only this section without touching the PlayerData.
     */
    public CompletableFuture<Void> forceSavePlayerData(){
        markDirty();
        return playerData.forceSavePlayerData();
    }

    /**
     * Immediately flushes ONLY this section to its own backend, in isolation: the parent
     * PlayerData is neither marked dirty nor saved, and no other section is touched.
     */
    public CompletableFuture<Void> forceSavePDSection(){
        markDirty();
        PlayerController controller = PlayerController.get();
        if (controller == null){
            return PlayerController.failedFuture(new IllegalStateException(
                    "PlayerController is not bootstrapped yet!"));
        }
        return controller.flushSection(this);
    }

    @Override
    public PlayerData getPlayerData() {
        return playerData;
    }

    @Override
    public String getName() {
        return playerData.getName();
    }

    /**
     * The PLAYER's platform uuid (the {@link IPlayerData} contract), delegating to the attached
     * PlayerData; falls back to the storage key on a detached instance (they are the same value).
     */
    @Override
    public UUID getUniqueId() {
        return playerData != null ? playerData.getUniqueId() : uuid;
    }

    /**
     * The key this section's row is persisted under - the player's platform uuid. What the storage
     * {@code keyExtractor} and the flush/conflict pipeline key by.
     */
    public final UUID getStorageKey() {
        return uuid;
    }

    @Override
    public boolean isPlayerOnline(){
        return playerData != null && playerData.isPlayerOnline();
    }

    @Override
    public FPlayer getPlayer(){
        return playerData.getPlayer();
    }

    @Override
    public long getFirstSeen(){
        return playerData.getFirstSeen();
    }

    @Override
    public long getLastSeen(){
        return playerData.getLastSeen();
    }

    @Override
    public long getLastSaved() {
        return playerData.getLastSaved();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }

    @Override
    public <T extends PDSection> CompletableFuture<T> getPDSection(Class<T> pdSectionClass){
        if (this.getClass() == pdSectionClass) return CompletableFuture.completedFuture((T) this);
        return playerData.getPDSection(pdSectionClass);
    }

    @Override
    public <T extends PDSection> CompletableFuture<Optional<T>> getPDSectionIfPresent(Class<T> pdSectionClass){
        if (this.getClass() == pdSectionClass){
            //a transient default is cache-only (no stored row): the presence primitive must
            //report absence here too, matching the controller path
            return CompletableFuture.completedFuture(isTransientDefault()
                    ? Optional.<T>empty()
                    : Optional.of((T) this));
        }
        return playerData.getPDSectionIfPresent(pdSectionClass);
    }

    @Override
    public CompletableFuture<Boolean> hasPDSection(Class<? extends PDSection> pdSectionClass){
        if (this.getClass() == pdSectionClass){
            return CompletableFuture.completedFuture(!isTransientDefault());
        }
        return playerData.hasPDSection(pdSectionClass);
    }

    @Override
    public boolean hasPDSectionIfLoaded(Class<? extends PDSection> pdSectionClass){
        if (this.getClass() == pdSectionClass) return true;
        return playerData.hasPDSectionIfLoaded(pdSectionClass);
    }

    @Override
    public <T extends PDSection> T getPDSectionIfLoaded(Class<T> pdSectionClass){
        if (this.getClass() == pdSectionClass) return (T) this;
        return playerData.getPDSectionIfLoaded(pdSectionClass);
    }
}
