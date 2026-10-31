package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code spawn}: Creatures spawning. */
public final class SpawnObjectiveType extends ActionObjectiveType {

    public SpawnObjectiveType() {
        super("spawn", EventSource.SPAWN, "entity", "Creatures spawning.");
    }
}
