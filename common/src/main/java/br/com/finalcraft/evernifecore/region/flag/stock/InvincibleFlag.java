package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: players in the region take no damage, whatever hurts them. */
public final class InvincibleFlag extends StockDenyFlag {

    public InvincibleFlag(ECPluginData owner) {
        super(owner, "invincible", EventSource.DAMAGE);
    }

    @Override
    boolean applies(ActionContext context) {
        return hurts(context, DamageContext.Kind.PLAYER);
    }
}
