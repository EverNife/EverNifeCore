package br.com.finalcraft.evernifecore.logic.value;

import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.MutableVariable;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.time.ECClock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * A number that moves with use and drifts back: each {@link #bump} moves it by the spec's step, inside its bounds, and
 * the decay pulls it toward the base as time passes. The decay is applied on read from the stored instant and
 * {@link ECClock}: no sweep, no scheduler. One state per {@link ProgressOwner}; as a variable it reads and sets the
 * context owner's. Any thread; the writes of one owner are serialized.
 */
public final class DynamicValue implements MutableVariable<BigDecimal> {

    private final DynamicValues values;
    private final String id;
    private volatile DynamicValueSpec spec;

    DynamicValue(DynamicValues values, String id, DynamicValueSpec spec) {
        this.values = values;
        this.id = id;
        this.spec = spec;
    }

    void setSpec(DynamicValueSpec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    public DynamicValueSpec getSpec() {
        return spec;
    }

    /** The value of {@code scope} now, decayed; the base for an owner that never moved it. */
    public BigDecimal get(ProgressOwner scope) {
        DynamicValues.Row row = values.rows.get(key(scope));
        if (row == null) {
            return spec.clamp(spec.getBase());
        }
        synchronized (row) {
            return current(row, ECClock.now().toEpochMilli())[0];
        }
    }

    /** Adds the spec's step; the new value, clamped. */
    public BigDecimal bump(ProgressOwner scope) {
        return bump(scope, spec.getStep());
    }

    /** Adds {@code delta} (negative lowers it); the new value, clamped. */
    public BigDecimal bump(ProgressOwner scope, BigDecimal delta) {
        return write(scope, delta, true);
    }

    /** Replaces the value, clamped; the decay counts from now. */
    public void set(ProgressOwner scope, BigDecimal value) {
        write(scope, value, false);
    }

    /** Back to the base. */
    public void reset(ProgressOwner scope) {
        write(scope, spec.getBase(), false);
    }

    private BigDecimal write(ProgressOwner scope, BigDecimal amount, boolean add) {
        DynamicValues.Row row = values.rows.computeIfAbsent(key(scope), DynamicValues.Row::new);
        BigDecimal written;
        DynamicValues.Row snapshot;
        synchronized (row) {
            long now = ECClock.now().toEpochMilli();
            BigDecimal[] state = current(row, now);
            written = spec.clamp(add ? state[0].add(amount) : amount);
            row.value = written.toPlainString();
            //an unchanged value keeps the decay's partial step; a new one starts counting now
            row.at = add && written.compareTo(state[0]) == 0 ? state[1].longValueExact() : now;
            snapshot = row.copy();
        }
        values.write(snapshot);
        return written;
    }

    /** {value, the instant the remaining decay counts from}. */
    private BigDecimal[] current(DynamicValues.Row row, long now) {
        BigDecimal base = spec.getBase();
        if (row.value == null) {
            return new BigDecimal[]{spec.clamp(base), BigDecimal.valueOf(now)};
        }
        BigDecimal value = new BigDecimal(row.value);
        Duration every = spec.getDecayEvery();
        long anchor = row.at;
        if (every != null && now > anchor && value.compareTo(base) != 0) {
            long steps = (now - anchor) / every.toMillis();
            if (steps > 0) {
                BigDecimal move = spec.getDecayAmount().multiply(BigDecimal.valueOf(steps));
                value = value.compareTo(base) > 0 ? value.subtract(move).max(base) : value.add(move).min(base);
                anchor += steps * every.toMillis();
            }
        }
        return new BigDecimal[]{spec.clamp(value), BigDecimal.valueOf(anchor)};
    }

    private String key(ProgressOwner scope) {
        return id + "@" + scope;
    }

    /** {@code <plugin>:name}. */
    @Override
    public String getId() {
        return id;
    }

    @Override
    public Class<BigDecimal> getType() {
        return BigDecimal.class;
    }

    @Override
    public BigDecimal read(ExecutionContext ctx) {
        return get(ctx.getOwner());
    }

    @Override
    public void set(ExecutionContext ctx, BigDecimal value) {
        set(ctx.getOwner(), value);
    }

    @Override
    public String toString() {
        return "DynamicValue{" + id + ", " + spec + "}";
    }
}
