package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.region.RegionId;

import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * A named stretch of time, {@code [from, to)}: an event, a ranking season. As a {@link Window#season(Season) window} it
 * opens at {@code from} and closes for good at {@code to}.
 */
public final class Season {

    private final String id;
    private final ZonedDateTime from;
    private final ZonedDateTime to;

    private Season(String id, ZonedDateTime from, ZonedDateTime to) {
        this.id = id;
        this.from = from;
        this.to = to;
    }

    /**
     * @throws IllegalArgumentException when the id breaks {@code [a-z0-9_-]{1,64}} or {@code to} is not after {@code from}
     */
    public static Season of(String id, ZonedDateTime from, ZonedDateTime to) {
        String checked = RegionId.requireName("season", id);
        if (!to.toInstant().isAfter(from.toInstant())) {
            throw new IllegalArgumentException("Season '" + id + "' ends at " + to + ", which is not after its start " + from
                    + ": a season runs from its start up to, not including, its end.");
        }
        return new Season(checked, from, to);
    }

    public String getId() {
        return id;
    }

    public ZonedDateTime getFrom() {
        return from;
    }

    public ZonedDateTime getTo() {
        return to;
    }

    /** Whether {@code now} falls in {@code [from, to)}. */
    public boolean contains(Instant now) {
        return !now.isBefore(from.toInstant()) && now.isBefore(to.toInstant());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Season)) {
            return false;
        }
        Season season = (Season) other;
        return id.equals(season.id) && from.toInstant().equals(season.from.toInstant()) && to.toInstant().equals(season.to.toInstant());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "season:" + id + " [" + from + ", " + to + ")";
    }
}
