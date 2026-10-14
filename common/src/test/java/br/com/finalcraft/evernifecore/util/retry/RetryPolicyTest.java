package br.com.finalcraft.evernifecore.util.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    private static final Duration NO_TIME = Duration.ZERO;

    @Test
    void theAttemptCeilingCountsTheFirstTry() {
        RetryPolicy policy = RetryPolicy.attempts(3).every(Duration.ofSeconds(2));

        assertEquals(Duration.ofSeconds(2), policy.delayBefore(2, NO_TIME));
        assertEquals(Duration.ofSeconds(2), policy.delayBefore(3, NO_TIME));
        assertNull(policy.delayBefore(4, NO_TIME), "3 attempts means a 4th never starts");
    }

    @Test
    void theTimeCeilingRefusesTheRetryThatWouldStartPastIt() {
        RetryPolicy policy = RetryPolicy.forever(Duration.ofSeconds(10)).giveUpAfter(Duration.ofSeconds(25));

        assertEquals(Duration.ofSeconds(10), policy.delayBefore(2, Duration.ZERO));
        assertEquals(Duration.ofSeconds(10), policy.delayBefore(3, Duration.ofSeconds(10)));
        assertNull(policy.delayBefore(4, Duration.ofSeconds(20)), "it would start at 30 s, past the 25 s ceiling");
        assertEquals(Duration.ofSeconds(10), policy.delayBefore(4, Duration.ofSeconds(15)), "starting at exactly 25 s is still allowed");
    }

    @Test
    void backoffGrowsAndStopsAtItsMax() {
        RetryPolicy policy = RetryPolicy.attempts(10).backoff(Duration.ofSeconds(1), 2, Duration.ofSeconds(5));

        assertEquals(Duration.ofSeconds(1), policy.delayBefore(2, NO_TIME));
        assertEquals(Duration.ofSeconds(2), policy.delayBefore(3, NO_TIME));
        assertEquals(Duration.ofSeconds(4), policy.delayBefore(4, NO_TIME));
        assertEquals(Duration.ofSeconds(5), policy.delayBefore(5, NO_TIME));
        assertEquals(Duration.ofSeconds(5), policy.delayBefore(10, NO_TIME));
    }

    @Test
    void foreverNeverGivesUp() {
        RetryPolicy policy = RetryPolicy.forever(Duration.ofSeconds(30));

        assertEquals(Duration.ofSeconds(30), policy.delayBefore(2, NO_TIME));
        assertEquals(Duration.ofSeconds(30), policy.delayBefore(Integer.MAX_VALUE, Duration.ofDays(3650)));
        assertEquals(0, policy.getMaxAttempts());
        assertNull(policy.getGiveUpAfter());
    }

    @Test
    void invalidArgumentsAreRefusedWithTheAcceptedValue() {
        IllegalArgumentException noAttempt = assertThrows(IllegalArgumentException.class, () -> RetryPolicy.attempts(0));
        assertTrue(noAttempt.getMessage().contains("attempts(1)"), noAttempt.getMessage());

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> RetryPolicy.attempts(2).every(Duration.ofSeconds(-1)));
        assertTrue(negative.getMessage().contains("Duration.ZERO"), negative.getMessage());

        IllegalArgumentException shrinking = assertThrows(IllegalArgumentException.class,
                () -> RetryPolicy.attempts(2).backoff(Duration.ofSeconds(1), 0.5, Duration.ofSeconds(5)));
        assertTrue(shrinking.getMessage().contains(">= 1"), shrinking.getMessage());

        IllegalArgumentException maxBelowFirst = assertThrows(IllegalArgumentException.class,
                () -> RetryPolicy.attempts(2).backoff(Duration.ofSeconds(5), 2, Duration.ofSeconds(1)));
        assertTrue(maxBelowFirst.getMessage().contains("raise the max"), maxBelowFirst.getMessage());

        IllegalArgumentException noTime = assertThrows(IllegalArgumentException.class,
                () -> RetryPolicy.forever(Duration.ofSeconds(1)).giveUpAfter(Duration.ZERO));
        assertTrue(noTime.getMessage().contains("positive"), noTime.getMessage());
    }

    @Test
    void itDescribesItself() {
        assertEquals("RetryPolicy[at most 5 attempts, every 2s, giving up after 30s]",
                RetryPolicy.attempts(5).every(Duration.ofSeconds(2)).giveUpAfter(Duration.ofSeconds(30)).toString());
        assertEquals("RetryPolicy[no attempt limit, every 30s]", RetryPolicy.forever(Duration.ofSeconds(30)).toString());
    }
}
