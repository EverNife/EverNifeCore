package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code pickup}: Picking up items; a stack counts its amount. */
public final class PickupObjectiveType extends ActionObjectiveType {

    public PickupObjectiveType() {
        super("pickup", EventSource.ITEM_PICKUP, "item", "Picking up items; a stack counts its amount.");
    }
}
