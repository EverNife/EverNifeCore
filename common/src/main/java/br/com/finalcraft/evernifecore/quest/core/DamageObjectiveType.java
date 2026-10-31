package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code damage}: Damaging creatures or players. */
public final class DamageObjectiveType extends ActionObjectiveType {

    public DamageObjectiveType() {
        super("damage", EventSource.DAMAGE, "victim", "Damaging creatures or players.");
    }
}
