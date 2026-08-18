package br.com.finalcraft.evernifecore.api.events.account;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.playerdata.account.AccountActor;
import br.com.finalcraft.evernifecore.playerdata.account.AccountMember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fired after an identity is linked into an account - the "join" side of the account layer. It is a
 * bus-only {@link IECEvent} (never mirrored into a platform bus), posted on the account pipeline's
 * completion thread, so a handler must not assume the main thread. Subscribe on
 * {@link br.com.finalcraft.evernifecore.eventbus.ECEventBus#global()}.
 *
 * <p>This is the event of an identity ATTACHED to an account without fusing a second existing account
 * (the {@code linkExternal} adopt paths). Two whole accounts merging is an {@link ECAccountMergedEvent}
 * instead. The linked identity's account-wide data is absorbed lazily at its next login.</p>
 */
public class ECAccountLinkedEvent implements IECEvent {

    private final UUID accountId;
    private final AccountMember linkedMember;
    private final List<AccountMember> membersAfter;
    private final AccountActor actor;

    public ECAccountLinkedEvent(UUID accountId, AccountMember linkedMember,
                                List<AccountMember> membersAfter, AccountActor actor) {
        this.accountId = accountId;
        this.linkedMember = linkedMember;
        this.membersAfter = Collections.unmodifiableList(new ArrayList<>(membersAfter));
        this.actor = actor;
    }

    /** The canonical account the identity was linked into. */
    public UUID getAccountId() {
        return accountId;
    }

    /** The identity that was just linked in. */
    public AccountMember getLinkedMember() {
        return linkedMember;
    }

    /** The account's full membership after the link (an unmodifiable snapshot). */
    public List<AccountMember> getMembersAfter() {
        return membersAfter;
    }

    /** Who performed the link. */
    public AccountActor getActor() {
        return actor;
    }

    @Override
    public String toString() {
        return "ECAccountLinkedEvent{account=" + accountId + ", linked=" + linkedMember
                + ", by=" + actor + "}";
    }
}
