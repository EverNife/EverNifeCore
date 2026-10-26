package br.com.finalcraft.evernifecore.economy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps accumulated experience to a level: a job's, a skill's, a clan's. Level 0 needs no experience; a negative
 * experience is level 0. Immutable and pure, any thread. Written in a config as
 * {@code { type: quadratic, a: 2.5, b: 40 }}, {@code { type: linear, per-level: 1000 }} or
 * {@code { type: table, totals: [100, 300, 700] }}.
 */
public interface LevelCurve {

    /** The highest level {@code exp} reaches, capped at {@link #getMaxLevel()}. */
    int levelOf(BigDecimal exp);

    /**
     * The total experience to reach {@code level}; zero for level 0 and below.
     *
     * @throws IllegalArgumentException when {@code level} is above {@link #getMaxLevel()}
     */
    BigDecimal expFor(int level);

    /** {@code Integer.MAX_VALUE} when the curve has no top. */
    int getMaxLevel();

    /** How far {@code exp} is between its level and the next, from 0 (inclusive) to 1 (exclusive); 1 at the top level. */
    default BigDecimal progressWithin(BigDecimal exp) {
        int level = levelOf(exp);
        if (level >= getMaxLevel()) {
            return BigDecimal.ONE;
        }
        BigDecimal floor = expFor(level);
        BigDecimal span = expFor(level + 1).subtract(floor);
        return exp.max(BigDecimal.ZERO).subtract(floor).divide(span, MathContext.DECIMAL64);
    }

    /** {@code expPerLevel} for each level: level n needs {@code n * expPerLevel}. */
    static LevelCurve linear(BigDecimal expPerLevel) {
        if (expPerLevel == null || expPerLevel.signum() <= 0) {
            throw new IllegalArgumentException("A linear level curve needs a positive per-level, not " + expPerLevel + ".");
        }
        return new Polynomial(BigDecimal.ZERO, expPerLevel);
    }

    /** Level n needs {@code a * n^2 + b * n}; {@code a} and {@code b} are not negative and not both zero. */
    static LevelCurve quadratic(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || a.signum() < 0 || b.signum() < 0 || (a.signum() == 0 && b.signum() == 0)) {
            throw new IllegalArgumentException("A quadratic level curve exp = a*level^2 + b*level needs a >= 0 and b >= 0, not both"
                    + " zero (got a=" + a + ", b=" + b + ").");
        }
        return new Polynomial(a, b);
    }

    /** Level n needs the n-th total; the last total is the top level. */
    static LevelCurve table(List<BigDecimal> totals) {
        return new Table(totals);
    }

    /** {@code a * n^2 + b * n}: linear when {@code a} is zero. */
    final class Polynomial implements LevelCurve {
        private final BigDecimal a;
        private final BigDecimal b;

        private Polynomial(BigDecimal a, BigDecimal b) {
            this.a = a;
            this.b = b;
        }

        public boolean isLinear() {
            return a.signum() == 0;
        }

        public BigDecimal getA() {
            return a;
        }

        public BigDecimal getB() {
            return b;
        }

        @Override
        public int levelOf(BigDecimal exp) {
            if (exp.signum() <= 0) {
                return 0;
            }
            double estimate = isLinear() ? exp.doubleValue() / b.doubleValue()
                    : (-b.doubleValue() + Math.sqrt(b.doubleValue() * b.doubleValue() + 4 * a.doubleValue() * exp.doubleValue())) / (2 * a.doubleValue());
            long level = (long) Math.min(Integer.MAX_VALUE, Math.max(0, Math.floor(estimate)));
            //the double estimate can land one off on either side: settle it on the exact values
            while (level > 0 && expFor(level).compareTo(exp) > 0) {
                level--;
            }
            while (level < Integer.MAX_VALUE && expFor(level + 1).compareTo(exp) <= 0) {
                level++;
            }
            return (int) level;
        }

        @Override
        public BigDecimal expFor(int level) {
            return expFor((long) level);
        }

        private BigDecimal expFor(long level) {
            if (level <= 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal n = BigDecimal.valueOf(level);
            return a.multiply(n).multiply(n).add(b.multiply(n));
        }

        @Override
        public int getMaxLevel() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Polynomial && ((Polynomial) other).a.compareTo(a) == 0 && ((Polynomial) other).b.compareTo(b) == 0;
        }

        @Override
        public int hashCode() {
            return 31 * a.stripTrailingZeros().hashCode() + b.stripTrailingZeros().hashCode();
        }

        @Override
        public String toString() {
            return isLinear() ? "LevelCurve.linear(" + b + ")" : "LevelCurve.quadratic(" + a + ", " + b + ")";
        }
    }

    /** One total per level, strictly increasing and positive. */
    final class Table implements LevelCurve {
        private final List<BigDecimal> totals;

        private Table(List<BigDecimal> totals) {
            if (totals == null || totals.isEmpty()) {
                throw new IllegalArgumentException("A table level curve needs at least one total: totals: [100, 300, 700].");
            }
            BigDecimal previous = BigDecimal.ZERO;
            for (int i = 0; i < totals.size(); i++) {
                BigDecimal total = totals.get(i);
                if (total == null || total.compareTo(previous) <= 0) {
                    throw new IllegalArgumentException("The totals of a table level curve grow with each level and start above 0, but"
                            + " level " + (i + 1) + " needs " + total + " after " + previous + ". Write them in increasing order.");
                }
                previous = total;
            }
            this.totals = Collections.unmodifiableList(new ArrayList<>(totals));
        }

        public List<BigDecimal> getTotals() {
            return totals;
        }

        @Override
        public int levelOf(BigDecimal exp) {
            int level = 0;
            while (level < totals.size() && totals.get(level).compareTo(exp) <= 0) {
                level++;
            }
            return level;
        }

        @Override
        public BigDecimal expFor(int level) {
            if (level <= 0) {
                return BigDecimal.ZERO;
            }
            if (level > totals.size()) {
                throw new IllegalArgumentException("This level curve stops at level " + totals.size() + "; level " + level + " has no total.");
            }
            return totals.get(level - 1);
        }

        @Override
        public int getMaxLevel() {
            return totals.size();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Table) || ((Table) other).totals.size() != totals.size()) {
                return false;
            }
            for (int i = 0; i < totals.size(); i++) {
                if (((Table) other).totals.get(i).compareTo(totals.get(i)) != 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return totals.size();
        }

        @Override
        public String toString() {
            return "LevelCurve.table(" + totals + ")";
        }
    }
}
