package br.com.finalcraft.evernifecore.api.events.account;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.playerdata.account.AccountActor;
import br.com.finalcraft.evernifecore.playerdata.account.AccountMember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fired after a member leaves an account - {@code /ecaccount unlink} of a platform identity, or
 * {@code unlinkExternal} of an external one. It is a bus-only {@link IECEvent} (never mirrored into a
 * platform bus), posted on the account pipeline's completion thread. Subscribe on
 * {@link br.com.finalcraft.evernifecore.eventbus.ECEventBus#global()}.
 *
 * <p>The account keeps its shared data; the member starts fresh (a platform member re-resolves to its
 * own singleton at its next login).</p>
 */
public class ECAccountUnlinkedEvent implements IECEvent {

    private final UUID accountId;
    private final AccountMember unlinkedMember;
    private final List<AccountMember> membersAfter;
    private final AccountActor actor;

    public ECAccountUnlinkedEvent(UUID accountId, AccountMember unlinkedMember,
                                  List<AccountMember> membersAfter, AccountActor actor) {
        this.accountId = accountId;
        this.unlinkedMember = unlinkedMember;
        this.membersAfter = Collections.unmodifiableList(new ArrayList<>(membersAfter));
        this.actor = actor;
    }

    /** The account the member left (it stays explicit and keeps the shared data). */
    public UUID getAccountId() {
        return accountId;
    }

    /** The identity that left the account. */
    public AccountMember getUnlinkedMember() {
        return unlinkedMember;
    }

    /** The account's remaining membership after the unlink (an unmodifiable snapshot). */
    public List<AccountMember> getMembersAfter() {
        return membersAfter;
    }

    /** Who performed the unlink. */
    public AccountActor getActor() {
        return actor;
    }

    @Override
    public String toString() {
        return "ECAccountUnlinkedEvent{account=" + accountId + ", unlinked=" + unlinkedMember
                + ", by=" + actor + "}";
    }
}
