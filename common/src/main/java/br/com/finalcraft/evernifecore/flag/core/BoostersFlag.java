package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagResolver;
import br.com.finalcraft.evernifecore.flag.FlagResolvers;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.flag.FlagTypes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@code boosters}: what standing in the region (or belonging to a holder that follows the player) boosts. Nothing in
 * the flag system applies it: the booster engine reads {@code RegionLookup.get().resolve(CoreFlags.BOOSTERS, player)}
 * and treats each {@link BoosterEntry} as a booster of its own.
 */
public final class BoostersFlag extends Flag<List<BoosterEntry>> {

    public BoostersFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "boosters"), FlagTypes.BOOSTERS, Collections.singleton(EventSource.NONE), null);
    }

    @Override
    public String getDescription() {
        return "What the region boosts for whoever stands in it: on=<gain> factor=<n> multiplies a gain, on=<gain> bonus=<n> adds to its"
                + " multiplier; entries separated by ','. The booster engine applies them.";
    }

    @Override
    public FlagCategory getCategory() {
        return FlagCategory.DATA;
    }

    /** It follows the player: a holder that follows them may carry it. */
    @Override
    public FlagScope getScope() {
        return FlagScope.ACTOR;
    }

    /** Every layer's boosters count, in layer order. */
    @Override
    public FlagResolver<List<BoosterEntry>> getResolver() {
        return FlagResolvers.concat();
    }

    @Override
    public List<FlagResolver<List<BoosterEntry>>> getAcceptedResolvers() {
        return Arrays.asList(getResolver(), FlagResolvers.firstDefined());
    }
}
