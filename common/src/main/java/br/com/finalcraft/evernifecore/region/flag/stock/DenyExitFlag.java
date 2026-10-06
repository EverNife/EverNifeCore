package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: players cannot get out of the region by any means. */
public final class DenyExitFlag extends StockDenyFlag {

    public DenyExitFlag(ECPluginData owner) {
        super(owner, "deny-exit", EventSource.MOVE);
    }
}
