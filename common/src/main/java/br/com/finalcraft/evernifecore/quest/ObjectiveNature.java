package br.com.finalcraft.evernifecore.quest;

/** How an objective learns that it progressed. */
public enum ObjectiveNature {
    /** Counts matching actions ({@link ObjectiveType#matches}). */
    EVENT,
    /** Done while a condition holds ({@link ObjectiveType#stateOf}), re-checked after the sources the condition needs. */
    STATE
}
