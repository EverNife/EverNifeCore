package br.com.finalcraft.evernifecore.logic.value;

import java.math.BigDecimal;

/** What one {@link Contribution#deposit} counted. Immutable. */
public final class DepositResult {

    private final BigDecimal counted;
    private final BigDecimal total;
    private final boolean completed;

    DepositResult(BigDecimal counted, BigDecimal total, boolean completed) {
        this.counted = counted;
        this.total = total;
        this.completed = completed;
    }

    /** Zero when nothing counted: nothing held, nothing missing, or already complete. */
    public BigDecimal getCounted() {
        return counted;
    }

    /** The contribution's total after this deposit. */
    public BigDecimal getTotal() {
        return total;
    }

    /** Whether this deposit reached the target. */
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public String toString() {
        return "DepositResult{counted=" + counted.toPlainString() + ", total=" + total.toPlainString() + (completed ? ", completed" : "") + "}";
    }
}
