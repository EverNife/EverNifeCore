package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.SpawnContext;

/** TRUE: animals do not spawn in the region. */
public final class DenyAnimalSpawnFlag extends StockDenyFlag {

    public DenyAnimalSpawnFlag(ECPluginData owner) {
        super(owner, "deny-animal-spawn", EventSource.SPAWN);
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof SpawnContext && ((SpawnContext) context).getEntityKind() == SpawnContext.Kind.ANIMAL;
    }
}
