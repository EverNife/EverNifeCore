package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code breed}: Breeding creatures. */
public final class BreedObjectiveType extends ActionObjectiveType {

    public BreedObjectiveType() {
        super("breed", EventSource.BREED, "entity", "Breeding creatures.");
    }
}
