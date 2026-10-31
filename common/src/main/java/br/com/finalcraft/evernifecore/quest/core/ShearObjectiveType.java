package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code shear}: Shearing creatures. */
public final class ShearObjectiveType extends ActionObjectiveType {

    public ShearObjectiveType() {
        super("shear", EventSource.SHEAR, "entity", "Shearing creatures.");
    }
}
