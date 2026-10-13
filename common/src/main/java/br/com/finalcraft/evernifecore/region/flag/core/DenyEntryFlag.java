package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.MoveContext;

import java.time.Duration;

/** TRUE: players cannot get into the region by any means. */
public final class DenyEntryFlag extends DenyFlag {

    public DenyEntryFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-entry"), EventSource.MOVE);
    }

    @Override
    public boolean applies(ActionContext context) {
        return crossing(context, MoveContext.Transition.ENTER, null);
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
        return "TRUE: players cannot get into the region by any means.";
    }
}
