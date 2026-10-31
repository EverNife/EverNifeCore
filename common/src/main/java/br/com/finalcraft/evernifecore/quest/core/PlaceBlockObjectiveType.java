package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code place-block}: Placing blocks. */
public final class PlaceBlockObjectiveType extends ActionObjectiveType {

    public PlaceBlockObjectiveType() {
        super("place-block", EventSource.PLACE, "block", "Placing blocks.");
    }
}
