package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.EventSource;

/** TRUE: fake players - machines acting as a player - cannot break blocks in the region. */
public final class DenyFakePlayerBreakFlag extends StockDenyFlag {

    public DenyFakePlayerBreakFlag(ECPluginData owner) {
        super(owner, "deny-fake-player-break", EventSource.BREAK);
    }

    @Override
    public boolean applies(ActionContext context) {
        return context instanceof BlockContext && ((BlockContext) context).isFakePlayer();
    }
}
