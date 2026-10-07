package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.MoveContext;
import br.com.finalcraft.evernifecore.region.session.MoveCause;

/** TRUE: players cannot teleport out of the region; walking out is up to {@code deny-exit}. */
public final class DenyTeleportOutFlag extends StockDenyFlag {

    public DenyTeleportOutFlag(ECPluginData owner) {
        super(owner, "deny-teleport-out", EventSource.MOVE);
    }

    @Override
    public boolean applies(ActionContext context) {
        return crossing(context, MoveContext.Transition.LEAVE, MoveCause.TELEPORT);
    }

    /** Where a denied player is sent back to, and when a player who keeps trying is moved further out. */
    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return MoveDenySettings.class;
    }
}
