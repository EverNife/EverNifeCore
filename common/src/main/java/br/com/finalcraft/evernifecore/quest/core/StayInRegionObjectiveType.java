package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.logic.core.StockRegions;
import br.com.finalcraft.evernifecore.quest.ObjectiveState;
import br.com.finalcraft.evernifecore.quest.RegionObjectiveType;
import br.com.finalcraft.evernifecore.region.event.RegionCrossedEvent;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code stay-in-region}: done once the player stood in a region for {@code duration}. {@code measure: continuous} (the
 * default) counts one stay - leaving starts it over; {@code accumulated} adds every stay up. Measured on each crossing
 * of the player and by the engine's sweep, once a minute; the count is the seconds measured so far.
 */
public final class StayInRegionObjectiveType implements RegionObjectiveType<StayInRegionObjectiveType.Filter> {

    @Override
    public String getKey() {
        return "evernifecore:stay-in-region";
    }

    @Override
    public String getDescription() {
        return "Done once the player stood in a region for a time: in one stay (continuous) or adding stays up (accumulated).";
    }

    @Override
    public List<ParamSpec> getParams() {
        List<ParamSpec> params = new ArrayList<>();
        params.add(ParamSpec.optional("region", ParamSpec.Kind.ID, null, "The region, owner:name; a bare name is the quest plugin's own."));
        params.add(ParamSpec.optional("tag", ParamSpec.Kind.STRING, null, "Any region with this tag."));
        params.add(ParamSpec.required("duration", ParamSpec.Kind.DURATION, "How long inside, e.g. 60s."));
        params.add(ParamSpec.optional("measure", ParamSpec.Kind.STRING, "continuous", "continuous (one stay) or accumulated (every stay added).")
                .oneOf("continuous", "accumulated"));
        return params;
    }

    @Override
    public Filter parseFilter(ParsedParams params) {
        String region = params.getString("region");
        String tag = params.getString("tag");
        if ((region == null) == (tag == null)) {
            throw new TypeRegistryException("stay-in-region names one region or one tag: write {type: stay-in-region, region: theduel:arena,"
                    + " duration: 60s} or {type: stay-in-region, tag: checkpoint, duration: 60s}.");
        }
        StockRegions.Target target = region != null ? StockRegions.Target.of(StockRegions.regionId(params, region)) : StockRegions.Target.tagged(tag);
        return new Filter(target, params.getDuration("duration"),
                "accumulated".equals(params.getString("measure").toLowerCase(Locale.ROOT)));
    }

    @Override
    public long countOf(RegionCrossedEvent event, Filter filter) {
        return 0; //timed: measured by accrue
    }

    @Override
    public boolean isTimed() {
        return true;
    }

    @Override
    public boolean accrue(UUID player, Filter filter, ObjectiveState state, Instant now) {
        long goal = filter.duration.toMillis();
        if (!filter.accumulated) {
            long inside = StockRegions.timeIn(player, filter.target).toMillis();
            state.setCount(inside / 1000);
            return inside >= goal;
        }
        JsonNode data = state.getData();
        long millis = data == null ? 0 : data.path("millis").asLong(0);
        long since = data == null ? 0 : data.path("since").asLong(0);
        if (since > 0) {
            millis += Math.max(0, now.toEpochMilli() - since);
        }
        boolean inside = StockRegions.isIn(player, filter.target);
        ObjectNode next = JsonNodeFactory.instance.objectNode();
        next.put("millis", millis);
        next.put("since", inside ? now.toEpochMilli() : 0);
        state.setData(next);
        state.setCount(millis / 1000);
        return millis >= goal;
    }

    /** The region, the time and how it is measured. */
    public static final class Filter {

        final StockRegions.Target target;
        final Duration duration;
        final boolean accumulated;

        Filter(StockRegions.Target target, Duration duration, boolean accumulated) {
            this.target = target;
            this.duration = duration;
            this.accumulated = accumulated;
        }

        @Override
        public String toString() {
            return target + " for " + duration + (accumulated ? " (accumulated)" : "");
        }
    }
}
