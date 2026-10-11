package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.EntityContext;

/**
 * TRUE: blocks and entities in the region cannot be used. {@link DenyInteractBlocksFlag} and
 * {@link DenyInteractEntitiesFlag} refine it - where one of them is defined it decides, where not this one does.
 */
public final class DenyInteractFlag extends StockInteractFlag {

    public DenyInteractFlag(ECPluginData owner) {
        super(owner, "deny-interact", null);
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
