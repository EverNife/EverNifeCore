package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.EffectList;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.reward.core.CoreRewardPieces;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One reward: who receives ({@link Eligibility}, all of them), how much each ({@link Distribution}), what becomes of the
 * offline ({@link Absence}), when it pays ({@link Moment}), the leaver/joiner rules ({@link RewardMembership}) and the
 * effects it applies, divided by each member's share - or, {@link #isOnce() once}, the effects applied one time for
 * nobody in particular. One step of a {@link RewardTable}, which pays it. Immutable; any thread.
 */
public final class RewardRule {

    private static final List<String> KEYS = Arrays.asList("eligibility", "distribution", "absent", "moment", "leaver", "joiner", "effects",
            "once", "name");

    /** What each key a {@code once: true} rule refuses would have decided: none of it applies to a rule that pays nobody. */
    private static final Map<String, String> NOT_WITH_ONCE = new LinkedHashMap<>();

    static {
        NOT_WITH_ONCE.put("eligibility", "would choose members, and once pays none");
        NOT_WITH_ONCE.put("distribution", "would share the effects among members, and once applies them whole, one time");
        NOT_WITH_ONCE.put("absent", "would decide for offline members, and once pays none");
        NOT_WITH_ONCE.put("leaver", "would decide which members count, and once pays none");
        NOT_WITH_ONCE.put("joiner", "would decide which members count, and once pays none");
        NOT_WITH_ONCE.put("membership", "would decide which members count, and once pays none");
    }

    private final List<Eligibility> eligibility;
    private final Distribution distribution;
    private final Absence absent;
    private final Moment moment;
    private final RewardMembership membership;
    private final EffectList effects;
    private final boolean once;
    private final @Nullable String name;

