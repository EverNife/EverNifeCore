package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: animals in the region cannot be hurt. */
public final class DenyDamageAnimalsFlag extends StockDenyFlag {

    public DenyDamageAnimalsFlag(ECPluginData owner) {
        super(owner, "deny-damage-animals", EventSource.DAMAGE);
    }

    @Override
    public boolean applies(ActionContext context) {
        return hurts(context, DamageContext.Kind.ANIMAL);
    }
}
