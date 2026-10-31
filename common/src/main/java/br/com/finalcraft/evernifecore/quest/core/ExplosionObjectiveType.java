package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code explosion}: Explosions. */
public final class ExplosionObjectiveType extends ActionObjectiveType {

    public ExplosionObjectiveType() {
        super("explosion", EventSource.EXPLOSION, "key", "Explosions.");
    }
}
