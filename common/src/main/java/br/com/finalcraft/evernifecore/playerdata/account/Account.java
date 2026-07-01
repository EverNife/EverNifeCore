package br.com.finalcraft.evernifecore.playerdata.account;

import br.com.finalcraft.everydatabase.util.JsonAutoDetectFieldsOnly;
import br.com.finalcraft.everydatabase.versioned.OptimisticLock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A canonical account grouping N platform identities ({@link AccountMember}) under a single
 * {@code accountId}. It is the key an account-wide section
 * ({@link br.com.finalcraft.evernifecore.playerdata.AccountSection}) is stored under, so linking
 * identities coalesces their account-wide data.
 *
 * <p>An unlinked platform uuid resolves to its own <b>singleton account</b>: {@code accountId} equals
 * the platform uuid and {@code members} holds the single identity. Only {@code /account link} (see
 * {@link Accounts}) merges two accounts, at which point one {@code accountId} becomes canonical.</p>
 *
 * <p>Persisted as a storage entity keyed by {@link #getAccountId()}, in its own collection on the
 * shared account backend (see {@link Accounts}).</p>
 *
 * <p><b>Alias rows.</b> The collection is only ever looked up by primary key, so a linked identity
 * must stay resolvable by its OWN uuid: linking writes, for each non-canonical member, an alias row
 * keyed by the member uuid whose {@link #getAliasOf()} points at the canonical account. Resolution
 * follows that pointer one hop (see {@link Accounts#account(UUID)}) - no index or scan needed.</p>
 */
@Getter
@EqualsAndHashCode(of = "accountId")
@JsonAutoDetectFieldsOnly
public class Account {

    private UUID accountId;

    /** The identities that belong to this account. The returned list is the live backing list. */
    private List<AccountMember> members = new ArrayList<>();

    /** The canonical accountId this alias points at, or {@code null} on a real account row. */
    private UUID aliasOf;

    @OptimisticLock
    private Long lockVersion;

    /**
     * True only on the in-memory singleton fabricated for a uuid that was never linked. A decoded
     * (stored) account is never a singleton - only explicitly linked accounts persist rows.
     */
    @JsonIgnore
    private transient boolean singleton;

    public Account() {
        //Jackson no-arg constructor
    }

    public Account(UUID accountId) {
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
    }

    /**
     * Builds the singleton account of an unlinked platform identity: {@code accountId == uuid} and a
     * single member. Used as the not-yet-persisted default the first time a uuid is resolved.
     */
    public static Account singleton(UUID uuid, String provider, String providerUid, String name) {
        Account account = new Account(uuid);
        account.singleton = true;
        account.members.add(new AccountMember(provider, providerUid, name));
        return account;
    }

    /** Builds the alias row that keeps {@code memberUuid} resolvable after being linked into {@code canonicalId}. */
    public static Account alias(UUID memberUuid, UUID canonicalId) {
        Account account = new Account(memberUuid);
        account.aliasOf = Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        return account;
    }

    /** True when this row only redirects a linked member uuid to its canonical account. */
    public boolean isAlias() {
        return aliasOf != null;
    }

    /**
     * Turns this row into an alias pointing at {@code canonicalId} (keeping the row's optimistic
     * lock, so rewriting an existing alias/account row lands cleanly). Members do not belong on an
     * alias row and are dropped.
     */
    void redirectTo(UUID canonicalId) {
        this.aliasOf = Objects.requireNonNull(canonicalId, "canonicalId cannot be null");
        this.members = new ArrayList<>();
        this.singleton = false;
    }

    /**
     * Adds a member, enforcing the {@code UNIQUE(provider, providerUid)} invariant WITHIN this account
     * (a duplicate identity is a no-op that returns {@code false}). Cross-account uniqueness is the
     * caller's responsibility (see {@link Accounts}).
     *
     * @return {@code true} when the member was added, {@code false} when this identity was already present
     */
    public boolean addMember(AccountMember member) {
        Objects.requireNonNull(member, "member cannot be null");
        //AccountMember equality IS identity equality (provider + providerUid)
        if (members.contains(member)) {
            return false;
        }
        members.add(member);
        return true;
    }

    /** The member matching {@code (provider, providerUid)}, or {@code null} when none belongs here. */
    public AccountMember findMember(String provider, String providerUid) {
        for (AccountMember member : members) {
            if (Objects.equals(member.getProvider(), provider)
                    && Objects.equals(member.getProviderUid(), providerUid)) {
                return member;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Account{" + accountId + ", members=" + members + "}";
    }
}
