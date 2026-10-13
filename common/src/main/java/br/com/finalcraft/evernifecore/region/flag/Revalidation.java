package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.region.RegionTime;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;

/**
 * When a {@link FlagCategory#SESSION} flag's effective value is checked again for a player who has not crossed a
 * region boundary. Protection flags resolve on every event and data flags never apply, so neither reads this.
 */
public final class Revalidation {

    public enum Kind {
        /** Only when the set of regions the player is in changes. */
        ON_BOUNDARY,
        /** On every event and tick: no cache at all. */
        EVERY_LOOKUP,
        /** On the boundary and every {@link #getInterval()}; {@link FlagSettings#getRevalidationInterval()} overrides it. */
        INTERVAL,
        /** On the boundary and whenever {@link #nextCheckAt} says. */
        CUSTOM
    }

    private static final Revalidation ON_BOUNDARY = new Revalidation(Kind.ON_BOUNDARY, null, null);
    private static final Revalidation EVERY_LOOKUP = new Revalidation(Kind.EVERY_LOOKUP, null, null);

    private final Kind kind;
    private final @Nullable Duration interval;
    private final @Nullable Function<SessionContext, Instant> nextCheckAt;

    private Revalidation(Kind kind, @Nullable Duration interval, @Nullable Function<SessionContext, Instant> nextCheckAt) {
        this.kind = kind;
        this.interval = interval;
        this.nextCheckAt = nextCheckAt;
    }

    /** The default. */
    public static Revalidation onBoundary() {
        return ON_BOUNDARY;
    }

    public static Revalidation everyLookup() {
        return EVERY_LOOKUP;
    }

    /** @throws IllegalArgumentException when {@code every} is zero or negative */
    public static Revalidation interval(Duration every) {
        if (every == null || every.isZero() || every.isNegative()) {
            throw new IllegalArgumentException("Revalidation.interval(" + every + ") needs a positive duration, e.g. Duration.ofSeconds(30);"
                    + " for a check on every lookup use Revalidation.everyLookup().");
        }
        return new Revalidation(Kind.INTERVAL, every, null);
    }

    /** The flag says, per player, when to look again. */
    public static Revalidation custom(Function<SessionContext, Instant> nextCheckAt) {
        return new Revalidation(Kind.CUSTOM, null, Objects.requireNonNull(nextCheckAt, "nextCheckAt"));
    }

    /** Checks again {@code margin} before an effect lasting {@code effect} runs out: now + effect - margin. */
    public static Revalidation before(Duration effect, Duration margin) {
        Duration lead = effect.minus(margin);
        return custom(context -> RegionTime.now().plus(lead));
    }

    public Kind getKind() {
        return kind;
    }

    /** The period of an {@link Kind#INTERVAL} policy; null for the others. */
    public @Nullable Duration getInterval() {
        return interval;
    }

    /** When to check {@code context} again under a {@link Kind#CUSTOM} policy; null for the others. */
    public @Nullable Instant nextCheckAt(SessionContext context) {
        return nextCheckAt == null ? null : nextCheckAt.apply(context);
    }
}
