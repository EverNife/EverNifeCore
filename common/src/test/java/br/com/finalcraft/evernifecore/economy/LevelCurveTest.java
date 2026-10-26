package br.com.finalcraft.evernifecore.economy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Experience to level, on the exact boundaries where a rounding slip would show. */
class LevelCurveTest {

    private static final BigDecimal TINY = new BigDecimal("0.0001");

    @Test
    void aLinearCurveNeedsTheSameExperienceForEveryLevel() {
        LevelCurve curve = LevelCurve.linear(new BigDecimal("1000"));

        assertEquals(0, curve.levelOf(new BigDecimal("999")));
        assertEquals(1, curve.levelOf(new BigDecimal("1000")));
        assertEquals(7, curve.levelOf(new BigDecimal("7999.99")));
        assertEquals(0, curve.expFor(3).compareTo(new BigDecimal("3000")));
        assertEquals(Integer.MAX_VALUE, curve.getMaxLevel());
    }

    @Test
    void aQuadraticCurveReachesEachLevelExactlyAtItsTotalAndNotATickBefore() {
        LevelCurve curve = LevelCurve.quadratic(new BigDecimal("2.5"), new BigDecimal("40"));

        assertEquals(0, curve.expFor(10).compareTo(new BigDecimal("650")), "2.5*100 + 40*10");
        assertExactBoundaries(curve);
        //coefficients a double cannot hold: the estimate lands beside the boundary and only the exact check settles it
        assertExactBoundaries(LevelCurve.quadratic(new BigDecimal("0.333333333333333333333"), new BigDecimal("0.1")));
        assertExactBoundaries(LevelCurve.linear(new BigDecimal("0.1")));
    }

    private static void assertExactBoundaries(LevelCurve curve) {
        for (int level = 1; level <= 2000; level++) {
            BigDecimal total = curve.expFor(level);
            assertEquals(level, curve.levelOf(total), curve + " at exactly the total of " + level);
            assertEquals(level - 1, curve.levelOf(total.subtract(TINY.min(total.subtract(curve.expFor(level - 1)).divide(BigDecimal.TEN)))),
                    curve + " just below the total of " + level);
        }
    }

    @Test
    void aTableCurveStopsAtItsLastTotal() {
        LevelCurve curve = LevelCurve.table(Arrays.asList(new BigDecimal("100"), new BigDecimal("300"), new BigDecimal("700")));

        assertEquals(0, curve.levelOf(new BigDecimal("99")));
        assertEquals(2, curve.levelOf(new BigDecimal("300")));
        assertEquals(3, curve.levelOf(new BigDecimal("1000000")));
        assertEquals(3, curve.getMaxLevel());
        assertEquals(0, curve.progressWithin(new BigDecimal("5000")).compareTo(BigDecimal.ONE), "the top level is full");
        assertThrows(IllegalArgumentException.class, () -> curve.expFor(4));
    }

    @Test
    void progressIsTheShareOfTheWayToTheNextLevel() {
        LevelCurve curve = LevelCurve.table(Arrays.asList(new BigDecimal("100"), new BigDecimal("300")));

        assertEquals(0, curve.progressWithin(new BigDecimal("200")).compareTo(new BigDecimal("0.5")));
        assertEquals(0, curve.progressWithin(new BigDecimal("50")).compareTo(new BigDecimal("0.5")));
        assertEquals(0, curve.progressWithin(new BigDecimal("-10")).signum(), "negative experience is the start of level 0");
    }

    @Test
    void negativeExperienceIsLevelZero() {
        assertEquals(0, LevelCurve.quadratic(BigDecimal.ONE, BigDecimal.ONE).levelOf(new BigDecimal("-50")));
        assertEquals(0, LevelCurve.linear(BigDecimal.TEN).levelOf(new BigDecimal("-50")));
    }

    @Test
    void aCurveThatCannotGrowIsRefusedSayingWhatItNeeds() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LevelCurve.linear(BigDecimal.ZERO)).getMessage().contains("positive"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LevelCurve.quadratic(BigDecimal.ZERO, BigDecimal.ZERO))
                .getMessage().contains("not both zero"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LevelCurve.table(Arrays.asList(new BigDecimal("300"), new BigDecimal("100"))))
                .getMessage().contains("increasing order"));
    }
}
