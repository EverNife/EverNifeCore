package br.com.finalcraft.evernifecore.booster;

import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.Season;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A boost of one gain ({@link #getVariableId()}, e.g. {@code evernifecore:money}): a {@link #getFactor() factor} that
 * multiplies it or a {@link #getBonus() bonus} added to its multiplier, for an owner, optionally until an instant or
 * while a season lasts. The booster itself multiplies nothing: {@link Boosters#resolve} joins the boosters of a context
 * and the source of the gain applies the result. Immutable; any thread.
 */
public final class Booster {

    private final @Nullable UUID id;
    private final String variableId;
    private final ProgressOwner owner;
    private final @Nullable BigDecimal factor;
    private final @Nullable BigDecimal bonus;
    private final @Nullable Instant until;
    private final @Nullable Season season;
    private final @Nullable Caller origin;

    private Booster(@Nullable UUID id, String variableId, ProgressOwner owner, @Nullable BigDecimal factor, @Nullable BigDecimal bonus,
                    @Nullable Instant until, @Nullable Season season, @Nullable Caller origin) {
        this.id = id;
        this.variableId = variableId;
        this.owner = owner;
        this.factor = factor;
        this.bonus = bonus;
        this.until = until;
        this.season = season;
        this.origin = origin;
    }

    /** A booster of {@code variableId}: a variable or currency id, bare for the core's own ({@code money}, {@code exp-points}). */
    public static Builder on(String variableId) {
        return new Builder(Boosters.normalize(variableId));
    }

    /** This booster under {@code id}; what a {@link BoosterManager} hands back from {@code grant}. */
    Booster withId(UUID id) {
        return new Booster(id, variableId, owner, factor, bonus, until, season, origin);
    }

    /** Assigned by the {@link BoosterManager} that keeps it; null for a booster a stateless source derived. */
    public @Nullable UUID getId() {
        return id;
    }

    /** The full id, {@code owner:name}. */
    public String getVariableId() {
        return variableId;
    }

    /** Who it boosts: a player, a group, a region's or a world's players, or everyone ({@code global}). */
    public ProgressOwner getOwner() {
        return owner;
    }

    /** Exactly one of factor and bonus is set. */
    public @Nullable BigDecimal getFactor() {
        return factor;
    }

    public @Nullable BigDecimal getBonus() {
        return bonus;
    }

    /** {@code factor}, or {@code 1 + bonus}. */
    public BigDecimal getMultiplier() {
        return factor != null ? factor : BigDecimal.ONE.add(bonus);
    }

    /** {@code factor - 1}, or {@code bonus}. */
    public BigDecimal getBonusShare() {
        return bonus != null ? bonus : factor.subtract(BigDecimal.ONE);
    }

    /** When it stops counting; null when it lasts. */
    public @Nullable Instant getUntil() {
        return until;
    }

    /** The season it counts during; null when it is not tied to one. */
    public @Nullable Season getSeason() {
        return season;
    }

    /** Who granted it and why; null for one read from a config. */
    public @Nullable Caller getOrigin() {
        return origin;
    }

    public boolean isActiveAt(Instant now) {
        return (until == null || now.isBefore(until)) && (season == null || season.contains(now));
    }

    /** Whether it can never count again after {@code now}: its end or its season's passed. */
    public boolean isOverAt(Instant now) {
        return (until != null && !now.isBefore(until)) || (season != null && !now.isBefore(season.getTo().toInstant()));
    }

    @Override
    public String toString() {
        return variableId + (factor != null ? " x" + factor.toPlainString() : " +" + bonus.toPlainString()) + " for " + owner
                + (until != null ? " until " + until : "") + (season != null ? " during " + season.getId() : "")
                + (origin != null ? " from " + origin : "");
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Booster)) {
            return false;
        }
        Booster that = (Booster) other;
        return Objects.equals(id, that.id) && variableId.equals(that.variableId) && owner.equals(that.owner)
                && Objects.equals(factor, that.factor) && Objects.equals(bonus, that.bonus) && Objects.equals(until, that.until)
                && Objects.equals(season, that.season) && Objects.equals(origin, that.origin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, variableId, owner, factor, bonus, until, season);
    }

    public static final class Builder {
        private final String variableId;
        private @Nullable BigDecimal factor;
        private @Nullable BigDecimal bonus;
        private ProgressOwner owner = ProgressOwner.global();
        private @Nullable Duration lasting;
        private @Nullable Instant until;
        private @Nullable Season season;
        private @Nullable Caller origin;

        private Builder(String variableId) {
            this.variableId = variableId;
        }

        /** Multiplies the gain: {@code 1.5} is x1.5. Exclusive with {@link #bonus}. */
        public Builder factor(BigDecimal factor) {
            this.factor = Objects.requireNonNull(factor, "factor");
            return this;
        }

        public Builder factor(double factor) {
            return factor(BigDecimal.valueOf(factor));
        }

        /** Adds to the multiplier: {@code 0.5} is +50%. Exclusive with {@link #factor}. */
        public Builder bonus(BigDecimal bonus) {
            this.bonus = Objects.requireNonNull(bonus, "bonus");
            return this;
        }

        public Builder bonus(double bonus) {
            return bonus(BigDecimal.valueOf(bonus));
        }

        /** Default {@code global}. A region or world owner boosts whoever stands in it, a group its members. */
        public Builder forOwner(ProgressOwner owner) {
            this.owner = Objects.requireNonNull(owner, "owner");
            return this;
        }

        /** Counts until {@code ECClock.now() + duration}, fixed when the booster is built. */
        public Builder lasting(Duration duration) {
            this.lasting = Objects.requireNonNull(duration, "duration");
            this.until = null;
            return this;
        }

        /** Counts until {@code until}. */
        public Builder until(@Nullable Instant until) {
            this.until = until;
            this.lasting = null;
            return this;
        }

        /** Counts only while {@code season} lasts. */
        public Builder during(@Nullable Season season) {
            this.season = season;
            return this;
        }

        public Builder from(@Nullable Caller origin) {
            this.origin = origin;
            return this;
        }

        /** @throws IllegalArgumentException when not exactly one of factor and bonus is set, or the multiplier would be negative */
        public Booster build() {
            if ((factor == null) == (bonus == null)) {
                throw new IllegalArgumentException("The booster on " + variableId + " sets " + (factor == null ? "neither factor nor bonus"
                        : "both factor and bonus") + ": set exactly one - factor(1.25) multiplies the gain, bonus(0.5) adds to its multiplier.");
            }
            BigDecimal multiplier = factor != null ? factor : BigDecimal.ONE.add(bonus);
            if (multiplier.signum() < 0) {
                throw new IllegalArgumentException("The booster on " + variableId + " would multiply the gain by " + multiplier.toPlainString()
                        + ": a booster cannot turn a gain into a loss - a factor is at least 0, a bonus at least -1.");
            }
            Instant end = lasting != null ? ECClock.now().plus(lasting) : until;
            return new Booster(null, variableId, owner, factor, bonus, end, season, origin);
        }
    }
}
