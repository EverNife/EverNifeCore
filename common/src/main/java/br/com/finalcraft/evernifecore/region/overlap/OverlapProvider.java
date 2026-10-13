package br.com.finalcraft.evernifecore.region.overlap;

import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.shape.Shape;

import java.util.List;

/**
 * Reports areas outside this core - another protection plugin's regions - that a shape would overlap. Registered
 * with {@link OverlapProviders}; asked by {@link RegionManager#dryRun} and every {@code create}.
 */
public interface OverlapProvider {

    OverlapSource getSource();

    /** What in {@code world} shares blocks with {@code shape}; an empty list for nothing. Must not block for long. */
    List<Overlap> findOverlaps(String world, Shape shape);
}
