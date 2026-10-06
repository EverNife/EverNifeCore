package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** How {@link RegionLookup#explain} reached a flag's value: the value, the region it came from, and every step on the way. */
public final class Resolution<T> {

    private final @Nullable T value;
    private final @Nullable Region source;
    private final Step.Reason reason;
    private final List<Step> steps;

    Resolution(@Nullable T value, @Nullable Region source, Step.Reason reason, List<Step> steps) {
        this.value = value;
        this.source = source;
        this.reason = reason;
        this.steps = Collections.unmodifiableList(steps);
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
     * Why the value is what it is: {@link Step.Reason#GROUP_VALUE}, {@link Step.Reason#LOCAL_VALUE} or
     * {@link Step.Reason#INHERITED} for a value some region gave, {@link Step.Reason#BYPASS} or
     * {@link Step.Reason#DEFAULT} for the flag's default.
     */
    public Step.Reason getReason() {
        return reason;
    }

    /** Every step looked at; empty when the resolution was not asked to record them. */
    public List<Step> getSteps() {
        return steps;
    }

    @Override
    public String toString() {
        return value + " from " + source + " " + steps;
    }

    /** One thing the resolution looked at. */
    public static final class Step {

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

        private final @Nullable Region region;
        private final int priority;
        private final @Nullable AccessGroup group;
        private final @Nullable Object value;
        private final Reason reason;

        Step(@Nullable Region region, int priority, @Nullable AccessGroup group, @Nullable Object value, Reason reason) {
            this.region = region;
            this.priority = priority;
            this.group = group;
            this.value = value;
            this.reason = reason;
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
            return reason + "(" + (region == null ? "-" : region.getId()) + (group == null ? "" : "/" + group.getName())
                    + ", p" + priority + ", " + value + ")";
        }
    }
}
