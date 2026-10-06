package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.region.flag.FlagValue;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import jakarta.annotation.Nullable;

/** A local flag value of a region, or of one of its groups, changed. */
public class RegionFlagChangedEvent extends RegionEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionFlagChangedEvent.class);
    }

    private final @Nullable AccessGroup group;
    private final RegionFlag<?> flag;
    private final @Nullable FlagValue<?> oldValue;
    private final @Nullable FlagValue<?> newValue;
    private final FlagChangeCause cause;

    public RegionFlagChangedEvent(Region region, @Nullable AccessGroup group, RegionFlag<?> flag,
                                  @Nullable FlagValue<?> oldValue, @Nullable FlagValue<?> newValue, FlagChangeCause cause) {
        super(region);
        this.group = group;
        this.flag = flag;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.cause = cause;
    }

    /** The group whose value changed; null when it is the region's own value. */
    public @Nullable AccessGroup getGroup() {
        return group;
    }

    public RegionFlag<?> getFlag() {
        return flag;
    }

    /** The live value before; null when there was none. */
    public @Nullable FlagValue<?> getOldValue() {
        return oldValue;
    }

    /** The live value now; null when there is none - an expired temporary value may uncover the permanent one. */
    public @Nullable FlagValue<?> getNewValue() {
        return newValue;
    }

    public FlagChangeCause getCause() {
        return cause;
    }
}
