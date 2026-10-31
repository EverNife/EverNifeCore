package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.EffectList;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.time.Window;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One reward: who receives ({@link Eligibility}, all of them), how much each ({@link Distribution}), what becomes of the
 * offline ({@link Absence}), when it pays ({@link Moment}), the leaver/joiner rules ({@link RewardMembership}) and the
 * effects it applies, divided by each member's share. Paid by {@link Rewards#pay}. Immutable; any thread.
 */
public final class RewardRule {

    private static final List<String> KEYS = Arrays.asList("eligibility", "distribution", "absent", "moment", "leaver", "joiner", "effects");

    private final List<Eligibility> eligibility;
    private final Distribution distribution;
    private final Absence absent;
    private final Moment moment;
    private final RewardMembership membership;
    private final EffectList effects;

    private RewardRule(Builder builder) {
        this.eligibility = Collections.unmodifiableList(new ArrayList<>(builder.eligibility));
        this.distribution = builder.distribution;
        this.absent = builder.absent;
        this.moment = builder.moment;
        this.membership = builder.membership;
        this.effects = builder.effects;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** All must accept a member; empty = {@code any-member}. Read-only. */
    public List<Eligibility> getEligibility() {
        return eligibility;
    }

    /** Default {@code each}. */
    public Distribution getDistribution() {
        return distribution;
    }

    /** Default {@code deliver}. */
    public Absence getAbsent() {
        return absent;
    }

    /** Default {@code on-complete}. */
    public Moment getMoment() {
        return moment;
    }

    /** Default: the leaver keeps, the joiner counts from now. */
    public RewardMembership getMembership() {
        return membership;
    }

    public EffectList getEffects() {
        return effects;
    }

    /**
     * Reads the form a quest writes under {@code rewards}:
     * <pre>{@code
     * eligibility: [ { type: contribution-share, at-least: 60% } ]   # a list, or one piece
     * distribution: { type: proportional }                           # or just: proportional
     * absent: deliver
     * moment: on-complete
     * leaver: keeps            # or forfeits
     * joiner: from-now         # or inherits-zero
     * effects: [ { type: money, amount: 10000 } ]
     * }</pre>
     * A piece written as a bare name ({@code absent: deliver}) is {@code {type: deliver}}. Pieces come from
     * {@link Rewards#registry()}, effects from {@code Effects.registry()}.
     *
     * @throws TypeRegistryException when a key is unknown, a piece is unknown or sits under another role's key, or a
     *                               piece or effect cannot be read - the message names it and the way out
     */
    public static RewardRule parse(ECPluginData caller, JsonNode node) throws TypeRegistryException {
        if (node == null || !node.isObject()) {
            throw new TypeRegistryException("A reward is written as {eligibility: [...], distribution: ..., absent: ..., effects: [...]}"
                    + " - this one is " + (node == null ? "empty" : node.toString()) + ".");
        }
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!KEYS.contains(field.toLowerCase(Locale.ROOT))) {
                throw new TypeRegistryException("A reward has no '" + field + "': it reads " + KEYS + ". Move the value under one of"
                        + " them or remove it.");
            }
        }
        Builder builder = builder();
        JsonNode eligibility = node.get("eligibility");
        if (eligibility != null && !eligibility.isNull()) {
            List<Eligibility> all = new ArrayList<>();
            if (eligibility.isArray()) {
                for (JsonNode each : eligibility) {
                    all.add(piece(caller, "eligibility", each, Eligibility.class));
                }
            } else {
                all.add(piece(caller, "eligibility", eligibility, Eligibility.class));
            }
            builder.eligibility(all.toArray(new Eligibility[0]));
        }
        Distribution distribution = optionalPiece(caller, node, "distribution", Distribution.class);
        if (distribution != null) {
            builder.distribution(distribution);
        }
        Absence absent = optionalPiece(caller, node, "absent", Absence.class);
        if (absent != null) {
            builder.absent(absent);
        }
        Moment moment = optionalPiece(caller, node, "moment", Moment.class);
        if (moment != null) {
            builder.moment(moment);
        }
        if (node.has("leaver") || node.has("joiner")) {
            ObjectNode membership = JsonNodeFactory.instance.objectNode();
            membership.put(TypeRegistry.TYPE_FIELD, "membership");
            if (node.has("leaver")) {
                membership.set("leaver", node.get("leaver"));
            }
            if (node.has("joiner")) {
                membership.set("joiner", node.get("joiner"));
            }
            builder.membership(piece(caller, "leaver/joiner", membership, RewardMembership.class));
        }
        builder.effects(Effects.parseList(caller, node.get("effects"), Window.getServerZone()));
        return builder.build();
    }

    private static <P extends RewardPiece> @Nullable P optionalPiece(ECPluginData caller, JsonNode rule, String key, Class<P> role)
            throws TypeRegistryException {
        JsonNode node = rule.get(key);
        return node == null || node.isNull() ? null : piece(caller, key, node, role);
    }

    private static <P extends RewardPiece> P piece(ECPluginData caller, String key, JsonNode node, Class<P> role) throws TypeRegistryException {
        JsonNode written = node;
        if (node.isTextual()) {
            ObjectNode named = JsonNodeFactory.instance.objectNode();
            named.put(TypeRegistry.TYPE_FIELD, node.asText());
            written = named;
        }
        RewardPiece piece = Rewards.registry().parse(caller, written);
        if (!role.isInstance(piece)) {
            throw new TypeRegistryException("The reward piece " + written + " is a " + piece.getRole() + " piece, written under '" + key
                    + "': move it under the key of its role (eligibility, distribution, absent, moment, leaver/joiner).");
        }
        return role.cast(piece);
    }

    @Override
    public String toString() {
        return "RewardRule{eligibility=" + eligibility + ", distribution=" + distribution + ", absent=" + absent + ", moment=" + moment
                + ", membership=" + membership + ", effects=" + effects + "}";
    }

    public static final class Builder {
        private List<Eligibility> eligibility = Collections.emptyList();
        private Distribution distribution = Distribution.each();
        private Absence absent = Absence.deliver();
        private Moment moment = Moment.onComplete();
        private RewardMembership membership = RewardMembership.of(true, false);
        private EffectList effects = Effects.of(Collections.<Effect>emptyList());

        private Builder() {
        }

        /** Replaces the eligibilities; none = {@code any-member}. */
        public Builder eligibility(Eligibility... all) {
            this.eligibility = Arrays.asList(all.clone());
            return this;
        }

        public Builder distribution(Distribution distribution) {
            this.distribution = Objects.requireNonNull(distribution, "distribution");
            return this;
        }

        public Builder absent(Absence absent) {
            this.absent = Objects.requireNonNull(absent, "absent");
            return this;
        }

        public Builder moment(Moment moment) {
            this.moment = Objects.requireNonNull(moment, "moment");
            return this;
        }

        public Builder membership(RewardMembership membership) {
            this.membership = Objects.requireNonNull(membership, "membership");
            return this;
        }

        public Builder effects(EffectList effects) {
            this.effects = Objects.requireNonNull(effects, "effects");
            return this;
        }

        public RewardRule build() {
            return new RewardRule(this);
        }
    }
}
