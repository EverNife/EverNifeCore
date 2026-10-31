package br.com.finalcraft.evernifecore.quest;

/** Where one owner stands on one quest. */
public enum QuestStatus {
    /** Its gate or its {@code requires} do not pass. */
    LOCKED,
    /** Nothing says otherwise: it can be offered or started. */
    AVAILABLE,
    /** Offered and not answered yet. */
    OFFERED,
    /** Being done. */
    ACTIVE,
    /** Done, and its repeat policy allows no more. */
    COMPLETED,
    FAILED,
    /** Offered or available past its {@code expires-at}. */
    EXPIRED,
    ABANDONED,
    /** Done, and waiting for its repeat cooldown or window. */
    COOLDOWN;

    /** Whether the instance is open: offered or active. */
    public boolean isOpen() {
        return this == OFFERED || this == ACTIVE;
    }
}
