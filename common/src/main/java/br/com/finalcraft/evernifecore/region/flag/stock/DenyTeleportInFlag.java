package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: players cannot teleport into the region; walking in stays allowed. */
public final class DenyTeleportInFlag extends StockDenyFlag {

    public DenyTeleportInFlag(ECPluginData owner) {
        super(owner, "deny-teleport-in", EventSource.MOVE);
    }
}
