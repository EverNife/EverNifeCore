package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.time.ECClock;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What happened while one {@link ExecutionContext} ran, in memory and in order: every failed condition, cost taken or
 * refunded, effect applied or failed. A context and every context derived from it write to the same trail. Every entry
 * also goes to {@link LogicTrace}. Thread-safe.
 */
public final class AuditTrail {

    private final List<AuditEntry> entries = new ArrayList<>();

    /** Records {@code text} as {@code kind} for {@code ctx}'s caller and owner, stamped now. */
    public AuditEntry add(ExecutionContext ctx, AuditKind kind, String text) {
        return add(ctx, kind, text, null, null);
    }

    public AuditEntry add(ExecutionContext ctx, AuditKind kind, String text, @Nullable BigDecimal amount, @Nullable Throwable cause) {
        AuditEntry entry = new AuditEntry(kind, ctx.getCaller(), ctx.getOwner(), ECClock.now(), text, amount, cause);
        synchronized (entries) {
            entries.add(entry);
        }
        LogicTrace.publish(ctx, () -> kind + " " + text);
        return entry;
    }

    /** Every entry so far, in order. */
    public List<AuditEntry> getEntries() {
        synchronized (entries) {
            return Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    /** The entries of {@code kind}, in order. */
    public List<AuditEntry> getEntries(AuditKind kind) {
        List<AuditEntry> found = new ArrayList<>();
        for (AuditEntry entry : getEntries()) {
            if (entry.getKind() == kind) {
                found.add(entry);
            }
        }
        return found;
    }
}
