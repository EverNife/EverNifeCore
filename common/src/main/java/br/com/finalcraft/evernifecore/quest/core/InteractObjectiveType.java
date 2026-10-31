package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code interact}: Interacting with a block or a creature. */
public final class InteractObjectiveType extends ActionObjectiveType {

    public InteractObjectiveType() {
        super("interact", EventSource.INTERACT, "target", "Interacting with a block or a creature.");
    }
}
