package br.com.finalcraft.evernifecore.logic.audit;

/** What an {@link AuditEntry} records. */
public enum AuditKind {
    CONDITION_FAILED,
    /** A condition threw instead of answering; it counted as failed. */
    CONDITION_THREW,
    COST_TAKEN,
    COST_REFUNDED,
    /** A refund threw: what the cost describes was not given back and needs a hand. */
    REFUND_FAILED,
    EFFECT_APPLIED,
    EFFECT_FAILED,
    /** A delayed effect found its player gone and the deferred-effect sink discarded it. */
    DEFERRED_DROPPED,
    GATE_DENIED,
    GATE_PAID,
    HELD,
    RELEASED
}
