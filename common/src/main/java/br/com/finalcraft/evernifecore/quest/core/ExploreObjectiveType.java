package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code explore}: Entering chunks never visited. */
public final class ExploreObjectiveType extends ActionObjectiveType {

    public ExploreObjectiveType() {
        super("explore", EventSource.EXPLORE, "key", "Entering chunks never visited.");
    }
}
