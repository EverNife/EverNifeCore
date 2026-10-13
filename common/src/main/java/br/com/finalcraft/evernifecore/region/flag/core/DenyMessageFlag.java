package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;

import java.util.EnumSet;
import java.util.List;

/** The text a player is shown when a {@code deny-*} flag of the region stops them; read by whoever enforces it. */
public final class DenyMessageFlag extends RegionFlag<String> {

    public DenyMessageFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "deny-message"), FlagTypes.TEXT, EnumSet.of(EventSource.NONE), null);
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.DATA;
    }

    /** The first. */
    @Override
    public String resolveTie(List<String> values) {
        return values.get(0);
    }

    @Override
    public String getDescription() {
        return "The text a player is shown when a deny-* flag of the region stops them.";
    }
}
