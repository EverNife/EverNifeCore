package br.com.finalcraft.evernifecore.quest;

/** Why an active quest failed. */
public enum FailCause {
    /** A {@code fail-when} condition stopped passing. */
    CONDITION,
    /** Active longer than its {@code time-limit}. */
    TIME_LIMIT,
    /** The group, region or world that owned it ended. */
    OWNER_GONE,
    /** An effect ({@code fail-quest}) or a stage that goes to {@code FAIL}. */
    EFFECT,
    /** An admin or the owning plugin through the API. */
    COMMAND
}