    private RewardRule(Builder builder) {
        this.eligibility = Collections.unmodifiableList(new ArrayList<>(builder.eligibility));
        this.distribution = builder.distribution;
        this.absent = builder.absent;
        this.moment = builder.moment;
        this.membership = builder.membership;
        this.effects = builder.effects;
        this.once = builder.once;
        this.name = builder.name;
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
     * Whether the effects apply one time, from the console, with the scene's variables and no member - even with no
     * participant at all; the eligibility, distribution, absence and leaver/joiner do not apply then.
     */
    public boolean isOnce() {
        return once;
    }

    /** The name the rule was given, written in its payment's audit line; null when none. */
    public @Nullable String getName() {
        return name;
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
     * name: bonus-alive        # optional, written in the payment's audit line
     * once: false              # true: the effects apply one time, for nobody in particular
     * }</pre>
     * A piece written as a bare name ({@code absent: deliver}) is {@code {type: deliver}}. Pieces come from
     * {@link Rewards#registry()}, effects from {@code Effects.registry()}. An effect with a {@code target} other than
     * {@code self} in a rule that is not {@code once} logs a warning on the caller's log: it applies once per member paid.
     *
     * @throws TypeRegistryException when a key is unknown, a piece is unknown or sits under another role's key, a piece
     *                               or effect cannot be read, a {@code mark} is read with no occasion, or {@code once}
     *                               comes with a key it would ignore - the message names it and the way out
     */
    public static RewardRule parse(ECPluginData caller, JsonNode node) throws TypeRegistryException {
        return read(caller, null, node, -1);
    }

    /** {@link #parse(ECPluginData, RewardOccasion, JsonNode)} for the step at {@code index} of a table, which its warnings name. */
    static RewardRule parseStep(ECPluginData caller, RewardOccasion occasion, JsonNode node, int index) throws TypeRegistryException {
        occasion.checkVariables(caller, node);
        return read(caller, occasion, node, index);
    }

    /** How a message names a step: {@code step 2 'bonus'}; without an index (-1), {@code step 'bonus'}, or {@code step}. */
    static String labelOf(int index, @Nullable String name) {
        return "The reward step" + (index < 0 ? "" : " " + index) + (name == null ? "" : " '" + name + "'");
    }

    private static RewardRule read(ECPluginData caller, @Nullable RewardOccasion occasion, JsonNode node, int index) throws TypeRegistryException {
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
        JsonNode onceNode = node.get("once");
        if (onceNode != null && !onceNode.isBoolean()) {
            throw new TypeRegistryException("A reward reads once = " + onceNode + ": write once: true to run its effects one time, or"
                    + " once: false (the default) to pay each member.");
        }
        boolean once = onceNode != null && onceNode.asBoolean();
        if (once) {
            for (String refused : NOT_WITH_ONCE.keySet()) {
                if (node.has(refused)) {
                    throw new TypeRegistryException(notWithOnce(refused));
                }
            }
            builder.once(true);
        }
        JsonNode name = node.get("name");
        if (name != null && !name.isNull()) {
            builder.name(name.asText());
        }
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
            for (Eligibility each : all) {
                checkMarks(occasion, each);
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
        RewardRule rule = builder.build();
        if (!once) {
            warnOfTargets(caller, rule, node.get("effects"), index);
        }
        return rule;
    }

    /** Why a {@code once: true} rule refuses {@code key}, and the way out. */
    private static String notWithOnce(String key) {
        return "A reward with once: true runs its effects one time, from the console, for nobody in particular, and '" + key + "' "
                + NOT_WITH_ONCE.get(key) + ". Remove '" + key + "', or drop once to pay each member.";
    }

    private static void checkMarks(@Nullable RewardOccasion occasion, Eligibility eligibility) throws TypeRegistryException {
        for (String mark : CoreRewardPieces.marksOf(eligibility)) {
            if (occasion == null) {
                throw new TypeRegistryException("The reward eligibility " + eligibility + " reads the mark '" + mark + "', and only a"
                        + " participant of a scene carries marks: read the rule with RewardRule.parse(caller, occasion, node) under the"
                        + " occasion that declares it, or choose members another way (online, top, contribution-at-least...).");
            }
            if (!occasion.getMarks().contains(mark)) {
                throw new TypeRegistryException("The reward eligibility " + eligibility + " reads the mark '" + mark + "', which the"
                        + " occasion '" + occasion.getId() + "' does not declare. It declares " + occasion.getMarks() + ": fix the mark, or"
                        + " add it with RewardOccasion.builder(...).marks(\"" + mark + "\").");
            }
        }
    }

    /** A target other than self competes with the eligibility: the effect reaches its targets once per member paid. */
    private static void warnOfTargets(ECPluginData caller, RewardRule rule, @Nullable JsonNode effects, int index) {
        if (effects == null) {
            return;
        }
        for (JsonNode effect : effects.isArray() ? effects : Collections.singletonList(effects)) {
            String target = effect.path("target").asText("self").trim().toLowerCase(Locale.ROOT);
            if (!target.equals("self")) {
                caller.getLog().warning(labelOf(index, rule.name) + " has an effect with"
                        + " target: " + target + " (" + effect + "): it applies to those targets once for every member the eligibility"
                        + " pays, so they get it as many times. Write once: true to apply it one time, or drop the target to pay each member.");
            }
        }
    }

    /**
     * {@link #parse(ECPluginData, JsonNode)} for a rule paid under {@code occasion}: every {@code {variable}} its texts
     * write must be one the occasion accepts ({@link RewardOccasion#acceptsVariable}); {@code %placeholders%} are not checked.
     *
     * @throws TypeRegistryException as {@link #parse(ECPluginData, JsonNode)} does, or naming the first variable the
     *                               occasion does not give with the ones it does
     */
    public static RewardRule parse(ECPluginData caller, RewardOccasion occasion, JsonNode node) throws TypeRegistryException {
        occasion.checkVariables(caller, node);
        return read(caller, occasion, node, -1);
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
        return "RewardRule{" + (name == null ? "" : "name=" + name + ", ") + (once ? "once, " : "") + "eligibility=" + eligibility + ", distribution=" + distribution + ", absent=" + absent + ", moment=" + moment
                + ", membership=" + membership + ", effects=" + effects + "}";
    }

    public static final class Builder {
        private List<Eligibility> eligibility = Collections.emptyList();
        private Distribution distribution = Distribution.each();
        private Absence absent = Absence.deliver();
        private Moment moment = Moment.onComplete();
        private RewardMembership membership = RewardMembership.of(true, false);
        private EffectList effects = Effects.of(Collections.<Effect>emptyList());
        private boolean once;
        private @Nullable String name;
        private final Set<String> set = new LinkedHashSet<>();

        private Builder() {
        }

        /** {@link RewardRule#isOnce()}: refused by {@link #build()} with an eligibility, distribution, absence or membership set. */
        public Builder once(boolean once) {
            this.once = once;
            return this;
        }

        public Builder name(@Nullable String name) {
            this.name = name == null || name.trim().isEmpty() ? null : name.trim();
            return this;
        }

        /** Replaces the eligibilities; none = {@code any-member}. */
        public Builder eligibility(Eligibility... all) {
            this.eligibility = Arrays.asList(all.clone());
            set.add("eligibility");
            return this;
        }

        public Builder distribution(Distribution distribution) {
            this.distribution = Objects.requireNonNull(distribution, "distribution");
            set.add("distribution");
            return this;
        }

        public Builder absent(Absence absent) {
            this.absent = Objects.requireNonNull(absent, "absent");
            set.add("absent");
            return this;
        }

        public Builder moment(Moment moment) {
            this.moment = Objects.requireNonNull(moment, "moment");
            return this;
        }

        public Builder membership(RewardMembership membership) {
            this.membership = Objects.requireNonNull(membership, "membership");
            set.add("membership");
            return this;
        }

        public Builder effects(EffectList effects) {
            this.effects = Objects.requireNonNull(effects, "effects");
            return this;
        }

        /** @throws IllegalStateException for a once rule with an eligibility, distribution, absence or membership set - it pays none of them */
        public RewardRule build() {
            if (once && !set.isEmpty()) {
                throw new IllegalStateException(notWithOnce(set.iterator().next()));
            }
            return new RewardRule(this);
        }
    }
}
