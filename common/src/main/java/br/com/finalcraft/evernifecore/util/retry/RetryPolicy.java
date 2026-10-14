package br.com.finalcraft.evernifecore.util.retry;

import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * How long to keep trying something that failed: how many tries at most, how long to wait between them, and how
 * long in total before giving up. Immutable - every builder method returns a new policy.
 *
 * <pre>{@code
 * RetryPolicy.attempts(5).every(Duration.ofSeconds(2)).giveUpAfter(Duration.ofSeconds(30));
 * RetryPolicy.attempts(8).backoff(Duration.ofSeconds(1), 2, Duration.ofSeconds(30));
 * RetryPolicy.forever(Duration.ofSeconds(30));
 * }</pre>
 *
 * The policy only answers {@link #delayBefore}; the caller runs the tries and measures the time they took, with
 * whatever clock it trusts.
 */
public final class RetryPolicy {

    private static final int UNBOUNDED = 0;

    private final int maxAttempts;
    private final Duration firstDelay;
    private final double multiplier;
    private final Duration maxDelay;
    private final @Nullable Duration giveUpAfter;

    private RetryPolicy(int maxAttempts, Duration firstDelay, double multiplier, Duration maxDelay, @Nullable Duration giveUpAfter) {
        this.maxAttempts = maxAttempts;
        this.firstDelay = firstDelay;
        this.multiplier = multiplier;
        this.maxDelay = maxDelay;
        this.giveUpAfter = giveUpAfter;
    }

    /** At most {@code maxAttempts} tries, the first included, one right after the other until {@link #every} or {@link #backoff} says otherwise. */
    public static RetryPolicy attempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("A RetryPolicy needs at least 1 attempt, got " + maxAttempts
                    + " - use attempts(1) to try once and never retry.");
        }
        return new RetryPolicy(maxAttempts, Duration.ZERO, 1, Duration.ZERO, null);
    }

    /** Tries until it succeeds, waiting {@code every} between tries; {@link #giveUpAfter} can still put a ceiling on it. */
    public static RetryPolicy forever(Duration every) {
        Duration delay = requireDelay(every, "every");
        return new RetryPolicy(UNBOUNDED, delay, 1, delay, null);
    }

    /** The same policy, waiting {@code delay} before every retry. */
    public RetryPolicy every(Duration delay) {
        Duration checked = requireDelay(delay, "every");
        return new RetryPolicy(maxAttempts, checked, 1, checked, giveUpAfter);
    }

    /**
     * The same policy, waiting {@code first} before the first retry and {@code multiplier} times longer before each
     * one after it, never more than {@code max}.
     */
    public RetryPolicy backoff(Duration first, double multiplier, Duration max) {
        Duration checkedFirst = requireDelay(first, "backoff first");
        Duration checkedMax = requireDelay(max, "backoff max");
        if (!(multiplier >= 1) || Double.isInfinite(multiplier)) {
            throw new IllegalArgumentException("A RetryPolicy backoff multiplier must be a finite number >= 1, got " + multiplier
                    + " - 1 waits the same every time, 2 doubles the wait.");
        }
        if (checkedMax.compareTo(checkedFirst) < 0) {
            throw new IllegalArgumentException("A RetryPolicy backoff max (" + describe(checkedMax) + ") cannot be shorter than its first wait ("
                    + describe(checkedFirst) + ") - raise the max, or use every(" + describe(checkedFirst) + ") for a fixed wait.");
        }
        return new RetryPolicy(maxAttempts, checkedFirst, multiplier, checkedMax, giveUpAfter);
    }

    /** The same policy, giving up before any retry that would start more than {@code total} after the first try. */
    public RetryPolicy giveUpAfter(Duration total) {
        Objects.requireNonNull(total, "'total' cannot be null in RetryPolicy.giveUpAfter - pass a Duration such as Duration.ofSeconds(30).");
        if (total.isNegative() || total.isZero()) {
            throw new IllegalArgumentException("A RetryPolicy must give up after a positive time, got " + describe(total)
                    + " - use attempts(1) to try once and never retry.");
        }
        return new RetryPolicy(maxAttempts, firstDelay, multiplier, maxDelay, total);
    }

    /**
     * How long to wait before try number {@code attempt} (2 for the first retry), when {@code elapsed} has passed
     * since the first try started; null when the policy gives up instead.
     */
    public @Nullable Duration delayBefore(int attempt, Duration elapsed) {
        if (attempt < 2) {
            throw new IllegalArgumentException("delayBefore asks about a retry, so attempt starts at 2 (the first retry), got " + attempt + ".");
        }
        if (maxAttempts != UNBOUNDED && attempt > maxAttempts) {
            return null;
        }
        Duration delay = delayOf(attempt - 2);
        if (giveUpAfter != null && elapsed.plus(delay).compareTo(giveUpAfter) > 0) {
            return null;
        }
        return delay;
    }

    private Duration delayOf(int retryIndex) {
        if (multiplier == 1 || retryIndex == 0) {
            return firstDelay;
        }
        double millis = firstDelay.toMillis() * Math.pow(multiplier, retryIndex);
        return millis >= maxDelay.toMillis() ? maxDelay : Duration.ofMillis((long) millis);
    }

    /** The most tries this policy makes; 0 when only time, or nothing, ends it. */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /** The longest it keeps trying after the first try; null when it has no time ceiling. */
    public @Nullable Duration getGiveUpAfter() {
        return giveUpAfter;
    }

    private static Duration requireDelay(Duration delay, String what) {
        Objects.requireNonNull(delay, "'" + what + "' cannot be null in a RetryPolicy - pass a Duration such as Duration.ofSeconds(2).");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("A RetryPolicy wait cannot be negative (" + what + " = " + describe(delay)
                    + ") - use Duration.ZERO to retry at once.");
        }
        return delay;
    }

    private static String describe(Duration duration) {
        long millis = duration.toMillis();
        return millis % 1000 == 0 ? millis / 1000 + "s" : millis + "ms";
    }

    @Override
    public String toString() {
        String attempts = maxAttempts == UNBOUNDED ? "no attempt limit" : "at most " + maxAttempts + " attempts";
        String waits = multiplier == 1 ? "every " + describe(firstDelay)
                : "backoff from " + describe(firstDelay) + " x" + multiplier + " up to " + describe(maxDelay);
        String ceiling = giveUpAfter == null ? "" : ", giving up after " + describe(giveUpAfter);
        return "RetryPolicy[" + attempts + ", " + waits + ceiling + "]";
    }
}
