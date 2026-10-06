package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.region.RegionTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Internal: the local flag values of one region or group. Plugins go through {@code Region}'s flag methods; the
 * store is what those methods and {@link FlagRegistry} share.
 *
 * <p>A temporary value is a layer over the permanent one: setting a flag for a while keeps the permanent value
 * underneath, and it counts again once the temporary one expires. Values of flags whose owner is not registered
 * stay raw, exactly as stored, until the flag registers.</p>
 *
 * <p>The raw form of a value is a JSON object: {@code value}, plus {@code expiresAt} (ISO-8601) when temporary,
 * plus {@code permanent} when a temporary value sits over a permanent one.</p>
 */
public final class FlagStore {

    /** Every mutation of the store goes through it, after the fact. */
    @FunctionalInterface
    public interface ChangeHook {
        void onFlagChanged(RegionFlag<?> flag, @Nullable FlagValue<?> oldValue, @Nullable FlagValue<?> newValue, FlagChangeCause cause);
    }

    private static final class Layers {
        final RegionFlag<?> flag;
        final @Nullable FlagValue<?> permanent;
        final @Nullable FlagValue<?> timed;

        Layers(RegionFlag<?> flag, @Nullable FlagValue<?> permanent, @Nullable FlagValue<?> timed) {
            this.flag = flag;
            this.permanent = permanent;
            this.timed = timed;
        }

        @Nullable FlagValue<?> live() {
            return timed != null && !timed.isExpired() ? timed : permanent;
        }
    }

    private final ConcurrentHashMap<FlagKey, Layers> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JsonNode> unknown = new ConcurrentHashMap<>();
    private final ChangeHook hook;

    public FlagStore(ChangeHook hook) {
        this.hook = Objects.requireNonNull(hook, "hook");
    }

    /** The live value: the temporary layer while it lasts, then the permanent one; null when neither. */
    @SuppressWarnings("unchecked")
    public <T> @Nullable FlagValue<T> getValue(RegionFlag<T> flag) {
        Layers layers = values.get(flag.getKey());
        return layers == null ? null : (FlagValue<T>) layers.live();
    }

    /** A read-only snapshot of every live value. */
    public Map<FlagKey, FlagValue<?>> getFlags() {
        Map<FlagKey, FlagValue<?>> live = new LinkedHashMap<>();
        values.forEach((key, layers) -> {
            FlagValue<?> value = layers.live();
            if (value != null) {
                live.put(key, value);
            }
        });
        return live.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(live);
    }

    /** Sets the permanent value; a temporary layer over it is dropped - what was set is what reads. */
    public <T> void set(RegionFlag<T> flag, T value) {
        FlagValue<T> permanent = FlagValue.permanent(value);
        mutate(flag, previous -> new Layers(flag, permanent, null), FlagChangeCause.SET);
    }

