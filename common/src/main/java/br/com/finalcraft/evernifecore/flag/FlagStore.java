package br.com.finalcraft.evernifecore.flag;

import br.com.finalcraft.evernifecore.time.ECClock;
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
 * <p>The raw form of a value is a JSON object: {@code value} and {@code since} (ISO-8601), plus the envelope keys that
 * are not the default - {@code mode: set}, {@code priority} when not 0, {@code lock: true} - plus {@code expiresAt} when
 * temporary, plus {@code permanent} (the bare value) when a temporary value sits over a permanent one. A missing key
 * reads as its default, {@code since} as {@link Instant#EPOCH}: a value stored before the envelope existed reads the
 * same, and as the oldest. The envelope belongs to the live layer; the permanent one under a temporary value keeps
 * only its value in storage.</p>
 *
 * <p>The store refuses what its holder cannot carry ({@link FlagHolder#accepts}) and a lock on an ACTOR holder; a
 * value whose flag refuses the holder ({@link Flag#accepts(FlagHolder)}) is kept but ignored, with one warning.</p>
 */
public final class FlagStore {

    /** Every mutation of the store goes through it, after the fact. */
    @FunctionalInterface
    public interface ChangeHook {
        void onFlagChanged(Flag<?> flag, @Nullable FlagValue<?> oldValue, @Nullable FlagValue<?> newValue, FlagChangeCause cause);
    }

    private static final class Layers {
        final Flag<?> flag;
        final @Nullable FlagValue<?> permanent;
        final @Nullable FlagValue<?> timed;

        Layers(Flag<?> flag, @Nullable FlagValue<?> permanent, @Nullable FlagValue<?> timed) {
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
    private final FlagHolder holder;
    private final ChangeHook hook;

    public FlagStore(FlagHolder holder, ChangeHook hook) {
        this.holder = Objects.requireNonNull(holder, "holder");
        this.hook = Objects.requireNonNull(hook, "hook");
    }

    public FlagHolder getHolder() {
        return holder;
    }

    /** The live value: the temporary layer while it lasts, then the permanent one; null when neither. */
    @SuppressWarnings("unchecked")
    public <T> @Nullable FlagValue<T> getValue(Flag<T> flag) {
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
    public <T> void set(Flag<T> flag, T value) {
        set(flag, FlagValue.permanent(value));
    }

    /** Sets a temporary value over the permanent one, until now + {@code ttl}. */
    public <T> void set(Flag<T> flag, T value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("A temporary value of flag " + flag.getKey() + " needs a positive duration, not " + ttl
                    + ": use setFlag(flag, value) for a permanent value.");
        }
        set(flag, FlagValue.until(value, ECClock.now().plus(ttl)));
    }

    /**
     * Sets {@code value} with its envelope: over the permanent value while it has a deadline, else as the permanent value
     * (a temporary layer over it is dropped).
     *
     * @throws IllegalArgumentException when the holder cannot carry the flag, or the value is locked on an ACTOR holder
     */
    public <T> void set(Flag<T> flag, FlagValue<T> value) {
        if (value.isLock() && holder.getHolderScope() == FlagScope.ACTOR) {
            throw new IllegalArgumentException("Flag " + flag.getKey() + " cannot be locked on " + holder.getHolderId() + ": lock belongs to"
                    + " places (regions), where it wins over every layer. A value of a holder that follows the player wins its ties with"
                    + " priority: <n> instead.");
        }
        if (value.getExpiresAt() == null) {
            mutate(flag, previous -> new Layers(flag, value, null), FlagChangeCause.SET);
        } else {
            mutate(flag, previous -> new Layers(flag, previous == null ? null : previous.permanent, value), FlagChangeCause.SET);
        }
    }

    /** Removes both layers; false when the flag had no value here. */
    public boolean remove(Flag<?> flag) {
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
        Flag<?> flag = FlagRegistry.get().find(key).orElse(null);
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
    boolean relink(Flag<?> flag) {
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
    void unlink(Flag<?> flag) {
        Layers layers = values.remove(flag.getKey());
        if (layers != null) {
            JsonNode raw = toRaw(layers);
            if (raw != null) {
                unknown.put(flag.getKey().toString(), raw);
            }
        }
    }

    private boolean link(Flag<?> flag, JsonNode raw) {
        try {
            values.put(flag.getKey(), fromRaw(flag, raw));
            if (!flag.accepts(holder) && flag.isRegistered()) {
                flag.getOwner().getLog().warning("Flag {} does not apply to {}: its value there is kept but ignored. Remove it from {},"
                        + " or post it on a holder the flag serves.", flag.getKey(), holder.getHolderId(), holder.getHolderId());
            }
            return true;
        } catch (FlagParseException unreadable) {
            return false;
        }
    }

    private <T> void mutate(Flag<T> flag, UnaryOperator<Layers> next, FlagChangeCause cause) {
        if (!flag.isRegistered()) {
            throw new IllegalStateException("Flag " + flag.getKey() + " is not registered: register it first with"
                    + " FlagRegistry.get().register(owner, flag), usually in the owner's enable.");
        }
        if (!holder.accepts(flag)) {
            throw new IllegalArgumentException("Flag " + flag.getKey() + " is a PLACE flag: it says what happens in a place, so "
                    + holder.getHolderId() + ", which follows its players wherever they stand, cannot carry it. Post it on a region,"
                    + " or on a group of that region.");
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
        if (top.getMode() == FlagMode.SET) {
            raw.put("mode", "set");
        }
        if (top.getPriority() != 0) {
            raw.put("priority", top.getPriority());
        }
        if (top.isLock()) {
            raw.put("lock", true);
        }
        raw.put("since", top.getSince().toString());
        if (top.getExpiresAt() != null) {
            raw.put("expiresAt", top.getExpiresAt().toString());
            if (layers.permanent != null) {
                raw.set("permanent", json(layers.flag, layers.permanent.getValue()));
            }
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private static <T> JsonNode json(Flag<T> flag, Object value) {
        return flag.getType().toJson((T) value);
    }

    private static <T> Layers fromRaw(Flag<T> flag, JsonNode raw) throws FlagParseException {
        if (raw == null || !raw.isObject() || !raw.has("value")) {
            throw new FlagParseException("Stored value " + raw + " of flag " + flag.getKey() + " is not {value: ..., expiresAt: ...}.");
        }
        T value = flag.getType().fromJson(raw.get("value"));
        JsonNode modeNode = raw.get("mode");
        FlagMode mode;
        try {
            mode = modeNode == null || modeNode.isNull() ? FlagMode.ADD : FlagMode.valueOf(modeNode.asText().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new FlagParseException("Stored mode '" + modeNode.asText() + "' of flag " + flag.getKey() + " is neither add nor set.", unknown);
        }
        JsonNode priority = raw.get("priority");
        JsonNode lock = raw.get("lock");
        Instant since = instant(flag, raw.get("since"), "time");
        Instant expiresAt = instant(flag, raw.get("expiresAt"), "deadline");
        FlagValue<T> live = (expiresAt == null ? FlagValue.permanent(value) : FlagValue.until(value, expiresAt))
                .withMode(mode)
                .withPriority(priority == null || priority.isNull() ? 0 : priority.asInt())
                .withLock(lock != null && lock.asBoolean())
                .withSince(since == null ? Instant.EPOCH : since);
        if (expiresAt == null) {
            return new Layers(flag, live, null);
        }
        JsonNode permanent = raw.get("permanent");
        return new Layers(flag, permanent == null || permanent.isNull() ? null
                : FlagValue.permanent(flag.getType().fromJson(permanent)).withSince(Instant.EPOCH), live);
    }

    private static @Nullable Instant instant(Flag<?> flag, @Nullable JsonNode node, String what) throws FlagParseException {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (DateTimeParseException notAnInstant) {
            throw new FlagParseException("Stored " + what + " '" + node.asText() + "' of flag " + flag.getKey()
                    + " is not an ISO-8601 instant such as 2026-10-06T12:00:00Z.", notAnInstant);
        }
    }
}
