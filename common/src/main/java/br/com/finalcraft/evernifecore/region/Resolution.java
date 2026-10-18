package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** How {@link RegionLookup#explain} reached a flag's value: the value, the region it came from, and every step on the way. */
public final class Resolution<T> {

    private final RegionFlag<T> flag;
    private final @Nullable T value;
    private final @Nullable Region source;
    private final Reason reason;
    private final List<Step> steps;

    Resolution(RegionFlag<T> flag, @Nullable T value, @Nullable Region source, Reason reason, List<Step> steps) {
        this.flag = flag;
        this.value = value;
        this.source = source;
        this.reason = reason;
        this.steps = Collections.unmodifiableList(steps);
    }

    /**
     * The flag of the asked flag's chain whose value this is: the asked flag itself, or the ancestor an undefined
     * refinement read its value from (see {@link RegionFlag#getParent()}).
     */
    public RegionFlag<T> getFlag() {
        return flag;
    }

    /** What {@code resolve} answers for the same question. */
    public @Nullable T getValue() {
        return value;
    }

    /** The region whose value (or group's value) won; null for the default or a bypass. */
    public @Nullable Region getSource() {
        return source;
    }

    /**
     * Why the value is what it is: {@link Reason#GROUP_VALUE}, {@link Reason#LOCAL_VALUE} or
     * {@link Reason#INHERITED} for a value some region gave, {@link Reason#BYPASS} or
     * {@link Reason#DEFAULT} for the flag's default.
     */
    public Reason getReason() {
        return reason;
    }

    /** Every step looked at; empty when the resolution was not asked to record them. */
    public List<Step> getSteps() {
        return steps;
    }

    @Override
    public String toString() {
        return value + " of " + flag.getKey() + " from " + source + " " + steps;
    }

    public enum Reason {
        /** The subject holds a bypass permission: the flag reads as its default. */
        BYPASS,
        /** A group of the region that contains the subject defines the flag. */
        GROUP_VALUE,
        /** The region defines the flag itself. */
        LOCAL_VALUE,
        /** The region does not define it; an ancestor does. */
        INHERITED,
        /** A parent of another candidate: its child speaks for it. */
        SKIPPED_ANCESTOR,
        /** Neither the region, its groups nor its ancestors define it. */
        NO_VALUE,
        /** Regions of the same priority disagree; the flag picked this one. */
        TIE,
        /** Nothing defines it: the flag's default. */
        DEFAULT
    }

    /** One thing the resolution looked at. */
    public static final class Step {

        private final RegionFlag<?> flag;
        private final @Nullable Region region;
        private final int priority;
        private final @Nullable AccessGroup group;
        private final @Nullable Object value;
        private final Reason reason;

        Step(RegionFlag<?> flag, @Nullable Region region, int priority, @Nullable AccessGroup group, @Nullable Object value, Reason reason) {
            this.flag = flag;
            this.region = region;
            this.priority = priority;
            this.group = group;
            this.value = value;
            this.reason = reason;
        }

        /** The flag asked at this step: the asked flag, or an ancestor of it once a refinement climbs. */
        public RegionFlag<?> getFlag() {
            return flag;
        }

        /** The region looked at; for {@link Reason#INHERITED} the ancestor that gave the value; null for bypass and default. */
        public @Nullable Region getRegion() {
            return region;
        }

        /** The priority band the step belongs to. */
        public int getPriority() {
            return priority;
        }

        public @Nullable AccessGroup getGroup() {
            return group;
        }

        public @Nullable Object getValue() {
            return value;
        }

        public Reason getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return reason + "(" + flag.getKey() + " " + (region == null ? "-" : region.getId()) + (group == null ? "" : "/" + group.getName())
                    + ", p" + priority + ", " + value + ")";
        }
    }
}
