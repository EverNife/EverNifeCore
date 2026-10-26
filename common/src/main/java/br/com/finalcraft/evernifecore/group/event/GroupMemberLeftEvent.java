package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupMember;
import br.com.finalcraft.evernifecore.group.LeaveCause;

import java.util.UUID;

/** A member left, for whatever reason {@link #getCause()} names. */
public class GroupMemberLeftEvent extends GroupEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GroupMemberLeftEvent.class);
    }

    private final UUID player;
    private final GroupMember member;
    private final LeaveCause cause;

    public GroupMemberLeftEvent(Group group, UUID player, GroupMember member, LeaveCause cause) {
        super(group);
        this.player = player;
        this.member = member;
        this.cause = cause;
    }

    public UUID getPlayer() {
        return player;
    }

    /** The membership as it was. */
    public GroupMember getMember() {
        return member;
    }

    public LeaveCause getCause() {
        return cause;
    }
}
