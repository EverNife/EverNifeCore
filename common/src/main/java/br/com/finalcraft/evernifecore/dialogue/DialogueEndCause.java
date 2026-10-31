package br.com.finalcraft.evernifecore.dialogue;

/** Why a dialogue session ended - the first cause wins, later ones find nothing to end. */
public enum DialogueEndCause {

    /** The talk reached its end: an option with {@code end}, or a node without options. */
    END,
    /** The player's screen went away (they closed it, or something closed it for them). */
    CLOSED,
    /** No answer within the dialogue's timeout. */
    TIMEOUT,
    /** A {@code fail-when} condition failed. */
    FAIL_WHEN,
    /** The player left the server. */
    PLAYER_LEFT,
    /** Another dialogue started for the same player. */
    REPLACED,
    /** The owning plugin shut down, or registered the dialogue again. */
    OWNER_DISABLED,
    /** A plugin ended it ({@code end-dialogue}, {@link DialogueSession#end}). */
    API
}
