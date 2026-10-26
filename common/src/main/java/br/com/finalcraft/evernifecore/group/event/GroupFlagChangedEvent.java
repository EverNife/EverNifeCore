package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.group.Group;
import jakarta.annotation.Nullable;

/**
 * A flag value of a group of a {@code GroupManager} changed. A group local to a region posts
 * {@code RegionFlagChangedEvent} with {@code getGroup()} instead, as the region's own values do.
 */
public class GroupFlagChangedEvent extends GroupEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(GroupFlagChangedEvent.class);
    }

    private final Flag<?> flag;
    private final @Nullable FlagValue<?> oldValue;
    private final @Nullable FlagValue<?> newValue;
    private final FlagChangeCause cause;

    public GroupFlagChangedEvent(Group group, Flag<?> flag, @Nullable FlagValue<?> oldValue, @Nullable FlagValue<?> newValue,
                                 FlagChangeCause cause) {
        super(group);
        this.flag = flag;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.cause = cause;
    }

    public Flag<?> getFlag() {
        return flag;
    }

    /** The live value before; null when there was none. */
    public @Nullable FlagValue<?> getOldValue() {
        return oldValue;
    }

    /** The live value now; null when there is none. */
    public @Nullable FlagValue<?> getNewValue() {
        return newValue;
    }

    public FlagChangeCause getCause() {
        return cause;
    }
}
