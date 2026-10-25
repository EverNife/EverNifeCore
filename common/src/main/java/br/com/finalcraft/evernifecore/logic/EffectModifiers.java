package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
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
 * {@code target: self|group|region-players|world-players} and {@code scope: permanent|held}. Immutable.
 */
public final class EffectModifiers {

    /** Every key {@link Effects#registry()} reserves for the executor. */
    public static final List<String> KEYS = Arrays.asList("delay", "chance", "if", "repeat", "every", "as", "target", "scope", "to");

    /** The attribute under which an effect that {@link Effect#acceptsSender() accepts a sender} reads {@code as}. */
    public static final String SENDER_ATTRIBUTE = "evernifecore:as";

    public static final List<String> TARGETS = Arrays.asList("self", "group", "region-players", "world-players");

    private static final EffectModifiers NONE = new EffectModifiers(null, null, null, 1, null, null, "self", EffectScope.PERMANENT);

    private final @Nullable Duration delay;
    private final @Nullable BigDecimal chance;
    private final @Nullable Condition condition;
    private final int repeat;
    private final @Nullable Duration every;
    private final @Nullable String sender;
    private final String target;
    private final EffectScope scope;

    private EffectModifiers(@Nullable Duration delay, @Nullable BigDecimal chance, @Nullable Condition condition, int repeat,
                            @Nullable Duration every, @Nullable String sender, String target, EffectScope scope) {
        this.delay = delay;
        this.chance = chance;
        this.condition = condition;
        this.repeat = repeat;
        this.every = every;
        this.sender = sender;
        this.target = target;
        this.scope = scope;
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
        if (node.has("to")) {
            throw new TypeRegistryException("The effect '" + type + "' writes to: " + node.get("to") + ", but to: needs a delivery"
                    + " destination, which no plugin registered yet. Remove it, or install the plugin that delivers.");
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
        String sender = choice(type, node, "as", Arrays.asList("player", "console"));
        String target = choice(type, node, "target", TARGETS);
        String scopeText = choice(type, node, "scope", Arrays.asList("permanent", "held"));
        EffectScope scope = "held".equals(scopeText) ? EffectScope.HELD : EffectScope.PERMANENT;
        if (delay == null && chance == null && condition == null && repeat == 1 && sender == null && target == null && scope == EffectScope.PERMANENT) {
            return NONE;
        }
        return new EffectModifiers(delay, chance, condition, repeat, every, sender, target == null ? "self" : target, scope);
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
}
