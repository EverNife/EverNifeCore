package br.com.finalcraft.evernifecore.api.events.account;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.playerdata.account.AccountActor;
import br.com.finalcraft.evernifecore.playerdata.account.AccountMember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fired after two accounts are fused into one - {@code /ecaccount link} of two platform identities, or
 * a transitive {@code linkExternal} fusion. It is a bus-only {@link IECEvent} (never mirrored into a
 * platform bus), posted on the account pipeline's completion thread. Subscribe on
 * {@link br.com.finalcraft.evernifecore.eventbus.ECEventBus#global()}.
 *
 * <p>{@link #getCanonicalAccountId()} is the account that survives. {@link #getTargetAccountId()} and
 * {@link #getSourceAccountId()} are the two inputs: one equals the canonical when an explicit account
 * absorbed the other, and both differ from it when two never-linked singletons fused into a freshly
 * minted id. Each member's account-wide DATA is absorbed lazily at that member's next login.</p>
 */
public class ECAccountMergedEvent implements IECEvent {

    private final UUID canonicalAccountId;
    private final UUID targetAccountId;
    private final UUID sourceAccountId;
    private final List<AccountMember> membersAfter;
    private final AccountActor actor;

    public ECAccountMergedEvent(UUID canonicalAccountId, UUID targetAccountId, UUID sourceAccountId,
                                List<AccountMember> membersAfter, AccountActor actor) {
        this.canonicalAccountId = canonicalAccountId;
        this.targetAccountId = targetAccountId;
        this.sourceAccountId = sourceAccountId;
        this.membersAfter = Collections.unmodifiableList(new ArrayList<>(membersAfter));
        this.actor = actor;
    }

    /** The account that survives the merge and holds the member union. */
    public UUID getCanonicalAccountId() {
        return canonicalAccountId;
    }

    /** The first input account of the merge (the {@code target} of {@code mergeAccounts}). */
    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    /** The second input account of the merge (the {@code source} of {@code mergeAccounts}). */
    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    /** The canonical account's full membership after the merge (an unmodifiable snapshot). */
    public List<AccountMember> getMembersAfter() {
        return membersAfter;
    }

    /** Who performed the merge. */
    public AccountActor getActor() {
        return actor;
    }

    @Override
    public String toString() {
        return "ECAccountMergedEvent{canonical=" + canonicalAccountId + ", target=" + targetAccountId
                + ", source=" + sourceAccountId + ", by=" + actor + "}";
    }
}
