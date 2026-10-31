package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code consume}: Eating or drinking items. */
public final class ConsumeObjectiveType extends ActionObjectiveType {

    public ConsumeObjectiveType() {
        super("consume", EventSource.CONSUME, "item", "Eating or drinking items.");
    }
}
