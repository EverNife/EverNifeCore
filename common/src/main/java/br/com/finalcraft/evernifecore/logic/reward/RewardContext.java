package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What one payment knows that the core cannot: who pays ({@link Caller}), whose reward it is ({@link ProgressOwner}),
 * why now ({@link RewardTrigger}), what each member contributed and whose action closed it - all measured by the caller
 * (a quest engine from its objectives, a plugin from its own book). Immutable; any thread.
 */
public final class RewardContext {

    private final Caller caller;
    private final ProgressOwner owner;
    private final RewardTrigger trigger;
    private final Map<UUID, BigDecimal> contributions;
    private final @Nullable UUID lastActor;
    private final Map<String, Object> attributes;

    private RewardContext(Builder builder) {
        this.caller = builder.caller;
        this.owner = builder.owner;
        this.trigger = builder.trigger;
        this.contributions = Collections.unmodifiableMap(new LinkedHashMap<>(builder.contributions));
        this.lastActor = builder.lastActor;
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
    }

    public static Builder builder(Caller caller, ProgressOwner owner) {
        return new Builder(caller, owner);
    }

    public Caller getCaller() {
        return caller;
    }

    public ProgressOwner getOwner() {
        return owner;
    }

    public RewardTrigger getTrigger() {
        return trigger;
    }

    /** What the caller measured per member, in its order; empty when it measured nothing. Read-only. */
    public Map<UUID, BigDecimal> getContributions() {
        return contributions;
    }

    /** {@code member}'s contribution; ZERO when it has none. */
    public BigDecimal getContribution(UUID member) {
        BigDecimal contribution = contributions.get(member);
        return contribution == null ? BigDecimal.ZERO : contribution;
    }

    /** Whose action closed it; null when the caller named nobody. */
    public @Nullable UUID getLastActor() {
        return lastActor;
    }

    /** What the caller put under {@code key}, for a piece of its own to read; null when absent. */
    public @Nullable Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public String toString() {
        return "RewardContext{" + caller + " for " + owner + " at " + trigger + "}";
    }

    public static final class Builder {
        private final Caller caller;
        private final ProgressOwner owner;
        private RewardTrigger trigger = RewardTrigger.ON_COMPLETE;
        private final Map<UUID, BigDecimal> contributions = new LinkedHashMap<>();
        private @Nullable UUID lastActor;
        private final Map<String, Object> attributes = new HashMap<>();

        private Builder(Caller caller, ProgressOwner owner) {
            this.caller = Objects.requireNonNull(caller, "caller");
            this.owner = Objects.requireNonNull(owner, "owner");
        }

        /** Replaces the contributions; a negative one is refused. */
        public Builder contributions(Map<UUID, BigDecimal> byMember) {
            contributions.clear();
            for (Map.Entry<UUID, BigDecimal> entry : byMember.entrySet()) {
                if (entry.getValue().signum() < 0) {
                    throw new IllegalArgumentException("The contribution of " + entry.getKey() + " is " + entry.getValue()
                            + ": a contribution is zero or more - leave out who took part without contributing.");
                }
                contributions.put(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public Builder lastActor(@Nullable UUID member) {
            this.lastActor = member;
            return this;
        }

        /** Default {@link RewardTrigger#ON_COMPLETE}. */
        public Builder trigger(RewardTrigger trigger) {
            this.trigger = Objects.requireNonNull(trigger, "trigger");
            return this;
        }

        public Builder attribute(String key, Object value) {
            attributes.put(key, value);
            return this;
        }

        public RewardContext build() {
            return new RewardContext(this);
        }
    }
}
