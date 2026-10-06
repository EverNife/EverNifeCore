package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: animals do not spawn in the region. */
public final class DenyAnimalSpawnFlag extends StockDenyFlag {

    public DenyAnimalSpawnFlag(ECPluginData owner) {
        super(owner, "deny-animal-spawn", EventSource.SPAWN);
    }
}
