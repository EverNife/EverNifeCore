package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code tame}: Taming creatures. */
public final class TameObjectiveType extends ActionObjectiveType {

    public TameObjectiveType() {
        super("tame", EventSource.TAME, "entity", "Taming creatures.");
    }
}
