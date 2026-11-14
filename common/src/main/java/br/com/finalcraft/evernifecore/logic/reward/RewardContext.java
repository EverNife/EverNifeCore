package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.booster.BoosterDomain;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What one payment knows that the core cannot: who pays ({@link Caller}), whose reward it is ({@link ProgressOwner}),
 * why now ({@link RewardTrigger}), what each member contributed and whose action closed it - all measured by the caller
 * (a quest engine from its objectives, a plugin from its own book) - or, with a {@link RewardScene}, who took part and what
 * the texts read. Immutable; any thread.
 */
public final class RewardContext {

    private final Caller caller;
    private final ProgressOwner owner;
    private final RewardTrigger trigger;
    private final Map<UUID, BigDecimal> contributions;
    private final @Nullable UUID lastActor;
    private final Map<String, Object> attributes;
    private final @Nullable RewardScene scene;
    private final List<BoosterDomain> boostedBy;

    private RewardContext(Builder builder) {
        this.caller = builder.caller;
        this.owner = builder.owner;
        this.trigger = builder.trigger;
        if (builder.scene != null && !builder.contributions.isEmpty()) {
            throw new IllegalArgumentException("The reward of " + builder.caller + " has a scene and contributions: the scene carries each"
                    + " participant's contribution - drop .contributions(...).");
        }
        this.scene = builder.scene;
        this.contributions = Collections.unmodifiableMap(new LinkedHashMap<>(scene != null ? scene.getContributions() : builder.contributions));
        this.lastActor = builder.lastActor;
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
        this.boostedBy = Collections.unmodifiableList(new ArrayList<>(builder.boostedBy));
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

    /** Every attribute; each payment's {@code ExecutionContext} carries them too. Read-only. */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /** Who took part, when the caller described the payment as a scene: its participants are then the candidates. */
    public @Nullable RewardScene getScene() {
        return scene;
    }

    /** The domains whose boosters multiply this payment's amounts; empty = nothing multiplies. Read-only. */
    public List<BoosterDomain> getBoostedBy() {
        return boostedBy;
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
        private @Nullable RewardScene scene;
        private final List<BoosterDomain> boostedBy = new ArrayList<>();

        private Builder(Caller caller, ProgressOwner owner) {
            this.caller = Objects.requireNonNull(caller, "caller");
            this.owner = Objects.requireNonNull(owner, "owner");
        }

        /** Replaces the contributions; a negative one is refused. With a {@link #scene} the scene's are the contributions. */
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

        /**
         * Pays the scene's participants instead of the owner's members: they are the candidates, their contributions the
         * contributions, and every payment reads the scene's variables and the participant's name as {@code {player}}.
         */
        public Builder scene(@Nullable RewardScene scene) {
            this.scene = scene;
            return this;
        }

        /**
         * Each member's {@code {amount}} is its {@code {base-amount}} times its multiplier of each of {@code domains} (their
         * product), resolved once per member and step when the receipt is settled and frozen there: the {@code money}
         * effect pays that number, a pending delivery keeps it. Without it nothing multiplies.
         */
        public Builder boostedBy(BoosterDomain... domains) {
            for (BoosterDomain domain : domains) {
                boostedBy.add(Objects.requireNonNull(domain, "domain"));
            }
            return this;
        }

        public RewardContext build() {
            return new RewardContext(this);
        }
    }
}
