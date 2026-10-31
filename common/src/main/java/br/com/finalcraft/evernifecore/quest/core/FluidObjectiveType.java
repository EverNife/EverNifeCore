package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code fluid}: Fluids flowing. */
public final class FluidObjectiveType extends ActionObjectiveType {

    public FluidObjectiveType() {
        super("fluid", EventSource.FLUID, "key", "Fluids flowing.");
    }
}
