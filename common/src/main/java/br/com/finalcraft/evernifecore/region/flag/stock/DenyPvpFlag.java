package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: players cannot hurt each other in the region. Holds for everyone - the bypass permission does not lift it. */
public final class DenyPvpFlag extends StockDenyFlag {

    public DenyPvpFlag(ECPluginData owner) {
        super(owner, "deny-pvp", EventSource.DAMAGE);
    }

    @Override
    public boolean isBypassable() {
        return false;
    }

    @Override
    public boolean applies(ActionContext context) {
        return hurts(context, DamageContext.Kind.PLAYER) && context.getPlayer() != null;
    }

    @Override
    public String getDescription() {
        return "TRUE: players cannot hurt each other in the region. Holds for everyone - the bypass permission does not lift it.";
    }
}