    /** Sets a temporary value over the permanent one, until now + {@code ttl}. */
    public <T> void set(RegionFlag<T> flag, T value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("A temporary value of flag " + flag.getKey() + " needs a positive duration, not " + ttl
                    + ": use setFlag(flag, value) for a permanent value.");
        }
        FlagValue<T> timed = FlagValue.until(value, RegionTime.now().plus(ttl));
        mutate(flag, previous -> new Layers(flag, previous == null ? null : previous.permanent, timed), FlagChangeCause.SET);
    }

    /** Removes both layers; false when the flag had no value here. */
    public boolean remove(RegionFlag<?> flag) {
        Layers removed = values.remove(flag.getKey());
        if (removed == null) {
            return false;
        }
        FlagValue<?> oldValue = removed.live();
        hook.onFlagChanged(flag, oldValue, null, FlagChangeCause.REMOVED);
        return true;
    }

    /** A read-only view of the raw values of flags that are not registered, by {@code owner:name}. */
    public Map<String, JsonNode> getUnknown() {
        return Collections.unmodifiableMap(unknown);
    }

    /**
     * Takes a stored value in its raw form: linked at once when its flag is registered and reads it, kept raw
     * otherwise. Loading is not a change, so the hook does not hear it.
     */
    public void putRaw(String key, JsonNode raw) {
        RegionFlag<?> flag = FlagRegistry.get().find(key).orElse(null);
        if (flag == null || !link(flag, raw)) {
            unknown.put(key.toLowerCase(Locale.ROOT), raw);
        }
    }

    /**
     * Every stored value in its raw form, by {@code owner:name}: the live ones of registered flags and the kept
     * ones of flags that are not registered - what storage writes.
     */
    public Map<String, JsonNode> toRaw() {
        Map<String, JsonNode> raw = new LinkedHashMap<>(unknown);
        values.forEach((key, layers) -> {
            JsonNode node = toRaw(layers);
            if (node != null) {
                raw.put(key.toString(), node);
            }
        });
        return raw;
    }

    /** The earliest deadline of a temporary value, expired ones included; null when none is temporary. */
    public @Nullable Instant getNextDeadline() {
        Instant next = null;
        for (Layers layers : values.values()) {
            Instant deadline = layers.timed == null ? null : layers.timed.getExpiresAt();
            if (deadline != null && (next == null || deadline.isBefore(next))) {
                next = deadline;
            }
        }
        return next;
    }

    /**
     * Drops every temporary value whose deadline is not after {@code now}; the permanent value underneath, if any,
     * reads again. Each drop is heard as {@link FlagChangeCause#EXPIRED}.
     */
    public void expire(Instant now) {
        for (FlagKey key : values.keySet()) {
            Layers[] dropped = new Layers[1];
            Layers kept = values.computeIfPresent(key, (k, layers) -> {
                Instant deadline = layers.timed == null ? null : layers.timed.getExpiresAt();
                if (deadline == null || now.isBefore(deadline)) {
                    return layers;
                }
                dropped[0] = layers;
                return layers.permanent == null ? null : new Layers(layers.flag, layers.permanent, null);
            });
            if (dropped[0] != null) {
                hook.onFlagChanged(dropped[0].flag, dropped[0].timed, kept == null ? null : kept.permanent, FlagChangeCause.EXPIRED);
            }
        }
    }

    /** Moves the raw value of {@code flag} into the store; false when there was one and it did not read. */
    boolean relink(RegionFlag<?> flag) {
        String key = flag.getKey().toString();
        JsonNode raw = unknown.get(key);
        if (raw == null) {
            return true;
        }
        if (!link(flag, raw)) {
            return false;
        }
        unknown.remove(key, raw);
        return true;
    }

    /** Turns the value of {@code flag} back into its raw form, keeping it for when the flag returns. */
    void unlink(RegionFlag<?> flag) {
        Layers layers = values.remove(flag.getKey());
        if (layers != null) {
            JsonNode raw = toRaw(layers);
            if (raw != null) {
                unknown.put(flag.getKey().toString(), raw);
            }
        }
    }

    private boolean link(RegionFlag<?> flag, JsonNode raw) {
        try {
            values.put(flag.getKey(), fromRaw(flag, raw));
            return true;
        } catch (FlagParseException unreadable) {
            return false;
        }
    }

    private <T> void mutate(RegionFlag<T> flag, UnaryOperator<Layers> next, FlagChangeCause cause) {
        if (FlagRegistry.get().get(flag.getKey()) != flag) {
            throw new IllegalStateException("Flag " + flag.getKey() + " is not registered: register it first with"
                    + " FlagRegistry.get().register(flag), usually in the owner's enable.");
        }
        FlagValue<?>[] old = new FlagValue<?>[1];
        Layers updated = values.compute(flag.getKey(), (key, previous) -> {
            old[0] = previous == null ? null : previous.live();
            return next.apply(previous);
        });
        hook.onFlagChanged(flag, old[0], updated.live(), cause);
    }

    private static @Nullable JsonNode toRaw(Layers layers) {
        FlagValue<?> top = layers.live();
        if (top == null) {
            return null;
        }
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.set("value", json(layers.flag, top.getValue()));
        if (top.getExpiresAt() != null) {
            raw.put("expiresAt", top.getExpiresAt().toString());
            if (layers.permanent != null) {
                raw.set("permanent", json(layers.flag, layers.permanent.getValue()));
            }
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private static <T> JsonNode json(RegionFlag<T> flag, Object value) {
        return flag.getType().toJson((T) value);
    }

    private static <T> Layers fromRaw(RegionFlag<T> flag, JsonNode raw) throws FlagParseException {
        if (raw == null || !raw.isObject() || !raw.has("value")) {
            throw new FlagParseException("Stored value " + raw + " of flag " + flag.getKey() + " is not {value: ..., expiresAt: ...}.");
        }
        T value = flag.getType().fromJson(raw.get("value"));
        JsonNode expiresAt = raw.get("expiresAt");
        if (expiresAt == null || expiresAt.isNull()) {
            return new Layers(flag, FlagValue.permanent(value), null);
        }
        Instant deadline;
        try {
            deadline = Instant.parse(expiresAt.asText());
        } catch (DateTimeParseException notAnInstant) {
            throw new FlagParseException("Stored deadline '" + expiresAt.asText() + "' of flag " + flag.getKey()
                    + " is not an ISO-8601 instant such as 2026-10-06T12:00:00Z.", notAnInstant);
        }
        JsonNode permanent = raw.get("permanent");
        return new Layers(flag, permanent == null || permanent.isNull() ? null : FlagValue.permanent(flag.getType().fromJson(permanent)),
                FlagValue.until(value, deadline));
    }
}
