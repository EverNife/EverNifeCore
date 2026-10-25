package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.flag.FlagKey;

/** TRUE: entities in the region cannot be used. Refines {@code deny-interact}: asked first, and reads it where undefined. */
public final class DenyInteractEntitiesFlag extends InteractDenyFlag {

    public DenyInteractEntitiesFlag(DenyInteractFlag parent) {
        super(FlagKey.of(CoreFlags.OWNER, "deny-interact-entities"), parent);
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof EntityContext;
    }

    @Override
    public String getDescription() {
        return "TRUE: entities in the region cannot be used. Where it is not set, deny-interact decides.";
    }
}
