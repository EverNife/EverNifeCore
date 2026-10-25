package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/** One thing a gate, an effect or a counter did for a context, stamped by {@code ECClock}. Immutable. */
public final class AuditEntry {

    private final AuditKind kind;
    private final Caller caller;
    private final ProgressOwner owner;
    private final Instant instant;
    private final String text;
    private final @Nullable BigDecimal amount;
    private final @Nullable Throwable cause;

    public AuditEntry(AuditKind kind, Caller caller, ProgressOwner owner, Instant instant, String text,
                      @Nullable BigDecimal amount, @Nullable Throwable cause) {
        this.kind = kind;
        this.caller = caller;
        this.owner = owner;
        this.instant = instant;
        this.text = text;
        this.amount = amount;
        this.cause = cause;
    }

    public AuditKind getKind() {
        return kind;
    }

    public Caller getCaller() {
        return caller;
    }

    public ProgressOwner getOwner() {
        return owner;
    }

    public Instant getInstant() {
        return instant;
    }

    public String getText() {
        return text;
    }

    public @Nullable BigDecimal getAmount() {
        return amount;
    }

    public @Nullable Throwable getCause() {
        return cause;
    }

    /** {@code [caller] KIND text}, the line a trace prints. */
    @Override
    public String toString() {
        return "[" + caller + "] " + kind + " " + text;
    }
}
