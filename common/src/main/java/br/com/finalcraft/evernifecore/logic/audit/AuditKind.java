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
    RELEASED,
    /** A currency balance moved; the subject is the currency. */
    BALANCE_CHANGE,
    /** A currency moved between two owners: the player paid, the counterparty received. */
    TRANSFER,
    /** A player counted toward a contribution; the subject is the contribution. */
    DEPOSIT,
    /** A plugin's booster manager granted a booster; the subject is the boosted gain. */
    BOOSTER_GRANTED,
    DELIVERY_ENQUEUED,
    DELIVERY_CLAIMED,
    DELIVERY_EXPIRED,
    DELIVERY_PURGED,
    /** A delayed effect whose player left was thrown away: only the core's own effects can wait in a delivery. */
    DELIVERY_DISCARDED,
    /** A reward rule paid a member its share (applied, or kept as a pending delivery); the subject is the reward's owner. */
    REWARD_PAID,
    /** A shop offer was bought or sold; the subject is the first price line's id, the amount its total. */
    TRANSACTION
}
