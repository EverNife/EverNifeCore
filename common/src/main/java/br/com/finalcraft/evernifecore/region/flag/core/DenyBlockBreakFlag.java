package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;

/** TRUE: blocks in the region cannot be broken. */
public final class DenyBlockBreakFlag extends DenyFlag {

    public DenyBlockBreakFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-block-break"), EventSource.BREAK);
    }

    @Override
    public String getDescription() {
        return "TRUE: blocks in the region cannot be broken.";
    }
}
