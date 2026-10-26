package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.group.Group;

/** A group was created: by its manager, or by the first {@code Region.getGroup(name)} of a name. */
public class GroupCreatedEvent extends GroupEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GroupCreatedEvent.class);
    }

    public GroupCreatedEvent(Group group) {
        super(group);
    }
}
