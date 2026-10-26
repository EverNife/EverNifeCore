package br.com.finalcraft.evernifecore.logic.audit;

import jakarta.annotation.Nullable;

import java.time.Duration;

/** How long an {@link AuditLog} keeps an entry: {@link #days(int)} (30 by default) or {@link #forever()}. Immutable. */
public final class AuditRetention {

    private static final AuditRetention FOREVER = new AuditRetention(null);

    private final @Nullable Duration keep;

    private AuditRetention(@Nullable Duration keep) {
        this.keep = keep;
    }

    /** @throws IllegalArgumentException when not positive */
    public static AuditRetention days(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("An audit log keeps its entries at least 1 day, not " + days + "; AuditRetention.forever()"
                    + " keeps them all.");
        }
        return new AuditRetention(Duration.ofDays(days));
    }

    public static AuditRetention forever() {
        return FOREVER;
    }

    /** How long an entry is kept; null for ever. */
    public @Nullable Duration getKeep() {
        return keep;
    }

    @Override
    public String toString() {
        return keep == null ? "forever" : keep.toDays() + " days";
    }
}
