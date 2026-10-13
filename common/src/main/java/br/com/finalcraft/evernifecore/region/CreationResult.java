package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.region.overlap.Overlap;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** What {@link RegionManager#create} did: the new region, or the reason it refused - never an exception for a refusal. */
public final class CreationResult {

    private final @Nullable Region region;
    private final List<Overlap> overlaps;
    private final @Nullable String refusal;

    private CreationResult(@Nullable Region region, List<Overlap> overlaps, @Nullable String refusal) {
        this.region = region;
        this.overlaps = Collections.unmodifiableList(overlaps);
        this.refusal = refusal;
    }

    static CreationResult created(Region region, List<Overlap> overlaps) {
        return new CreationResult(region, overlaps, null);
    }

    static CreationResult refused(String refusal, List<Overlap> overlaps) {
        return new CreationResult(null, overlaps, refusal);
    }

    public boolean isCreated() {
        return region != null;
    }

    /** The new region; null when refused. */
    public @Nullable Region getRegion() {
        return region;
    }

    /** What the shape shares blocks with; empty when the refusal came before the overlaps were looked at. */
    public List<Overlap> getOverlaps() {
        return overlaps;
    }

    /** Why nothing was created, and what to do instead; null when created. */
    public @Nullable String getRefusal() {
        return refusal;
    }

    @Override
    public String toString() {
        return isCreated() ? "created " + region : "refused: " + refusal;
    }
}
