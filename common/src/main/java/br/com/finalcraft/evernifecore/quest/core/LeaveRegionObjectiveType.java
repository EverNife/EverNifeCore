package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.quest.RegionObjectiveType;
import br.com.finalcraft.evernifecore.region.event.RegionCrossedEvent;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;

import java.util.List;

/** {@code leave-region}: one per crossing out of a region the filter names (by id, tag or owner). */
public final class LeaveRegionObjectiveType implements RegionObjectiveType<RegionCrossingFilter> {

    @Override
    public String getKey() {
        return "evernifecore:leave-region";
    }

    @Override
    public String getDescription() {
        return "Counts each time the player walks out of a region, by id, tag or owner.";
    }

    @Override
    public List<ParamSpec> getParams() {
        return RegionCrossingFilter.params();
    }

    @Override
    public RegionCrossingFilter parseFilter(ParsedParams params) {
        return RegionCrossingFilter.parse(params, "leave-region");
    }

    @Override
    public long countOf(RegionCrossedEvent event, RegionCrossingFilter filter) {
        return filter.anyOf(event.getLeft()) ? 1 : 0;
    }
}
