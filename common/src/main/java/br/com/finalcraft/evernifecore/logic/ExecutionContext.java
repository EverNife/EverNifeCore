package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logic.audit.AuditTrail;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything a condition, cost or effect is evaluated against: who runs it ({@link Caller}, always), the player (null
 * in a preview, for the scheduler or a global owner), whose progress it is ({@link ProgressOwner}), the event that
 * triggered it, the variables the executor wrote for the texts and typed attributes a consumer adds. Immutable; a
 * changed copy comes from {@link #derive()}, and every copy writes to the same {@link AuditTrail}. It never switches
 * threads by itself.
 */
public final class ExecutionContext {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.:-]+)}");

    private final Caller caller;
    private final @Nullable FPlayer player;
    private final ProgressOwner owner;
    private final @Nullable ActionContext action;
    private final @Nullable Locale locale;
    private final Map<String, String> variables;
    private final Map<String, Object> attributes;
    private final AuditTrail audit;

    private ExecutionContext(Builder builder) {
        this.caller = builder.caller;
        this.player = builder.player;
        this.owner = builder.owner != null ? builder.owner
                : builder.player != null ? ProgressOwner.player(builder.player.getUniqueId()) : ProgressOwner.global();
        this.action = builder.action;
        this.locale = builder.locale;
        this.variables = Collections.unmodifiableMap(new LinkedHashMap<>(builder.variables));
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
        this.audit = builder.audit != null ? builder.audit : new AuditTrail();
    }

    /** @throws NullPointerException when {@code caller} is null - every context names who runs it */
    public static Builder builder(Caller caller) {
        return new Builder(Objects.requireNonNull(caller, "An ExecutionContext needs its Caller - the plugin and the place the"
                + " logic was written, Caller.of(plugin, \"shop/vip#slot12\") - so every audit and trace line can name it."));
    }

    /** A builder with everything this context has, the owner included; what it changes leaves this context as it is. */
    public Builder derive() {
        Builder builder = new Builder(caller);
        builder.player = player;
        builder.owner = owner;
        builder.action = action;
        builder.locale = locale;
        builder.variables.putAll(variables);
        builder.attributes.putAll(attributes);
        builder.audit = audit;
        return builder;
    }

    public Caller getCaller() {
        return caller;
    }

    public @Nullable FPlayer getPlayer() {
        return player;
    }

    public ProgressOwner getOwner() {
        return owner;
    }

    /** The event that triggered the logic; null for a click, a command or an API call. */
    public @Nullable ActionContext getAction() {
        return action;
    }

    /** The one set on the builder, else the player's chosen language, else the caller plugin's language. */
    public Locale getLocale() {
        if (locale != null) {
            return locale;
        }
        String lang = FCLocaleManager.getLangOf(player, caller.getPlugin());
        return Locale.forLanguageTag(lang.replace('_', '-'));
    }

    public AuditTrail getAudit() {
        return audit;
    }

    /** A variable the executor wrote ({@code missing}, {@code cost}); null when none has that key. */
    public @Nullable String getVariable(String key) {
        return variables.get(key);
    }

    /** The product of every registered {@link MultiplierProvider}'s factor for {@code variableId}; {@code ONE} without one. */
    public BigDecimal getMultiplier(String variableId) {
        return Multipliers.of(this, variableId);
    }

    /** The attribute under {@code key}; null when absent or of another type. */
    public <V> @Nullable V getAttribute(String key, Class<V> type) {
        Object value = attributes.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    /** @throws IllegalStateException naming the key, the type asked and the type held */
    public <V> V requireAttribute(String key, Class<V> type) {
        Object value = attributes.get(key);
        if (!type.isInstance(value)) {
            throw new IllegalStateException("The context of " + caller + " has no " + type.getSimpleName() + " attribute '" + key + "'"
                    + (value == null ? "" : " - it holds a " + value.getClass().getSimpleName()) + ". Whoever builds the context sets it"
                    + " with .attribute(\"" + key + "\", value).");
        }
        return type.cast(value);
    }

    /**
     * {@code text} with every {@code {key}} replaced - by a variable of this context, else by a registered parameterless
     * {@link Variable} read for this context ({@code {health}}, {@code {finaljobs:level}}), else by {@code {player}},
     * {@code {owner}} or {@code {caller}} - and then every {@code %placeholder%} through the core's and PlaceholderAPI's
     * replacers. A key nobody answers stays written as it was; color codes are left for the text renderer.
     */
    public String resolve(String text) {
        if (text == null) {
            return null;
        }
        String resolved = text;
        if (resolved.indexOf('{') >= 0) {
            Matcher matcher = PLACEHOLDER.matcher(resolved);
            StringBuffer out = new StringBuffer();
            while (matcher.find()) {
                String value = lookup(matcher.group(1));
                matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group()));
            }
            matcher.appendTail(out);
            resolved = out.toString();
        }
        if (resolved.indexOf('%') >= 0) {
            resolved = PAPIIntegration.parse(player, resolved);
        }
        return resolved;
    }

    private @Nullable String lookup(String key) {
        String written = variables.get(key);
        if (written != null) {
            return written;
        }
        Optional<Variable<?>> variable = Variables.find(caller.getPlugin(), key);
        if (variable.isPresent()) {
            try {
                return format(variable.get().read(this));
            } catch (RuntimeException unreadable) {
                return null; //this context lacks what the variable reads: the key stays written
            }
        }
        switch (key) {
            case "player":
                return player == null ? null : player.getName();
            case "owner":
                return owner.toString();
            case "caller":
                return caller.toString();
            default:
                return null;
        }
    }

    private static @Nullable String format(@Nullable Object value) {
        if (value instanceof BigDecimal) {
            BigDecimal number = (BigDecimal) value;
            return number.signum() == 0 ? "0" : number.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Duration) {
            return FCTimeFrame.of((Duration) value).toConfigString();
        }
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public String toString() {
        return "ExecutionContext(" + caller + ", owner " + owner + (player == null ? "" : ", player " + player.getName()) + ")";
    }

    public static final class Builder {

        private final Caller caller;
        private @Nullable FPlayer player;
        private @Nullable ProgressOwner owner;
        private @Nullable ActionContext action;
        private @Nullable Locale locale;
        private final Map<String, String> variables = new LinkedHashMap<>();
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private @Nullable AuditTrail audit;

        private Builder(Caller caller) {
            this.caller = caller;
        }

        /** Null for a preview, the scheduler or a global owner. */
        public Builder player(@Nullable FPlayer player) {
            this.player = player;
            return this;
        }

        /** Default: {@code player(uuid)} when a player is set, {@code global()} otherwise. */
        public Builder owner(@Nullable ProgressOwner owner) {
            this.owner = owner;
            return this;
        }

        public Builder action(@Nullable ActionContext action) {
            this.action = action;
            return this;
        }

        /** Default: the player's chosen language, else the caller plugin's. */
        public Builder locale(@Nullable Locale locale) {
            this.locale = locale;
            return this;
        }

        /** {@code {key}} in a resolved text reads {@code value}. */
        public Builder variable(String key, String value) {
            variables.put(key, value);
            return this;
        }

        /** A typed extension read with {@link ExecutionContext#getAttribute}; null removes it. */
        public Builder attribute(String key, @Nullable Object value) {
            if (value == null) {
                attributes.remove(key);
            } else {
                attributes.put(key, value);
            }
            return this;
        }

        public ExecutionContext build() {
            return new ExecutionContext(this);
        }
    }
}
