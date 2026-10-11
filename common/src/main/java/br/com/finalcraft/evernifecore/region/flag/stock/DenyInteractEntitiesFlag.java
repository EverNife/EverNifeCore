package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.EntityContext;

/** TRUE: entities in the region cannot be used. Refines {@code deny-interact}: asked first, and reads it where undefined. */
public final class DenyInteractEntitiesFlag extends StockInteractFlag {

    public DenyInteractEntitiesFlag(ECPluginData owner, DenyInteractFlag parent) {
        super(owner, "deny-interact-entities", parent);
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
