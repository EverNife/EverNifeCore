package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;

/**
 * TRUE: nothing explodes in the region - the whole explosion is cancelled. Hytale fires no event for an explosion as a
 * whole, so there this flag has no effect: what an explosion breaks there is up to {@code deny-block-break}.
 */
public final class DenyExplosionFlag extends DenyFlag {

    public DenyExplosionFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-explosion"), EventSource.EXPLOSION);
    }

    @Override
    public String getDescription() {
        return "TRUE: nothing explodes in the region - the whole explosion is cancelled. What a permitted explosion breaks is up to"
                + " deny-block-break. No effect on Hytale, which reports an explosion only block by block.";
    }
}
