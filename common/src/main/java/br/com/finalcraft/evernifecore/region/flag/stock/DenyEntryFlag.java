package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: players cannot get into the region by any means. */
public final class DenyEntryFlag extends StockDenyFlag {

    public DenyEntryFlag(ECPluginData owner) {
        super(owner, "deny-entry", EventSource.MOVE);
    }
}
