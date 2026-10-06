package br.com.finalcraft.evernifecore.region;

import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Which overlaps refuse a creation. */
public final class RegionPolicy {

    private static final RegionPolicy ALLOW_ALL = new RegionPolicy(Collections.emptySet());

    private final Set<OverlapSource> refused;

    private RegionPolicy(Set<OverlapSource> refused) {
        this.refused = refused;
    }

    /** Creates whatever the shape overlaps. The default. */
    public static RegionPolicy allowAll() {
        return ALLOW_ALL;
    }

    /** Refuses the creation when the shape touches anything from one of {@code sources}. */
    public static RegionPolicy refuseIfTouches(OverlapSource... sources) {
        return sources.length == 0 ? ALLOW_ALL : new RegionPolicy(Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(sources))));
    }

    /** The reason this policy refuses these overlaps, with the way out; null when it does not. */
    @Nullable String refusal(List<Overlap> overlaps) {
        for (Overlap overlap : overlaps) {
            if (refused.contains(overlap.getSource())) {
                return "The shape touches " + overlap.getId() + " (" + overlap.getSource() + "), and the creation's policy refuses "
                        + refused + " - pick a spot clear of it, or create with a policy that allows " + overlap.getSource() + ".";
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return refused.isEmpty() ? "allowAll" : "refuseIfTouches" + refused;
    }
}
