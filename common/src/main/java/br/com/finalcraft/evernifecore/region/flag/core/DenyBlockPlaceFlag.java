package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;

/** TRUE: blocks cannot be placed in the region. */
public final class DenyBlockPlaceFlag extends DenyFlag {

    public DenyBlockPlaceFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-block-place"), EventSource.PLACE);
    }

    @Override
    public String getDescription() {
        return "TRUE: blocks cannot be placed in the region.";
    }
}
