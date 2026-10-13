package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;

/** TRUE: blocks in the region cannot be used. Refines {@code deny-interact}: asked first, and reads it where undefined. */
public final class DenyInteractBlocksFlag extends InteractDenyFlag {

    public DenyInteractBlocksFlag(DenyInteractFlag parent) {
        super(FlagKey.of(CoreFlags.OWNER, "deny-interact-blocks"), parent);
    }

    @Override
    public boolean applies(ActionContext context) {
        return isBlockUse(context);
    }

    @Override
    public String getDescription() {
        return "TRUE: blocks in the region cannot be used. Where it is not set, deny-interact decides.";
    }
}
