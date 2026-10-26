package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagTypes;

import java.util.Collections;
import java.util.List;

/** The text a player is shown when a {@code deny-*} flag of the region stops them; read by whoever enforces it. */
public final class DenyMessageFlag extends Flag<String> {

    public DenyMessageFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-message"), FlagTypes.TEXT, Collections.singleton(EventSource.NONE), null);
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.DATA;
    }


    @Override
    public String getDescription() {
        return "The text a player is shown when a deny-* flag of the region stops them.";
    }
}
