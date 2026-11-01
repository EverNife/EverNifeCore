package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.quest.RegionObjectiveType;
import br.com.finalcraft.evernifecore.region.event.RegionCrossedEvent;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;

import java.util.List;

/** {@code enter-region}: one per crossing into a region the filter names (by id, tag or owner). */
public final class EnterRegionObjectiveType implements RegionObjectiveType<RegionCrossingFilter> {

    @Override
    public String getKey() {
        return "evernifecore:enter-region";
    }

    @Override
    public String getDescription() {
        return "Counts each time the player walks into a region, by id, tag or owner.";
    }

    @Override
    public List<ParamSpec> getParams() {
        return RegionCrossingFilter.params();
    }

    @Override
    public RegionCrossingFilter parseFilter(ParsedParams params) {
        return RegionCrossingFilter.parse(params, "enter-region");
    }

    @Override
    public long countOf(RegionCrossedEvent event, RegionCrossingFilter filter) {
        return filter.anyOf(event.getEntered()) ? 1 : 0;
    }
}
