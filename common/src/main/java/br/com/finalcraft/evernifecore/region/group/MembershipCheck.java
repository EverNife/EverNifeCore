package br.com.finalcraft.evernifecore.region.group;

import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * How often a group asks its predicate and permission again for a player. Explicit members are a set lookup and
 * never cached. The text form ({@link #toText()} / {@link #parse}) is {@code exact}, {@code on-boundary} or
 * {@code interval:<ISO-8601 duration>}, e.g. {@code interval:PT30S}.
 */
public final class MembershipCheck {

    public enum Type {
        /** Ask on every check; nothing is cached. The default. */
        EXACT,
        /** Keep the answer until the set of regions the player is in changes. */
        ON_BOUNDARY,
        /** Keep the answer until the region set changes or the interval passes, whichever comes first. */
        INTERVAL
    }

    private static final MembershipCheck EXACT_CHECK = new MembershipCheck(Type.EXACT, null);
    private static final MembershipCheck ON_BOUNDARY_CHECK = new MembershipCheck(Type.ON_BOUNDARY, null);
    private static final String INTERVAL_PREFIX = "interval:";

    private final Type type;
    private final @Nullable Duration interval;

    private MembershipCheck(Type type, @Nullable Duration interval) {
        this.type = type;
        this.interval = interval;
    }

    public static MembershipCheck exact() {
        return EXACT_CHECK;
    }

    public static MembershipCheck onBoundary() {
        return ON_BOUNDARY_CHECK;
    }

    /** @throws IllegalArgumentException when {@code every} is zero or negative */
    public static MembershipCheck interval(Duration every) {
        if (every == null || every.isZero() || every.isNegative()) {
            throw new IllegalArgumentException("MembershipCheck.interval(" + every + ") needs a positive duration, e.g. Duration.ofSeconds(30);"
                    + " to ask on every check use MembershipCheck.exact().");
        }
        return new MembershipCheck(Type.INTERVAL, every);
    }

    /**
     * The inverse of {@link #toText()}.
     *
     * @throws IllegalArgumentException naming the text and the three accepted forms
     */
    public static MembershipCheck parse(String text) {
        if ("exact".equals(text)) {
            return EXACT_CHECK;
        }
        if ("on-boundary".equals(text)) {
            return ON_BOUNDARY_CHECK;
        }
        if (text != null && text.startsWith(INTERVAL_PREFIX)) {
            try {
                return interval(Duration.parse(text.substring(INTERVAL_PREFIX.length())));
            } catch (DateTimeParseException notADuration) {
                //falls through to the message below
            }
        }
        throw new IllegalArgumentException("Invalid membership check '" + text + "': write exact, on-boundary or interval:<ISO-8601 duration>,"
                + " e.g. interval:PT30S.");
    }

    public Type getType() {
        return type;
    }

    /** The period of an {@link Type#INTERVAL} check; null for the others. */
    public @Nullable Duration getInterval() {
        return interval;
    }

    public String toText() {
        switch (type) {
            case EXACT:
                return "exact";
            case ON_BOUNDARY:
                return "on-boundary";
            default:
                return INTERVAL_PREFIX + interval;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MembershipCheck && type == ((MembershipCheck) other).type
                && Objects.equals(interval, ((MembershipCheck) other).interval);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + Objects.hashCode(interval);
    }

    @Override
    public String toString() {
        return toText();
    }
}
