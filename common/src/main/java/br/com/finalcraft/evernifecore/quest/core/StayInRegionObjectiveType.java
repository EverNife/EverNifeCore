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
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code stay-in-region}: done once the player stood in a region for {@code duration}. {@code measure: continuous} (the
 * default) counts one stay - leaving starts it over; {@code accumulated} adds every stay up. A group's quest reads
 * {@code presence}: {@code all} (the default) is inside while every online member is, {@code any} while one is; no member
 * online is outside. Measured on each crossing and at the moment the duration would end; the engine's sweep, once a
 * minute, is only the safety net. The count is the seconds measured so far.
 */
public final class StayInRegionObjectiveType implements RegionObjectiveType<StayInRegionObjectiveType.Filter> {

    @Override
    public String getKey() {
        return "evernifecore:stay-in-region";
    }

    @Override
    public String getDescription() {
        return "Done once the player stood in a region for a time: in one stay (continuous) or adding stays up (accumulated)."
                + " A group's quest counts while every online member is inside (presence: all) or one is (any); a player's ignores presence.";
    }

    @Override
    public List<ParamSpec> getParams() {
        List<ParamSpec> params = new ArrayList<>();
        params.add(ParamSpec.optional("region", ParamSpec.Kind.ID, null, "The region, owner:name; a bare name is the quest plugin's own."));
        params.add(ParamSpec.optional("tag", ParamSpec.Kind.STRING, null, "Any region with this tag."));
        params.add(ParamSpec.required("duration", ParamSpec.Kind.DURATION, "How long inside, e.g. 60s."));
        params.add(ParamSpec.optional("measure", ParamSpec.Kind.STRING, "continuous", "continuous (one stay) or accumulated (every stay added).")
                .oneOf("continuous", "accumulated"));
        params.add(ParamSpec.optional("presence", ParamSpec.Kind.STRING, "all", "A group's quest: all (every online member inside) or any (one is);"
                        + " a player's quest ignores it.")
                .oneOf("all", "any"));
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
                "accumulated".equals(params.getString("measure").toLowerCase(Locale.ROOT)),
                "all".equals(params.getString("presence").toLowerCase(Locale.ROOT)));
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
    public boolean accrue(Collection<UUID> players, Filter filter, ObjectiveState state, Instant now) {
        long goal = filter.duration.toMillis();
        Duration stay = stayOf(players, filter);
        if (!filter.accumulated) {
            long inside = stay == null ? 0 : stay.toMillis();
            state.setCount(inside / 1000);
            return inside >= goal;
        }
        long millis = accumulated(state, now);
        ObjectNode next = JsonNodeFactory.instance.objectNode();
        next.put("millis", millis);
        next.put("since", stay != null ? now.toEpochMilli() : 0);
        state.setData(next);
        state.setCount(millis / 1000);
        return millis >= goal;
    }

    @Override
    public @Nullable Duration remaining(Collection<UUID> players, Filter filter, ObjectiveState state, Instant now) {
        Duration stay = stayOf(players, filter);
        if (stay == null) {
            return null;
        }
        long measured = filter.accumulated ? accumulated(state, now) : stay.toMillis();
        return Duration.ofMillis(Math.max(0, filter.duration.toMillis() - measured));
    }

    /**
     * How long {@code players} have met the presence: {@code all} is the shortest stay among them, {@code any} the longest;
     * null when they do not meet it (nobody to measure included).
     */
    private static @Nullable Duration stayOf(Collection<UUID> players, Filter filter) {
        //ponytail: any = the longest current stay, so a hand-off between members starts a continuous stay over; keep the
        //start in the objective's data if a hand-off ever has to count
        Duration met = null;
        for (UUID player : players) {
            if (!StockRegions.isIn(player, filter.target)) {
                if (filter.everyone) {
                    return null;
                }
                continue;
            }
            Duration inside = StockRegions.timeIn(player, filter.target);
            if (met == null || (filter.everyone ? inside.compareTo(met) < 0 : inside.compareTo(met) > 0)) {
                met = inside;
            }
        }
        return met;
    }

    /** The accumulated millis up to {@code now}: the closed stays plus the open one. */
    private static long accumulated(ObjectiveState state, Instant now) {
        JsonNode data = state.getData();
        long millis = data == null ? 0 : data.path("millis").asLong(0);
        long since = data == null ? 0 : data.path("since").asLong(0);
        return since > 0 ? millis + Math.max(0, now.toEpochMilli() - since) : millis;
    }

    /** The region, the time, how it is measured and whose presence counts. */
    public static final class Filter {

        final StockRegions.Target target;
        final Duration duration;
        final boolean accumulated;
        /** presence: all - every measured player inside; false is any. */
        final boolean everyone;

        Filter(StockRegions.Target target, Duration duration, boolean accumulated, boolean everyone) {
            this.target = target;
            this.duration = duration;
            this.accumulated = accumulated;
            this.everyone = everyone;
        }

        @Override
        public String toString() {
            return target + " for " + duration + (accumulated ? " (accumulated)" : "") + (everyone ? "" : " (any member)");
        }
    }
}
