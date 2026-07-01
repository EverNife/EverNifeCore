package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.everydatabase.manager.writeback.PersistedState;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.everydatabase.util.JsonAutoDetectFieldsOnly;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A pluggable ACCOUNT-wide data section: one row per account (see {@link PlayerData#getAccountId()}),
 * shared by every identity linked into that account - the home of network-universal data
 * (global achievements, VIP status, ...). While a player was never linked, its accountId equals its
 * uuid, so an account section behaves exactly like a per-player section.
 *
 * <p>Unlike a {@link PDSection}, an account section belongs to the ACCOUNT, not to one player: it
 * carries no player reference and no player identity (two linked players online at once share the
 * SAME live instance). It can be written from several servers of the network, so consistency is
 * EVENTUAL and convergence is driven by {@link #merge(List)} - which is also how a concurrent-write
 * conflict is resolved.</p>
 *
 * <p>The persisted skeleton (schema version, optimistic lock, dirty flag, transient-default
 * bookkeeping, flush lock) lives in {@link StoredSection}; this class adds the account key, the
 * merge-ledger and the {@link #merge(List)} convergence policy.</p>
 *
 * <p>Contract for subclasses:</p>
 * <ol>
 *   <li>Declare a no-arg constructor (Jackson decodes through it).</li>
 *   <li>Implement {@link #merge(List)}: pure, associative and commutative (see its contract).</li>
 *   <li>Persistence is automatic on flush when marked dirty via {@code markDirty()}.</li>
 *   <li>Runtime-only fields: mark them as {@code @JsonIgnore}.</li>
 *   <li>The class must be platform-agnostic (the same section is loaded by every platform of the
 *       network) and its collection must live on the shared account backend.</li>
 * </ol>
 *
 * @param <T> the concrete section type (self-type, so {@code merge} is typed)
 */
@JsonAutoDetectFieldsOnly
public abstract class AccountSection<T extends AccountSection<T>> extends StoredSection {

    /** The accountId this row belongs to - the storage key. */
    protected UUID accountId;

    /**
     * Keys already absorbed into this row by an account merge, with the lock version each old row
     * had when absorbed - lets an interrupted or repeated data migration detect what was already
     * merged instead of double-applying a non-idempotent {@code merge}.
     */
    protected List<MergedKeyRecord> mergedKeys = new ArrayList<>();

    protected AccountSection() {
        //Jackson no-arg constructor - the framework attaches the accountId afterwards when seeding
    }

    @Override
    String sectionKind() {
        return "AccountSection";
    }

    /**
     * Combines this section with {@code others} belonging to the same account, producing the state
     * the account should hold. Called when linked identities' rows are coalesced AND when a
     * concurrent-write conflict is resolved - it IS this section's convergence policy.
     *
     * <p><b>Pure:</b> return a NEW instance; never mutate {@code this} or any element of
     * {@code others}. <b>Associative and commutative:</b> the order the inputs arrive in is
     * undefined (union sets, max/min timestamps, sum counters - fine; "keep the first one" - not).
     * It does NOT need to be idempotent: the framework tracks what was already merged.</p>
     *
     * @param others other states of the same account (never null; may be empty); {@code this} is
     *               NOT included
     * @return a brand-new instance holding the combined state
     */
    public abstract T merge(List<T> others);

    /** The accountId this row belongs to (the storage key). */
    public final UUID getAccountId() {
        return accountId;
    }

    /** Framework wiring: stamps the storage key onto a freshly seeded default. */
    final void attachAccountId(UUID accountId) {
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
    }

    /** Marks the cache entry as stale: the next read reloads from the backend. */
    public void invalidate() {
        PlayerController.invalidateAccountSection(this.getClass(), accountId);
    }

    /** Immediately flushes ONLY this section to the account backend. */
    public CompletableFuture<Void> forceSave() {
        markDirty();
        PlayerController controller = PlayerController.get();
        if (controller == null) {
            return PlayerController.failedFuture(new IllegalStateException(
                    "PlayerController is not bootstrapped yet!"));
        }
        return controller.flushAccountSection(this);
    }

    // ---- conflict/merge wiring (called by the flush pipeline, always under the lock) -----------

    /**
     * Resolves a concurrent-write conflict by MERGING the stored winner into THIS live instance
     * (plugins keep their references): the combined state replaces the local fields, the ledgers
     * are united, the winner's lock version is adopted (so the next flush lands cleanly) and the
     * instance is re-marked dirty to persist the merged state.
     */
    @SuppressWarnings("unchecked")
    final void mergeStoredState(AccountSection<?> stored) {
        T combined = merge(Collections.singletonList((T) stored));
        //the dev's merge builds a fresh instance: preserve the framework identity/bookkeeping
        UUID key = this.accountId;
        List<MergedKeyRecord> ledger = unionLedgers(this.mergedKeys, stored.mergedKeys);
        PersistedState.copyInto(this, combined);
        this.accountId = key;
        this.mergedKeys = ledger;
        this.schemaVersion = EntitySchemaMigrations.currentVersion(getClass());
        this.lockVersion = stored.lockVersion;
        markStoredInBackend(); //the winner exists in the backend
        markDirty();
    }

    /**
     * Absorbs a row stored under a FORMER key of this account (a member's pre-link singleton row, an
     * absorbed account's row) into THIS live instance: the combined state replaces the local fields,
     * the ledgers are united and the absorption is recorded ({@code {oldKey, its lockVersion}}), so
     * an interrupted or repeated migration never double-applies a non-idempotent merge. Unlike
     * {@link #mergeStoredState}, THIS row's own lock version is kept - the absorbed row is a
     * different key, not a newer version of this one. Always called under the lock.
     */
    @SuppressWarnings("unchecked")
    final void absorbMigratedState(AccountSection<?> oldRow) {
        T combined = merge(Collections.singletonList((T) oldRow));
        UUID key = this.accountId;
        Long ownLockVersion = this.lockVersion;
        List<MergedKeyRecord> ledger = unionLedgers(this.mergedKeys, oldRow.mergedKeys);
        PersistedState.copyInto(this, combined);
        this.accountId = key;
        this.mergedKeys = ledger;
        this.schemaVersion = EntitySchemaMigrations.currentVersion(getClass());
        this.lockVersion = ownLockVersion;
        recordMergedKey(oldRow.accountId, oldRow.lockVersion);
        markStoredInBackend();
        markDirty();
    }

    /** Unites two absorption ledgers, keeping the HIGHEST lock version recorded per key. */
    private static List<MergedKeyRecord> unionLedgers(List<MergedKeyRecord> a, List<MergedKeyRecord> b) {
        List<MergedKeyRecord> union = new ArrayList<>(a == null ? Collections.emptyList() : a);
        if (b != null) {
            for (MergedKeyRecord record : b) {
                MergedKeyRecord existing = null;
                for (MergedKeyRecord candidate : union) {
                    if (Objects.equals(candidate.getKey(), record.getKey())) {
                        existing = candidate;
                        break;
                    }
                }
                if (existing == null) {
                    union.add(record);
                } else if (record.getLockVersion() != null
                        && (existing.getLockVersion() == null
                            || record.getLockVersion() > existing.getLockVersion())) {
                    existing.setLockVersion(record.getLockVersion());
                }
            }
        }
        return union;
    }

    /** The absorption record for {@code key}, or null when that key was never merged into this row. */
    final MergedKeyRecord findMergedKey(UUID key) {
        for (MergedKeyRecord record : mergedKeys) {
            if (Objects.equals(record.getKey(), key)) {
                return record;
            }
        }
        return null;
    }

    final void recordMergedKey(UUID key, Long absorbedLockVersion) {
        MergedKeyRecord existing = findMergedKey(key);
        if (existing == null) {
            mergedKeys.add(new MergedKeyRecord(key, absorbedLockVersion));
        } else {
            existing.setLockVersion(absorbedLockVersion);
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o; //only equal when it is the same live object
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(accountId);
    }

    /** One absorbed key + the lock version its row had when absorbed (see {@link #mergedKeys}). */
    @JsonAutoDetectFieldsOnly
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static final class MergedKeyRecord {

        private UUID key;

        @Setter(AccessLevel.PACKAGE)
        private Long lockVersion;
    }
}
