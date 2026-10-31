package br.com.finalcraft.evernifecore.quest;

/** What a newer {@link QuestDefinition#getVersion() version} of a definition does to the instances already open. */
public enum DefinitionPolicy {
    /**
     * Keeps the progress of every objective whose id still exists and drops the rest; a current stage that is gone
     * restarts at the first stage, and a stage left with no objective counts as done.
     */
    KEEP_BY_ID,
    /** Sends every open instance back to available, keeping how many times it was completed. */
    RESET
}
