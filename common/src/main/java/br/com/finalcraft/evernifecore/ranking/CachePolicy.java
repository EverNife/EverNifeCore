package br.com.finalcraft.evernifecore.ranking;

import br.com.finalcraft.evernifecore.time.ECClock;
import jakarta.annotation.Nullable;

import java.time.Duration;

/**
 * How long a {@link RankedSnapshot} stays good for. The cost of reading a list's source is known only to whoever
 * wrote the list - the online players are free, a top-balance query over thousands of accounts is not - so the answer
 * is declared there rather than configured server-wide.
 */
@FunctionalInterface
public interface CachePolicy {

    /** Whether {@code snapshot} may still be served. A {@code null} snapshot never can. */
    boolean isValid(@Nullable RankedSnapshot<?> snapshot);

    /** Good for {@code duration} after it was built, on the {@link ECClock}. */
    static CachePolicy ttl(Duration duration) {
        long millis = duration.toMillis();
        return snapshot -> snapshot != null && ECClock.now().toEpochMilli() - snapshot.getBuiltAt() < millis;
    }

    /** Never reused: every read consults the source again. For a small list, or data that cannot lag. */
    static CachePolicy none() {
        return snapshot -> false;
    }

    /** Kept until {@link RankedList#invalidate()} says otherwise - for a list whose owner knows when it changed. */
    static CachePolicy manual() {
        return snapshot -> snapshot != null;
    }
}
