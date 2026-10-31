package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code fish}: Fishing items. */
public final class FishObjectiveType extends ActionObjectiveType {

    public FishObjectiveType() {
        super("fish", EventSource.FISH, "item", "Fishing items.");
    }
}
