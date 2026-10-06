package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: players cannot teleport out of the region; walking out stays allowed. */
public final class DenyTeleportOutFlag extends StockDenyFlag {

    public DenyTeleportOutFlag(ECPluginData owner) {
        super(owner, "deny-teleport-out", EventSource.MOVE);
    }
}
