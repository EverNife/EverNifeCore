package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.ProtectionFlagGate;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The decisions about one platform event asked in stages - an explosion as a whole, then each block it breaks, with
 * the same actor. The native handles of every context decided stay open until {@link #close()}, which the event's
 * handler calls once the event is done ({@link RegionLookup#decide} is a dispatch of one).
 */
public final class RegionDispatch implements AutoCloseable {

    private final RegionLookup lookup;
    private final List<ActionContext> decided = new ArrayList<>(2);

    RegionDispatch(RegionLookup lookup) {
        this.lookup = lookup;
    }

    /** As {@link RegionLookup#decide}, keeping the context's handles open until {@link #close()}. */
    public Decision decide(ActionContext context) {
        decided.add(context);
        return ProtectionFlagGate.decide(lookup, context);
    }

    /** Closes the native handles of every context decided; using one afterwards throws. */
    @Override
    public void close() {
        for (ActionContext context : decided) {
            context.close();
        }
        decided.clear();
    }
}
