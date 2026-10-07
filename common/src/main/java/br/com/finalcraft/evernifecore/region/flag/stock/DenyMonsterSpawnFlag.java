package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.SpawnContext;

/** TRUE: monsters do not spawn in the region. */
public final class DenyMonsterSpawnFlag extends StockDenyFlag {

    public DenyMonsterSpawnFlag(ECPluginData owner) {
        super(owner, "deny-monster-spawn", EventSource.SPAWN);
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof SpawnContext && ((SpawnContext) context).getEntityKind() == SpawnContext.Kind.MONSTER;
    }
}
