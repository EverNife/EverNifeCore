package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.MoveContext;
import br.com.finalcraft.evernifecore.region.session.MoveCause;

import java.time.Duration;

/** TRUE: players cannot teleport out of the region; walking out is up to {@code deny-exit}. */
public final class DenyTeleportOutFlag extends DenyFlag {

    public DenyTeleportOutFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-teleport-out"), EventSource.MOVE);
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

    /** A denied crossing repeats every tick while the player pushes the border. */
    @Override
    public Duration getDefaultTriggerInterval() {
        return MOVE_TRIGGER_INTERVAL;
    }

    @Override
    public String getDescription() {
        return "TRUE: players cannot teleport out of the region; walking out is up to deny-exit.";
    }
}
