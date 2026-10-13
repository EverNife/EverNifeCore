package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.EntityContext;

/**
 * TRUE: blocks and entities in the region cannot be used. {@link DenyInteractBlocksFlag} and
 * {@link DenyInteractEntitiesFlag} refine it - where one of them is defined it decides, where not this one does.
 */
public final class DenyInteractFlag extends InteractDenyFlag {

    public DenyInteractFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-interact"), null);
    }

    @Override
    public boolean applies(ActionContext context) {
        return isBlockUse(context) || context instanceof EntityContext;
    }

    @Override
    public String getDescription() {
        return "TRUE: blocks and entities in the region cannot be used; where deny-interact-blocks or deny-interact-entities is set, that one decides.";
    }
}
