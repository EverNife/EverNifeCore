package br.com.finalcraft.evernifecore.logic.audit;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A filter of an {@link AuditLog}: each call narrows, none is required; newest entries first, {@value #DEFAULT_LIMIT}
 * at most unless {@link #limit} says otherwise. Short-lived and not thread-safe: build it where it is used.
 */
public final class AuditQuery {

    public static final int DEFAULT_LIMIT = 50;

    private @Nullable UUID player;
    private @Nullable UUID counterparty;
    private @Nullable UUID betweenA;
    private @Nullable UUID betweenB;
    private @Nullable Instant since;
    private @Nullable AuditKind kind;
    private int limit = DEFAULT_LIMIT;

    public AuditQuery() {
    }

    public AuditQuery player(UUID player) {
        this.player = player;
        return this;
    }

    public AuditQuery counterparty(UUID counterparty) {
        this.counterparty = counterparty;
        return this;
    }

    /** Entries whose player and counterparty are {@code a} and {@code b}, in either order: who traded what with whom. */
    public AuditQuery between(UUID a, UUID b) {
        this.betweenA = a;
        this.betweenB = b;
        return this;
    }

    /** Entries at or after {@code since}. */
    public AuditQuery since(Instant since) {
        this.since = since;
        return this;
    }

    public AuditQuery kind(AuditKind kind) {
        this.kind = kind;
        return this;
    }

    /** @throws IllegalArgumentException when not positive */
    public AuditQuery limit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("An audit query returns at least 1 entry; " + limit + " was asked.");
        }
        this.limit = limit;
        return this;
    }

    @Nullable UUID getPlayer() {
        return player;
    }

    @Nullable UUID getCounterparty() {
        return counterparty;
    }

    @Nullable UUID getBetweenA() {
        return betweenA;
    }

    @Nullable UUID getBetweenB() {
        return betweenB;
    }

    @Nullable Instant getSince() {
        return since;
    }

    @Nullable AuditKind getKind() {
        return kind;
    }

    int getLimit() {
        return limit;
    }
}
