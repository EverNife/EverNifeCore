package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code enchant}: Enchanting items. */
public final class EnchantObjectiveType extends ActionObjectiveType {

    public EnchantObjectiveType() {
        super("enchant", EventSource.ENCHANT, "item", "Enchanting items.");
    }
}
