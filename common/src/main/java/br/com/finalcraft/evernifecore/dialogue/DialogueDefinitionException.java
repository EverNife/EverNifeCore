package br.com.finalcraft.evernifecore.dialogue;

/** A dialogue that cannot run as written: the message names the dialogue, the node, the option and the way out. */
public class DialogueDefinitionException extends IllegalArgumentException {

    public DialogueDefinitionException(String message) {
        super(message);
    }

    public DialogueDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
