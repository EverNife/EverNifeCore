package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;

/** TRUE: blocks in the region cannot be used. Refines {@code deny-interact}: asked first, and reads it where undefined. */
public final class DenyInteractBlocksFlag extends StockInteractFlag {

    public DenyInteractBlocksFlag(ECPluginData owner, DenyInteractFlag parent) {
        super(owner, "deny-interact-blocks", parent);
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
