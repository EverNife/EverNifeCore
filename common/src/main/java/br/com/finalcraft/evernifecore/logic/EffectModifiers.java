package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What the executor does around an effect, written as keys of the effect's own node and never read by its type:
 * {@code delay}, {@code chance}, {@code if}, {@code repeat} + {@code every}, {@code as: player|console},
 * {@code target: self|group|region-players|world-players|online-players}, {@code scope: permanent|held} and
 * {@code offline: run|skip|on-login}. Immutable.
 */
public final class EffectModifiers {

    /** Every key {@link Effects#registry()} reserves for the executor. */
    public static final List<String> KEYS = Arrays.asList("delay", "chance", "if", "repeat", "every", "as", "target", "scope", "to", "offline");

    /** The attribute under which an effect that {@link Effect#acceptsSender() accepts a sender} reads {@code as}. */
    public static final String SENDER_ATTRIBUTE = "evernifecore:as";

    /**
     * The attribute under which an effect that {@link Effect#acceptsDestination() accepts a destination} reads
     * {@code to}: a delivery destination id, resolved when the effect applies.
     */
    public static final String DESTINATION_ATTRIBUTE = "evernifecore:to";

    public static final List<String> TARGETS = Arrays.asList("self", "group", "region-players", "world-players", "online-players");
    private static final List<String> SENDERS = Arrays.asList("player", "console");
    private static final List<String> SCOPES = Arrays.asList("permanent", "held");
    private static final List<String> OFFLINE = Arrays.asList("run", "skip", "on-login");

    private static final EffectModifiers NONE = new EffectModifiers(null, null, null, 1, null, null, "self", EffectScope.PERMANENT, null, null);

    private final @Nullable Duration delay;
    private final @Nullable BigDecimal chance;
    private final @Nullable Condition condition;
    private final int repeat;
    private final @Nullable Duration every;
    private final @Nullable String sender;
    private final String target;
    private final EffectScope scope;
    private final @Nullable String destination;
    private final @Nullable String offline;

    private EffectModifiers(@Nullable Duration delay, @Nullable BigDecimal chance, @Nullable Condition condition, int repeat,
                            @Nullable Duration every, @Nullable String sender, String target, EffectScope scope,
                            @Nullable String destination, @Nullable String offline) {
        this.delay = delay;
        this.chance = chance;
        this.condition = condition;
        this.repeat = repeat;
        this.every = every;
        this.sender = sender;
        this.target = target;
        this.scope = scope;
        this.destination = destination;
        this.offline = offline;
    }

    /**
     * Each of the {@link #KEYS} as a parameter - kind, default, accepted values, one sentence - for what describes the
     * effects to an editor. Built on each call: it names the condition registry.
     */
    public static List<ParamSpec> getParams() {
        return Arrays.asList(
                ParamSpec.optional("delay", ParamSpec.Kind.DURATION, null, "Waits this long before applying the effect."),
                ParamSpec.optional("chance", ParamSpec.Kind.NUMBER, null, "From 0 (never) to 1 (always): how likely the effect applies."),
                ParamSpec.optional("if", ParamSpec.Kind.NODE, null, "A condition that must pass for the effect to apply.").nested(Conditions.registry()),
                ParamSpec.optional("repeat", ParamSpec.Kind.NUMBER, 1, "How many times the effect runs; more than 1 needs every."),
                ParamSpec.optional("every", ParamSpec.Kind.DURATION, null, "The time between the runs of repeat."),
                ParamSpec.optional("as", ParamSpec.Kind.STRING, null, "Who runs it, for an effect that takes a sender (command).").oneOf(SENDERS.toArray(new String[0])),
                ParamSpec.optional("target", ParamSpec.Kind.STRING, "self", "Who receives it.").oneOf(TARGETS.toArray(new String[0])),
                ParamSpec.optional("scope", ParamSpec.Kind.STRING, "permanent", "held lasts only while the condition that gave it holds.").oneOf(SCOPES.toArray(new String[0])),
                ParamSpec.optional("to", ParamSpec.Kind.ID, null, "The delivery destination, for an effect that takes one, e.g. pending."),
                ParamSpec.optional("offline", ParamSpec.Kind.STRING, "run", "With the player offline: apply now, skip it, or wait for the"
                        + " next login; a command run as: player waits by default.").oneOf(OFFLINE.toArray(new String[0])));
    }

    public static EffectModifiers none() {
        return NONE;
    }

