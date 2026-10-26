package br.com.finalcraft.evernifecore.logic.delivery;

/** What a pending {@link Delivery} does while some of its effects belong to a plugin that is not loaded. */
public enum DeliveryPolicy {
    /** Hands out what the core can apply and keeps the rest raw until its plugin loads. */
    KEEP_SELF_CONTAINED,
    /** Touches nothing until every effect's plugin is loaded: the delivery is one piece. */
    REQUIRES_ORIGIN
}
