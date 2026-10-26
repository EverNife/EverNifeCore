package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.group.Group;
import jakarta.annotation.Nullable;

import java.util.UUID;

/** The leader changed by {@code setLeader}, or the leader left and the group has none now. */
public class GroupLeaderChangedEvent extends GroupEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GroupLeaderChangedEvent.class);
    }

    private final @Nullable UUID oldLeader;
    private final @Nullable UUID newLeader;

    public GroupLeaderChangedEvent(Group group, @Nullable UUID oldLeader, @Nullable UUID newLeader) {
        super(group);
        this.oldLeader = oldLeader;
        this.newLeader = newLeader;
    }

    public @Nullable UUID getOldLeader() {
        return oldLeader;
    }

    public @Nullable UUID getNewLeader() {
        return newLeader;
    }
}
