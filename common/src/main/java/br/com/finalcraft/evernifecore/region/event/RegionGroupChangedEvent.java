package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.GroupChange;
import jakarta.annotation.Nullable;

import java.util.UUID;

/** An access group of a region changed. A change of one of the group's flag values is a {@link RegionFlagChangedEvent} instead. */
public class RegionGroupChangedEvent extends RegionEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionGroupChangedEvent.class);
    }

    private final AccessGroup group;
    private final GroupChange change;
    private final @Nullable UUID player;

    public RegionGroupChangedEvent(Region region, AccessGroup group, GroupChange change, @Nullable UUID player) {
        super(region);
        this.group = group;
        this.change = change;
        this.player = player;
    }

    public AccessGroup getGroup() {
        return group;
    }

    public GroupChange getChange() {
        return change;
    }

    /** The member added, removed or expired; null for the other changes. */
    public @Nullable UUID getPlayer() {
        return player;
    }
}
