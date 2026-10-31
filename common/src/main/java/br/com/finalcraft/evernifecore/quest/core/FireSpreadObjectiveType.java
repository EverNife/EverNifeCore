package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code fire-spread}: Fire spreading to blocks. */
public final class FireSpreadObjectiveType extends ActionObjectiveType {

    public FireSpreadObjectiveType() {
        super("fire-spread", EventSource.FIRE_SPREAD, "block", "Fire spreading to blocks.");
    }
}
