package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;

/** TRUE: fire does not spread into the region. Fire lit by a player or by lava is a block place, decided by deny-block-place. */
public final class DenyFireSpreadFlag extends DenyFlag {

    public DenyFireSpreadFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-fire-spread"), EventSource.FIRE_SPREAD);
    }

    @Override
    public String getDescription() {
        return "TRUE: fire does not spread into the region. Fire lit by a player or by lava is a block place - deny-block-place decides it.";
    }
}
