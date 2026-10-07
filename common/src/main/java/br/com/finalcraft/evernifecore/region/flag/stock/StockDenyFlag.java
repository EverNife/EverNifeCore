package br.com.finalcraft.evernifecore.region.flag.stock;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.MoveContext;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import jakarta.annotation.Nullable;

import java.util.EnumSet;

/** The shape of every stock {@code deny-*} flag: default FALSE, TRUE wins a tie, TRUE denies the actions the flag is about. */
abstract class StockDenyFlag extends TristateFlag {

    StockDenyFlag(ECPluginData owner, String name, EventSource source) {
        this(owner, name, source, null);
    }

    StockDenyFlag(ECPluginData owner, String name, EventSource source, @Nullable StockDenyFlag parent) {
        super(FlagKey.of(owner, name), EnumSet.of(source), Tristate.FALSE, Tristate.TRUE, parent);
    }

    /** TRUE denies, for the actions {@link #applies} accepts. */
    @Override
    public final Decision decide(ActionContext context, Tristate value) {
        return value == Tristate.TRUE && applies(context) ? Decision.DENY : Decision.PASS;
    }

    /** A crossing of the region this flag's value comes from, in {@code transition}, optionally only by {@code cause}. */
    static boolean crossing(ActionContext context, MoveContext.Transition transition, @Nullable MoveCause cause) {
        if (!(context instanceof MoveContext)) {
            return false;
        }
        MoveContext move = (MoveContext) context;
        return move.getTransition() == transition && (cause == null || move.getCause() == cause);
    }

    /** Damage to a victim of {@code kind}. */
    static boolean hurts(ActionContext context, DamageContext.Kind kind) {
        return context instanceof DamageContext && ((DamageContext) context).getVictimKind() == kind;
    }
}
