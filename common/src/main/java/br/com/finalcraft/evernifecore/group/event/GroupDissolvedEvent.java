package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.group.DissolveCause;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupMember;

import java.util.Collection;

/** A group was dissolved; every member already left with {@code LeaveCause.DISSOLVED}. */
public class GroupDissolvedEvent extends GroupEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GroupDissolvedEvent.class);
    }

    private final DissolveCause cause;
    private final Collection<GroupMember> lastMembers;

    public GroupDissolvedEvent(Group group, DissolveCause cause, Collection<GroupMember> lastMembers) {
        super(group);
        this.cause = cause;
        this.lastMembers = lastMembers;
    }

    public DissolveCause getCause() {
        return cause;
    }

    /** The members the group had when it was dissolved. Read-only. */
    public Collection<GroupMember> getLastMembers() {
        return lastMembers;
    }
}
