package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: blocks and entities in the region cannot be used. */
public final class DenyInteractFlag extends StockDenyFlag {

    public DenyInteractFlag(ECPluginData owner) {
        super(owner, "deny-interact", EventSource.INTERACT);
    }
}
