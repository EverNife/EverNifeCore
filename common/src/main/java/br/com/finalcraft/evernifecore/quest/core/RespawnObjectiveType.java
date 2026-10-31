package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code respawn}: Respawning. */
public final class RespawnObjectiveType extends ActionObjectiveType {

    public RespawnObjectiveType() {
        super("respawn", EventSource.RESPAWN, "key", "Respawning.");
    }
}
