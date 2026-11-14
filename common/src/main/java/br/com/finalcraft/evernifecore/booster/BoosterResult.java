package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.fancytext.FancyText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/** The multiplier of one {@link BoosterDomain} for one context, the boosters it came from and the join that made it. Immutable. */
public final class BoosterResult {

    private final BoosterDomain domain;
    private final BigDecimal multiplier;
    private final List<Booster> parcels;
    private final BoosterJoin join;

    BoosterResult(BoosterDomain domain, BigDecimal multiplier, List<Booster> parcels, BoosterJoin join) {
        this.domain = domain;
        this.multiplier = multiplier;
        this.parcels = Collections.unmodifiableList(parcels);
        this.join = join;
    }

    public BoosterDomain getDomain() {
        return domain;
    }

    /** {@code ONE} when no booster counts. */
    public BigDecimal getMultiplier() {
        return multiplier;
    }

    /** {@code amount} times the multiplier: a sum of money, points. */
    public BigDecimal apply(BigDecimal amount) {
        return amount.multiply(multiplier);
    }

    /**
     * {@code base} items, kills, planks times the multiplier, made whole by {@code rounding}; {@link CountRounding#CHANCE}
     * draws from {@link ThreadLocalRandom}.
     *
     * @throws IllegalArgumentException when {@code base} is negative
     * @throws ArithmeticException      when the result does not fit an {@code int}
     */
    public int applyToCount(int base, CountRounding rounding) {
        return applyToCount(base, rounding, () -> ThreadLocalRandom.current().nextDouble());
    }

    /**
     * {@link #applyToCount(int, CountRounding)} drawing {@link CountRounding#CHANCE}'s luck from {@code random}, a number
     * in {@code [0, 1)}: the extra one comes when it is below the fraction.
     */
    public int applyToCount(int base, CountRounding rounding, DoubleSupplier random) {
        if (base < 0) {
            throw new IllegalArgumentException("A boosted count starts from 0 or more, not " + base + ": boost what is gained,"
                    + " and take what is lost without a booster.");
        }
        BigDecimal boosted = multiplier.multiply(BigDecimal.valueOf(base));
        switch (Objects.requireNonNull(rounding, "rounding")) {
            case ROUND:
                return boosted.setScale(0, RoundingMode.HALF_UP).intValueExact();
            case CHANCE:
                BigDecimal whole = boosted.setScale(0, RoundingMode.FLOOR);
                double fraction = boosted.subtract(whole).doubleValue();
                return whole.intValueExact() + (fraction > 0 && random.getAsDouble() < fraction ? 1 : 0);
            default:
                return boosted.setScale(0, RoundingMode.FLOOR).intValueExact();
        }
    }

    /** The active boosters joined, in source order. */
    public List<Booster> getParcels() {
        return parcels;
    }

    public BoosterJoin getJoin() {
        return join;
    }

    /** {@code duels:win x1.75 (sum-bonus cap 3): x1.5 from ..., +0.25 from ...}, one parcel per line. */
    public FancyText explain() {
        StringBuilder text = new StringBuilder(domain.getId()).append(" x").append(multiplier.stripTrailingZeros().toPlainString())
                .append(" (").append(join).append(")");
        if (parcels.isEmpty()) {
            text.append(": no booster");
        }
        for (Booster parcel : parcels) {
            text.append("\n - ").append(parcel);
        }
        return FancyText.of(text.toString());
    }

    @Override
    public String toString() {
        return explain().getText();
    }
}
