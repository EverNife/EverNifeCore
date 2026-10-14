package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.SpawnContext;

/** TRUE: monsters do not spawn in the region. */
public final class DenyMonsterSpawnFlag extends DenyFlag {

    public DenyMonsterSpawnFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-monster-spawn"), EventSource.SPAWN);
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof SpawnContext && ((SpawnContext) context).getEntity().getEntityType() == ECEntityType.MONSTER;
    }

    @Override
    public String getDescription() {
        return "TRUE: monsters do not spawn in the region.";
    }
}
