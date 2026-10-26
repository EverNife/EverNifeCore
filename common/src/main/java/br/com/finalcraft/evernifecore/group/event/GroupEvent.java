package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.group.Group;

import java.util.Objects;

/**
 * Something happened to one group. Every group event is posted only when somebody listens, on the thread of whoever
 * made the change; a change of a group local to a region has {@code getGroup().getRegion() != null}. Loading is not a
 * change: nothing is posted for what a load reads.
 */
public abstract class GroupEvent extends ECEvent implements IECEvent {

    private final Group group;

    protected GroupEvent(Group group) {
        this.group = Objects.requireNonNull(group, "'group' cannot be null when building a group event!");
    }

    public Group getGroup() {
        return group;
    }

    /** The plugin that owns the group (the region's owner for a region-local group). */
    public ECPluginData getOwner() {
        return group.getOwner();
    }
}
