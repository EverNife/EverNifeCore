package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code kill}: Killing creatures or players. */
public final class KillObjectiveType extends ActionObjectiveType {

    public KillObjectiveType() {
        super("kill", EventSource.KILL, "entity", "Killing creatures or players.");
    }
}
