package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.FluidContext;

/**
 * TRUE: water and lava from outside do not flow into the region; inside it they flow as usual. It hears
 * {@link EventSource#FLUID}, the costliest event a server raises: while a region holds this flag, every liquid that
 * spreads anywhere in the world is asked about - define it where it is wanted, not as a default.
 */
public final class DenyFluidFlowFlag extends DenyFlag {

    public DenyFluidFlowFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-fluid-flow"), EventSource.FLUID);
    }

    /** A flow that arrives from a block where this flag is not TRUE: from outside the region, or from one that allows it. */
    @Override
    public boolean applies(ActionContext context) {
        if (!(context instanceof FluidContext)) {
            return false;
        }
        WorldLocPos from = ((FluidContext) context).getFrom();
        return RegionLookup.get().resolve(this, from.getWorldName(), from.getX(), from.getY(), from.getZ(), null) != Tristate.TRUE;
    }

    @Override
    public String getDescription() {
        return "TRUE: water and lava from outside do not flow into the region. Costly: while any region holds it, every liquid that"
                + " spreads in the world is asked about.";
    }
}
