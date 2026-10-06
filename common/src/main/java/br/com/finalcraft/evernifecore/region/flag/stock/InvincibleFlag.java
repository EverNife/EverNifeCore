package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: players in the region take no damage. */
public final class InvincibleFlag extends StockDenyFlag {

    public InvincibleFlag(ECPluginData owner) {
        super(owner, "invincible", EventSource.DAMAGE);
    }
}
