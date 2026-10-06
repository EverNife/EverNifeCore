package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: blocks cannot be placed in the region. */
public final class DenyBlockPlaceFlag extends StockDenyFlag {

    public DenyBlockPlaceFlag(ECPluginData owner) {
        super(owner, "deny-block-place", EventSource.PLACE);
    }
}