    /**
     * Reads the modifier keys of {@code node}, an effect definition of type {@code type}.
     *
     * @throws TypeRegistryException naming the key, the value and what it expects
     */
    static EffectModifiers parse(ECPluginData caller, String type, JsonNode node, ZoneId zone) {
        String destination = null;
        JsonNode toNode = node.get("to");
        if (toNode != null) {
            destination = toNode.isTextual() ? toNode.asText().trim().toLowerCase(Locale.ROOT) : "";
            if (destination.isEmpty() || destination.contains(" ")) {
                throw new TypeRegistryException("The effect '" + type + "' cannot read to = " + toNode + ": it expects a delivery"
                        + " destination id, e.g. pending, inventory or finalcolonies:warehouse.");
            }
        }
        Duration delay = duration(type, node, "delay");
        BigDecimal chance = null;
        JsonNode chanceNode = node.get("chance");
        if (chanceNode != null) {
            try {
                chance = new BigDecimal(chanceNode.asText().trim());
            } catch (NumberFormatException notANumber) {
                chance = null;
            }
            if (!chanceNode.isValueNode() || chance == null || chance.signum() < 0 || chance.compareTo(BigDecimal.ONE) > 0) {
                throw new TypeRegistryException("The effect '" + type + "' cannot read chance = " + chanceNode + ": it expects a number"
                        + " from 0 (never) to 1 (always), e.g. 0.25.");
            }
        }
        JsonNode ifNode = node.get("if");
        Condition condition = ifNode == null ? null : Conditions.parse(caller, ifNode, zone);
        int repeat = 1;
        JsonNode repeatNode = node.get("repeat");
        if (repeatNode != null) {
            if (!repeatNode.canConvertToInt() || repeatNode.asInt() < 1) {
                throw new TypeRegistryException("The effect '" + type + "' cannot read repeat = " + repeatNode + ": it expects how many"
                        + " times it runs, 1 or more, with every: <duration> between them, e.g. repeat: 3, every: 10s.");
            }
            repeat = repeatNode.asInt();
        }
        Duration every = duration(type, node, "every");
        if (repeat > 1 && (every == null || every.isZero())) {
            throw new TypeRegistryException("The effect '" + type + "' repeats " + repeat + " times but says no every: write how long"
                    + " between runs, e.g. repeat: " + repeat + ", every: 10s.");
        }
        String sender = choice(type, node, "as", SENDERS);
        String target = choice(type, node, "target", TARGETS);
        String scopeText = choice(type, node, "scope", SCOPES);
        EffectScope scope = "held".equals(scopeText) ? EffectScope.HELD : EffectScope.PERMANENT;
        String offline = choice(type, node, "offline", OFFLINE);
        if ("run".equals(offline) && "player".equals(sender)) {
            throw new TypeRegistryException("The effect '" + type + "' writes offline: run, which applies it now, and as: player, which"
                    + " needs the player online to run as. Write offline: on-login to wait for the next login, or as: console to run it now.");
        }
        if (delay == null && chance == null && condition == null && repeat == 1 && sender == null && target == null && scope == EffectScope.PERMANENT
                && destination == null && offline == null) {
            return NONE;
        }
        return new EffectModifiers(delay, chance, condition, repeat, every, sender, target == null ? "self" : target, scope, destination,
                offline);
    }

    private static @Nullable Duration duration(String type, JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null) {
            return null;
        }
        try {
            if (!value.isValueNode()) {
                throw new IllegalArgumentException();
            }
            return Duration.ofMillis(FCTimeFrame.fromConfigString(value.asText()).getMillis());
        } catch (IllegalArgumentException notADuration) {
            throw new TypeRegistryException("The effect '" + type + "' cannot read " + key + " = " + value + ": it expects a duration,"
                    + " e.g. 30s, 10m or 1h30m. " + FCTimeFrame.ACCEPTED_FORMAT);
        }
    }

    private static @Nullable String choice(String type, JsonNode node, String key, List<String> accepted) {
        JsonNode value = node.get(key);
        if (value == null) {
            return null;
        }
        String text = value.asText().trim().toLowerCase(Locale.ROOT);
        if (!value.isTextual() || !accepted.contains(text)) {
            throw new TypeRegistryException("The effect '" + type + "' cannot read " + key + " = " + value + ": it expects one of "
                    + String.join(", ", accepted) + ".");
        }
        return text;
    }

    public boolean isNone() {
        return this == NONE;
    }

    public @Nullable Duration getDelay() {
        return delay;
    }

    /** From 0 to 1; null means always. */
    public @Nullable BigDecimal getChance() {
        return chance;
    }

    /** The {@code if} condition; null when there is none. */
    public @Nullable Condition getCondition() {
        return condition;
    }

    public int getRepeat() {
        return repeat;
    }

    public @Nullable Duration getEvery() {
        return every;
    }

    /** {@code player} or {@code console}; null when not written. */
    public @Nullable String getSender() {
        return sender;
    }

    /** One of {@link #TARGETS}; {@code self} by default. */
    public String getTarget() {
        return target;
    }

    public EffectScope getScope() {
        return scope;
    }

    /** The {@code to} delivery destination id; null when not written. */
    public @Nullable String getDestination() {
        return destination;
    }

    /**
     * What the effect does for a player owner with no player online: {@code run} applies it anyway, {@code skip} audits
     * {@code EFFECT_SKIPPED}, {@code on-login} keeps it as a delivery for the next login. Null when not written: the
     * effect applies as it would, and a {@link Effect#decidesAbsence deciding} one (a command) picks its own default.
     */
    public @Nullable String getOffline() {
        return offline;
    }
}
