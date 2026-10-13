package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.MoveContext;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Denies entry and tells the player why, in one value: {@code denied=true message="Boss fight in progress"}. The
 * message goes out at most once per trigger interval of the flag's settings.
 */
public final class DenyEntryWithMessageFlag extends RegionFlag<DenyEntryMessage> {

    public DenyEntryWithMessageFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-entry-with-message"), FlagTypes.jackson("deny-entry-message", DenyEntryMessage.class),
                Collections.singleton(EventSource.MOVE), null);
    }

    /** A value that denies wins; otherwise the first. */
    @Override
    public DenyEntryMessage resolveTie(List<DenyEntryMessage> values) {
        for (DenyEntryMessage value : values) {
            if (value.getDenied() == Tristate.TRUE) {
                return value;
            }
        }
        return values.get(0);
    }

    /** Denies entering, never leaving: a player already inside walks out freely. */
    @Override
    public Decision decide(ActionContext context, DenyEntryMessage value) {
        return value.getDenied() == Tristate.TRUE && DenyFlag.crossing(context, MoveContext.Transition.ENTER, null)
                ? Decision.DENY : Decision.PASS;
    }

    @Override
    public void onApplied(ActionContext context, DenyEntryMessage value) {
        FPlayer player = context.getPlayer();
        if (player != null && value.getMessage() != null) {
            player.sendMessage(value.getMessage());
        }
    }

    /** A denied crossing repeats every tick while the player pushes the border. */
    @Override
    public Duration getDefaultTriggerInterval() {
        return DenyFlag.MOVE_TRIGGER_INTERVAL;
    }

    @Override
    public String getDescription() {
        return "Denies entry and tells the player why, in one value: denied=true message=\"Boss fight in progress\".";
    }
}
