package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.time.ECClock;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One thing a gate, an effect, a wallet or a delivery did for a plugin, stamped by {@code ECClock}: what an
 * {@link AuditTrail} holds in memory and an {@link AuditLog} keeps in the plugin's storage. Immutable.
 */
public final class AuditEntry {

    private final UUID id;
    private final AuditKind kind;
    private final Caller caller;
    private final ProgressOwner owner;
    private final Instant instant;
    private final String text;
    private final @Nullable BigDecimal amount;
    private final @Nullable Throwable cause;
    private final @Nullable UUID player;
    private final @Nullable UUID counterparty;
    private final @Nullable String subject;

    /** The entry a trail writes: its player is the owner's when the owner is a player. */
    public AuditEntry(AuditKind kind, Caller caller, ProgressOwner owner, Instant instant, String text,
                      @Nullable BigDecimal amount, @Nullable Throwable cause) {
        this(UUID.randomUUID(), kind, caller, owner, instant, text, amount, cause, playerOf(owner), null, null);
    }

    private AuditEntry(UUID id, AuditKind kind, Caller caller, ProgressOwner owner, Instant instant, String text, @Nullable BigDecimal amount,
                       @Nullable Throwable cause, @Nullable UUID player, @Nullable UUID counterparty, @Nullable String subject) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.caller = Objects.requireNonNull(caller, "caller");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.instant = Objects.requireNonNull(instant, "instant");
        this.text = text == null ? "" : text;
        this.amount = amount;
        this.cause = cause;
        this.player = player;
        this.counterparty = counterparty;
        this.subject = subject;
    }

    /** An entry of {@code kind} by {@code caller}, stamped now, owned by the global owner until {@link Builder#owner} says otherwise. */
    public static Builder builder(AuditKind kind, Caller caller) {
        return new Builder(kind, caller);
    }

    private static @Nullable UUID playerOf(ProgressOwner owner) {
        if (!owner.isKind(ProgressOwner.PLAYER)) {
            return null;
        }
        try {
            return UUID.fromString(owner.getId());
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    public UUID getId() {
        return id;
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

    /** What happened, in words: the detail line of the log's page. */
    public String getText() {
        return text;
    }

    public @Nullable BigDecimal getAmount() {
        return amount;
    }

    /** What a failure threw; never kept by an {@link AuditLog}. */
    public @Nullable Throwable getCause() {
        return cause;
    }

    /** The player it was about (who paid, who received, who deposited); null when none. */
    public @Nullable UUID getPlayer() {
        return player;
    }

    /** The other party of a transfer, a trade or a give to another player; null when there is none. */
    public @Nullable UUID getCounterparty() {
        return counterparty;
    }

    /** What moved: the currency, the item, the contribution, the boosted gain; null when nothing in particular. */
    public @Nullable String getSubject() {
        return subject;
    }

    /** {@code [caller] KIND text}, the line a trace prints. */
    @Override
    public String toString() {
        return "[" + caller + "] " + kind + " " + text;
    }

    /** Builds an {@link AuditEntry}; one use. */
    public static final class Builder {
        private final AuditKind kind;
        private final Caller caller;
        private UUID id = UUID.randomUUID();
        private ProgressOwner owner = ProgressOwner.global();
        private @Nullable Instant at;
        private String text = "";
        private @Nullable BigDecimal amount;
        private @Nullable UUID player;
        private boolean playerSet;
        private @Nullable UUID counterparty;
        private @Nullable String subject;

        private Builder(AuditKind kind, Caller caller) {
            this.kind = kind;
            this.caller = caller;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        /** Also its player, when the owner is a player and no {@link #player} was given. */
        public Builder owner(ProgressOwner owner) {
            this.owner = owner;
            return this;
        }

        public Builder at(Instant at) {
            this.at = at;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder amount(@Nullable BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder player(@Nullable UUID player) {
            this.player = player;
            this.playerSet = true;
            return this;
        }

        public Builder counterparty(@Nullable UUID counterparty) {
            this.counterparty = counterparty;
            return this;
        }

        public Builder subject(@Nullable String subject) {
            this.subject = subject;
            return this;
        }

        public AuditEntry build() {
            return new AuditEntry(id, kind, caller, owner, at == null ? ECClock.now() : at, text, amount, null,
                    playerSet ? player : playerOf(owner), counterparty, subject);
        }
    }
}
