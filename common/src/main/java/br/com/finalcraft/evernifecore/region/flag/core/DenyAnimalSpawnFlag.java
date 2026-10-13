package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.SpawnContext;

/** TRUE: animals do not spawn in the region. */
public final class DenyAnimalSpawnFlag extends DenyFlag {

    public DenyAnimalSpawnFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-animal-spawn"), EventSource.SPAWN);
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof SpawnContext && ((SpawnContext) context).getEntityKind() == SpawnContext.Kind.ANIMAL;
    }

    @Override
    public String getDescription() {
        return "TRUE: animals do not spawn in the region.";
    }
}
