package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.BlockContext;

/** TRUE: fake players - machines acting as a player - cannot break blocks in the region. */
public final class DenyFakePlayerBreakFlag extends DenyFlag {

    public DenyFakePlayerBreakFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-fake-player-break"), EventSource.BREAK);
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof BlockContext && ((BlockContext) context).isFakePlayer();
    }

    @Override
    public String getDescription() {
        return "TRUE: fake players - machines acting as a player - cannot break blocks in the region.";
    }
}
