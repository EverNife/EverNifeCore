package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: monsters do not spawn in the region. */
public final class DenyMonsterSpawnFlag extends StockDenyFlag {

    public DenyMonsterSpawnFlag(ECPluginData owner) {
        super(owner, "deny-monster-spawn", EventSource.SPAWN);
    }
}
