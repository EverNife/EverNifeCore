package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code smelt}: Taking smelted items; a stack counts its amount. */
public final class SmeltObjectiveType extends ActionObjectiveType {

    public SmeltObjectiveType() {
        super("smelt", EventSource.SMELT, "item", "Taking smelted items; a stack counts its amount.");
    }
}
