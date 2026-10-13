package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.MoveContext;

/** TRUE: players cannot get out of the region by any means. */
public final class DenyExitFlag extends DenyFlag {

    public DenyExitFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-exit"), EventSource.MOVE);
    }

    @Override
    public boolean applies(ActionContext context) {
        return crossing(context, MoveContext.Transition.LEAVE, null);
    }

    /** Where a denied player is sent back to, and when a player who keeps trying is moved further out. */
    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return MoveDenySettings.class;
    }

    @Override
    public String getDescription() {
        return "TRUE: players cannot get out of the region by any means.";
    }
}
