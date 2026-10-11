package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: blocks in the region cannot be broken. */
public final class DenyBlockBreakFlag extends StockDenyFlag {

    public DenyBlockBreakFlag(ECPluginData owner) {
        super(owner, "deny-block-break", EventSource.BREAK);
    }

    @Override
    public String getDescription() {
        return "TRUE: blocks in the region cannot be broken.";
    }
}
