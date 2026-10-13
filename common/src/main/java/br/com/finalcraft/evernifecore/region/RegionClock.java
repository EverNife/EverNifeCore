package br.com.finalcraft.evernifecore.region;

import jakarta.annotation.Nullable;

import java.time.Clock;
import java.time.Instant;

/**
 * The one source of "now" for everything region-related: flag deadlines, memberships, throttles and sweeps all ask
 * {@link #now()}, so a test that swaps the clock moves every deadline at once.
 */
public final class RegionClock {

    private static volatile @Nullable Clock clock;

    private RegionClock() {
    }

    public static Instant now() {
        Clock current = clock;
        return current == null ? Instant.now() : current.instant();
    }

    /** The clock {@link #now()} reads; the system UTC clock unless one was set. */
    public static Clock clock() {
        Clock current = clock;
        return current == null ? Clock.systemUTC() : current;
    }

    /** Replaces the clock; {@code null} goes back to the system clock. */
    public static void setClock(@Nullable Clock newClock) {
        clock = newClock;
    }
}
