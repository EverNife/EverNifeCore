package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code drop}: Dropping items; a stack counts its amount. */
public final class DropObjectiveType extends ActionObjectiveType {

    public DropObjectiveType() {
        super("drop", EventSource.ITEM_DROP, "item", "Dropping items; a stack counts its amount.");
    }
}
