package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.MoveContext;
import br.com.finalcraft.evernifecore.region.session.MoveCause;

/** TRUE: players cannot teleport into the region; walking in is up to {@code deny-entry}. */
public final class DenyTeleportInFlag extends DenyFlag {

    public DenyTeleportInFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-teleport-in"), EventSource.MOVE);
    }

    @Override
    public boolean applies(ActionContext context) {
        return crossing(context, MoveContext.Transition.ENTER, MoveCause.TELEPORT);
    }

    /** Where a denied player is sent back to, and when a player who keeps trying is moved further out. */
    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return MoveDenySettings.class;
    }

    @Override
    public String getDescription() {
        return "TRUE: players cannot teleport into the region; walking in is up to deny-entry.";
    }
}
