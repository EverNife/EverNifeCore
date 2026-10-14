package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;

/** TRUE: nothing explodes in the region - the whole explosion is cancelled. */
public final class DenyExplosionFlag extends DenyFlag {

    public DenyExplosionFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-explosion"), EventSource.EXPLOSION);
    }

    @Override
    public String getDescription() {
        return "TRUE: nothing explodes in the region - the whole explosion is cancelled. What a permitted explosion breaks is up to"
                + " deny-block-break.";
    }
}
