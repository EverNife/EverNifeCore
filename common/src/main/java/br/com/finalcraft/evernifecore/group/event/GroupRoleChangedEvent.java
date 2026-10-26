package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupRole;

import java.util.Set;
import java.util.UUID;

/** The roles of a member changed. */
public class GroupRoleChangedEvent extends GroupEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GroupRoleChangedEvent.class);
    }

    private final UUID player;
    private final Set<GroupRole> oldRoles;
    private final Set<GroupRole> newRoles;

    public GroupRoleChangedEvent(Group group, UUID player, Set<GroupRole> oldRoles, Set<GroupRole> newRoles) {
        super(group);
        this.player = player;
        this.oldRoles = oldRoles;
        this.newRoles = newRoles;
    }

    public UUID getPlayer() {
        return player;
    }

    public Set<GroupRole> getOldRoles() {
        return oldRoles;
    }

    public Set<GroupRole> getNewRoles() {
        return newRoles;
    }
}
