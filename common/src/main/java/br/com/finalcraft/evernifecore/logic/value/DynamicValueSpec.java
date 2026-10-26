package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.everylibs.util.numberwrapper.NumericSpec;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * The rule of a {@link DynamicValue}: where it starts ({@code base}), the bounds it never leaves, how much a bump moves
 * it ({@code step}) and how fast it drifts back to the base ({@code decay}), optionally rounded by a
 * {@link NumericSpec}. Immutable; any thread. In a config: {@code {base: 100, min: 40, max: 400, step: 5,
 * decay: {amount: 10, every: 1h}}}.
 */
public final class DynamicValueSpec {

    private final BigDecimal base;
    private final @Nullable BigDecimal min;
    private final @Nullable BigDecimal max;
    private final BigDecimal step;
    private final @Nullable BigDecimal decayAmount;
    private final @Nullable Duration decayEvery;
    private final @Nullable NumericSpec numericSpec;

    private DynamicValueSpec(Builder builder) {
        this.base = builder.base;
        this.min = builder.min;
        this.max = builder.max;
        this.step = builder.step;
        this.decayAmount = builder.decayAmount;
        this.decayEvery = builder.decayEvery;
        this.numericSpec = builder.numericSpec;
    }

    public static Builder base(BigDecimal base) {
        return new Builder(Objects.requireNonNull(base, "base"));
    }

    public static Builder base(double base) {
        return base(BigDecimal.valueOf(base));
    }

    public BigDecimal getBase() {
        return base;
    }

    public @Nullable BigDecimal getMin() {
        return min;
    }

    public @Nullable BigDecimal getMax() {
        return max;
    }

    /** What one {@code bump} adds; zero by default. */
    public BigDecimal getStep() {
        return step;
    }

    /** How much the value moves toward the base each {@link #getDecayEvery()}; null when it never drifts back. */
    public @Nullable BigDecimal getDecayAmount() {
        return decayAmount;
    }

    public @Nullable Duration getDecayEvery() {
        return decayEvery;
    }

    public @Nullable NumericSpec getNumericSpec() {
        return numericSpec;
    }

    /** {@code value} inside the bounds, rounded by the numeric spec when there is one. */
    public BigDecimal clamp(BigDecimal value) {
        BigDecimal clamped = value;
        if (min != null && clamped.compareTo(min) < 0) {
            clamped = min;
        }
        if (max != null && clamped.compareTo(max) > 0) {
            clamped = max;
        }
        return numericSpec == null ? clamped : numericSpec.round(clamped);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DynamicValueSpec)) {
            return false;
        }
        DynamicValueSpec that = (DynamicValueSpec) other;
        return base.compareTo(that.base) == 0 && same(min, that.min) && same(max, that.max) && step.compareTo(that.step) == 0
                && same(decayAmount, that.decayAmount) && Objects.equals(decayEvery, that.decayEvery) && Objects.equals(numericSpec, that.numericSpec);
    }

    private static boolean same(@Nullable BigDecimal a, @Nullable BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(base.stripTrailingZeros(), decayEvery);
    }

    @Override
    public String toString() {
        return "DynamicValueSpec{base=" + base.toPlainString() + ", min=" + min + ", max=" + max + ", step=" + step.toPlainString()
                + (decayEvery == null ? "" : ", decay=" + decayAmount + "/" + decayEvery) + "}";
    }

    public static final class Builder {
        private final BigDecimal base;
        private @Nullable BigDecimal min;
        private @Nullable BigDecimal max;
        private BigDecimal step = BigDecimal.ZERO;
        private @Nullable BigDecimal decayAmount;
        private @Nullable Duration decayEvery;
        private @Nullable NumericSpec numericSpec;

        private Builder(BigDecimal base) {
            this.base = base;
        }

        public Builder min(@Nullable BigDecimal min) {
            this.min = min;
            return this;
        }

        public Builder max(@Nullable BigDecimal max) {
            this.max = max;
            return this;
        }

        public Builder step(BigDecimal step) {
            this.step = Objects.requireNonNull(step, "step");
            return this;
        }

        /** Moves the value {@code amount} toward the base every {@code every}, computed on read: nothing runs between reads. */
        public Builder decay(BigDecimal amount, Duration every) {
            this.decayAmount = Objects.requireNonNull(amount, "amount");
            this.decayEvery = Objects.requireNonNull(every, "every");
            return this;
        }

        public Builder spec(@Nullable NumericSpec numericSpec) {
            this.numericSpec = numericSpec;
            return this;
        }

        /** @throws IllegalArgumentException when the base is outside the bounds, or the decay is negative or not timed */
        public DynamicValueSpec build() {
            if (min != null && max != null && min.compareTo(max) > 0) {
                throw new IllegalArgumentException("A dynamic value's min (" + min.toPlainString() + ") is above its max (" + max.toPlainString()
                        + "): swap them.");
            }
            if ((min != null && base.compareTo(min) < 0) || (max != null && base.compareTo(max) > 0)) {
                throw new IllegalArgumentException("A dynamic value's base " + base.toPlainString() + " lies outside its bounds [" + min + ", " + max
                        + "]: the value decays toward the base, so the base sits between min and max.");
            }
            if (decayEvery != null && (decayAmount.signum() < 0 || decayEvery.isZero() || decayEvery.isNegative())) {
                throw new IllegalArgumentException("A dynamic value decays a positive amount every positive time, e.g. decay: {amount: 10,"
                        + " every: 1h}; it reads " + decayAmount + " every " + decayEvery + ".");
            }
            return new DynamicValueSpec(this);
        }
    }
}
