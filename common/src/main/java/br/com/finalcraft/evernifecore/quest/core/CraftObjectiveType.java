package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code craft}: Crafting items; a stack counts its amount. */
public final class CraftObjectiveType extends ActionObjectiveType {

    public CraftObjectiveType() {
        super("craft", EventSource.CRAFT, "item", "Crafting items; a stack counts its amount.");
    }
}
