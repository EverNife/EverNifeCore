package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.MoveContext;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;

import java.util.EnumSet;
import java.util.List;

/**
 * Denies entry and tells the player why, in one value: {@code denied=true message="Boss fight in progress"}. The
 * message goes out at most once per trigger interval of the flag's settings.
 */
public final class DenyEntryWithMessageFlag extends RegionFlag<DenyEntryMessage> {

    public DenyEntryWithMessageFlag(ECPluginData owner) {
        super(FlagKey.of(owner, "deny-entry-with-message"), FlagTypes.jackson("deny-entry-message", DenyEntryMessage.class),
                EnumSet.of(EventSource.MOVE), null);
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
        return value.getDenied() == Tristate.TRUE && StockDenyFlag.crossing(context, MoveContext.Transition.ENTER, null)
                ? Decision.DENY : Decision.PASS;
    }

    @Override
    public void onApplied(ActionContext context, DenyEntryMessage value) {
        FPlayer player = context.getPlayer();
        if (player != null && value.getMessage() != null) {
            player.sendMessage(value.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Denies entry and tells the player why, in one value: denied=true message=\"Boss fight in progress\".";
    }
}
