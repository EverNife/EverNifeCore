package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: fake players (machines, mods) cannot break blocks in the region. */
public final class DenyFakePlayerBreakFlag extends StockDenyFlag {

    public DenyFakePlayerBreakFlag(ECPluginData owner) {
        super(owner, "deny-fake-player-break", EventSource.BREAK);
    }
}
