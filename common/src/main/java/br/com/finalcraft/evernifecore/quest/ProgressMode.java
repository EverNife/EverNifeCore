package br.com.finalcraft.evernifecore.quest;

/** What a match does to an event objective. */
public enum ProgressMode {
    /** Adds to the count until the goal; the objective is then done. */
    COUNT,
    /** Never done: every match applies the objective's {@code on-match} effects with the delta (pay per action). */
    EACH_MATCH
}
