package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.npc.NpcRef;

/** {@code interact-npc}: talking to a NPC, named {@code provider:id} ({@code npc: "citizens:12"}); globs on the entity type also work. */
public final class InteractNpcObjectiveType extends ActionObjectiveType {

    public InteractNpcObjectiveType() {
        super("interact-npc", EventSource.INTERACT_NPC, "npc", "Interacting with a NPC.");
    }

    @Override
    public boolean matches(ActionEvent<?> event, KeyFilter filter) {
        if (filter.getPattern() == null) {
            return true;
        }
        NpcRef npc = ((EntityContext) event.getContext()).getEntity().getNpc();
        if (npc != null && npc.toString().equalsIgnoreCase(filter.getPattern())) {
            return true;
        }
        return super.matches(event, filter);
    }
}
