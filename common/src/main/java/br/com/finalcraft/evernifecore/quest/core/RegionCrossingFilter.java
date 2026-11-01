package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.logic.core.StockRegions;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/** The regions an {@code enter-region} or {@code leave-region} objective names: one by id, every one with a tag, or every one of an owner. */
public final class RegionCrossingFilter {

    private final @Nullable StockRegions.Target target;
    private final @Nullable String owner;

    private RegionCrossingFilter(@Nullable StockRegions.Target target, @Nullable String owner) {
        this.target = target;
        this.owner = owner;
    }

    static List<ParamSpec> params() {
        return Arrays.asList(
                ParamSpec.optional("region", ParamSpec.Kind.ID, null, "The region, owner:name; a bare name is the quest plugin's own."),
                ParamSpec.optional("tag", ParamSpec.Kind.STRING, null, "Any region with this tag."),
                ParamSpec.optional("owner", ParamSpec.Kind.STRING, null, "Any region of this plugin."));
    }

    static RegionCrossingFilter parse(ParsedParams params, String type) {
        String region = params.getString("region");
        String tag = params.getString("tag");
        String owner = params.getString("owner");
        int named = (region == null ? 0 : 1) + (tag == null ? 0 : 1) + (owner == null ? 0 : 1);
        if (named != 1) {
            throw new TypeRegistryException(type + " names exactly one of region, tag or owner: write {type: " + type + ", region: theduel:arena},"
                    + " {type: " + type + ", tag: checkpoint} or {type: " + type + ", owner: theduel}.");
        }
        if (owner != null) {
            return new RegionCrossingFilter(null, owner);
        }
        return new RegionCrossingFilter(region != null ? StockRegions.Target.of(StockRegions.regionId(params, region))
                : StockRegions.Target.tagged(tag), null);
    }

    public boolean matches(Region region) {
        return target != null ? target.matches(region) : region.getId().getOwner().equalsIgnoreCase(owner);
    }

    /** Whether any of {@code regions} matches. */
    boolean anyOf(List<Region> regions) {
        for (Region region : regions) {
            if (matches(region)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return target != null ? target.toString() : "owner " + owner;
    }
}
