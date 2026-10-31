package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code break-block}: Breaking blocks. */
public final class BreakBlockObjectiveType extends ActionObjectiveType {

    public BreakBlockObjectiveType() {
        super("break-block", EventSource.BREAK, "block", "Breaking blocks.");
    }
}
