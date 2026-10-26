package br.com.finalcraft.evernifecore.waypoint;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** "Go here": what {@link Waypoints#show} points a player at, how it is labelled, for how long and through which channels. */
public final class Waypoint {

    private final WaypointId id;
    private final WaypointTarget target;
    private final FancyText label;
    private final @Nullable Duration duration;
    private final @Nullable List<String> channels;

    private Waypoint(Builder builder) {
        this.id = builder.id;
        this.target = builder.target;
        this.label = builder.label;
        this.duration = builder.duration;
        this.channels = builder.channels == null ? null : Collections.unmodifiableList(new ArrayList<>(builder.channels));
    }

    public static Builder of(WaypointId id, WaypointTarget target) {
        return new Builder(id, target);
    }

    public WaypointId getId() {
        return id;
    }

    public WaypointTarget getTarget() {
        return target;
    }

    /** The hologram text and the web-map marker name; the id's name when none was given. */
    public FancyText getLabel() {
        return label;
    }

    /** How long it stays; null = until hidden. */
    public @Nullable Duration getDuration() {
        return duration;
    }

    /** The channel names asked for; null = {@code Settings.Waypoint.channels}. */
    public @Nullable List<String> getChannels() {
        return channels;
    }

    public static final class Builder {

        private final WaypointId id;
        private final WaypointTarget target;
        private FancyText label;
        private @Nullable Duration duration;
        private @Nullable List<String> channels;

        private Builder(WaypointId id, WaypointTarget target) {
            this.id = Objects.requireNonNull(id, "id");
            this.target = Objects.requireNonNull(target, "target");
            this.label = FancyText.of(id.getName());
        }

        public Builder label(FancyText label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        public Builder label(String label) {
            return label(FancyText.of(label));
        }

        /** Hides itself after {@code duration}; a zero or negative one is "until hidden". */
        public Builder duration(@Nullable Duration duration) {
            this.duration = duration == null || duration.isZero() || duration.isNegative() ? null : duration;
            return this;
        }

        /** Bare names ({@code compass}) or {@code owner:name} keys; every supported one is used, the others are skipped. */
        public Builder channels(String... channels) {
            this.channels = Arrays.asList(channels);
            return this;
        }

        public Builder channels(List<String> channels) {
            this.channels = new ArrayList<>(channels);
            return this;
        }

        public Waypoint build() {
            return new Waypoint(this);
        }
    }
}
