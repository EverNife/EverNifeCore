package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EventSource;

/** {@code xp}: Gaining experience; each gain counts its points. */
public final class XpObjectiveType extends ActionObjectiveType {

    public XpObjectiveType() {
        super("xp", EventSource.XP_GAIN, "key", "Gaining experience; each gain counts its points.");
    }
}
