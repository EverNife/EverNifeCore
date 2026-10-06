package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.MoveContext;

/** TRUE: players cannot get out of the region by any means. */
public final class DenyExitFlag extends StockDenyFlag {

    public DenyExitFlag(ECPluginData owner) {
        super(owner, "deny-exit", EventSource.MOVE);
    }

    @Override
    boolean applies(ActionContext context) {
        return crossing(context, MoveContext.Transition.LEAVE, null);
    }

    /** Where a denied player is sent back to, and when a player who keeps trying is moved further out. */
    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return MoveDenySettings.class;
    }
}
