package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.group.GroupManager;

/**
 * A {@link GroupKind} was registered on a {@link GroupManager}, loaded or not. When the manager has already loaded, the
 * stored groups of that kind are read before this is posted.
 */
public class GroupKindRegisteredEvent extends ECEvent implements IECEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GroupKindRegisteredEvent.class);
    }

    private final GroupManager manager;
    private final GroupKind kind;

    public GroupKindRegisteredEvent(GroupManager manager, GroupKind kind) {
        this.manager = manager;
        this.kind = kind;
    }

    public GroupManager getManager() {
        return manager;
    }

    public GroupKind getKind() {
        return kind;
    }
}
