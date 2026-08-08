package br.com.finalcraft.evernifecore.pageviewer;

import jakarta.annotation.Nullable;

import java.time.Duration;

/**
 * How long a {@link PageSnapshot} stays good for. The cost of reading a page's source is known only
 * to whoever wrote the page - a list of online players is free, a top-balance query over thousands
 * of accounts is not - so the answer is declared there rather than configured server-wide.
 */
@FunctionalInterface
public interface CachePolicy {

    /** Whether {@code snapshot} may still be served. A {@code null} snapshot never can. */
    boolean isValid(@Nullable PageSnapshot<?> snapshot);

    /** Good for {@code duration} after it was built. */
    static CachePolicy ttl(Duration duration) {
        long millis = duration.toMillis();
        return snapshot -> snapshot != null && System.currentTimeMillis() - snapshot.getBuiltAt() < millis;
    }

    /** Never reused: every send consults the source again. For a small list, or data that cannot lag. */
    static CachePolicy none() {
        return snapshot -> false;
    }

    /** Kept until {@link PageViewer#invalidate()} says otherwise - for a page whose owner knows when it changed. */
    static CachePolicy manual() {
        return snapshot -> snapshot != null;
    }
}
