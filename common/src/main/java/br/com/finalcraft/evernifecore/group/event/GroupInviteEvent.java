package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.Invite;

/** An invite was sent, accepted, revoked or expired. */
public class GroupInviteEvent extends GroupEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GroupInviteEvent.class);
    }

    public enum Change { SENT, ACCEPTED, REVOKED, EXPIRED }

    private final Invite invite;
    private final Change change;

    public GroupInviteEvent(Group group, Invite invite, Change change) {
        super(group);
        this.invite = invite;
        this.change = change;
    }

    public Invite getInvite() {
        return invite;
    }

    public Change getChange() {
        return change;
    }
}
