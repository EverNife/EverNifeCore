package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;

import java.time.Duration;

/** TRUE: blocks in the region cannot be broken. */
public final class DenyBlockBreakFlag extends DenyFlag {

    /** Hytale asks once per hit on the block, so a held click is a denial every swing. */
    static final Duration HIT_TRIGGER_INTERVAL = Duration.ofSeconds(1);

    public DenyBlockBreakFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-block-break"), EventSource.BREAK);
    }

    @Override
    public Duration getDefaultTriggerInterval() {
        return HIT_TRIGGER_INTERVAL;
    }

    @Override
    public String getDescription() {
        return "TRUE: blocks in the region cannot be broken.";
    }
}
