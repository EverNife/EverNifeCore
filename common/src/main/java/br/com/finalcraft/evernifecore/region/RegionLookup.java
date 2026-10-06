package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.RegionIndex.WorldSnapshot;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.ShapeType;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Read-only queries over every indexed region. Every method is safe from any thread and takes no lock: it reads
 * the world's current immutable snapshot, so a concurrent write is either fully visible or not at all.
 *
 * <p>{@code at}, {@code in} and {@code byWorld} come ordered by priority (highest first), then a child before its
 * parent, then id - the order as of the snapshot read, so a priority changed later shows once the region is
 * re-published. {@code byOwner}, {@code byTag} and {@code all} are unordered. Every returned collection is
 * read-only; a query that finds nothing returns the shared empty list.</p>
 */
public final class RegionLookup {

    private static final RegionLookup GLOBAL = new RegionLookup(new RegionIndex());

    private final RegionIndex index;

    RegionLookup(RegionIndex index) {
        this.index = index;
    }

    public static RegionLookup get() {
        return GLOBAL;
    }

    /** The index this lookup reads; writers publish through it. */
    RegionIndex getIndex() {
        return index;
    }

    /** The regions containing the block at these coordinates. */
    public List<Region> at(String world, int x, int y, int z) {
        return at(index.snapshot(world), x, y, z);
    }

    /** The regions containing the point, judged by the block it is in. */
    public List<Region> at(String world, double x, double y, double z) {
        return at(world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public List<Region> at(WorldBlockPos pos) {
        return at(pos.getWorldName(), pos.getX(), pos.getY(), pos.getZ());
    }

    public List<Region> at(WorldLocPos pos) {
        return at(pos.getWorldName(), pos.getX(), pos.getY(), pos.getZ());
    }

    List<Region> at(WorldSnapshot snapshot, int x, int y, int z) {
        if (snapshot == WorldSnapshot.EMPTY) {
            return Collections.emptyList();
        }
        int shift = RegionGridOptions.getCurrent().getChunkShift();
        List<Region> found = collect(snapshot.buckets.get(RegionIndex.chunkKey(x >> shift, z >> shift)), x, y, z, null);
        found = collect(snapshot.huge, x, y, z, found);
        found = collect(snapshot.shapeless, x, y, z, found);
        return ordered(found, snapshot);
    }

    private static List<Region> collect(@Nullable Region[] regions, int x, int y, int z, @Nullable List<Region> found) {
        if (regions != null) {
            for (Region region : regions) {
                if (region.contains(x, y, z)) {
                    if (found == null) {
                        found = new ArrayList<>(4);
                    }
                    found.add(region);
                }
            }
        }
        return found;
    }

    /**
     * The regions of {@code world} that share a block with {@code shape}: exact when both are cuboids, by bounding
     * box otherwise - a polygon or a world-wide region may be reported although its area misses the shape.
     */
    public List<Region> in(String world, Shape shape) {
        WorldSnapshot snapshot = index.snapshot(world);
        if (snapshot == WorldSnapshot.EMPTY) {
            return Collections.emptyList();
        }
        Collection<Region> candidates;
        if (shape.getType() == ShapeType.WORLD || index.isHuge(shape)) {
            candidates = Arrays.asList(snapshot.all);
        } else {
            Set<Region> nearby = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ChunkPos chunk : shape.getChunks()) {
                Region[] bucket = snapshot.buckets.get(RegionIndex.chunkKey(chunk.getX(), chunk.getZ()));
                if (bucket != null) {
                    nearby.addAll(Arrays.asList(bucket));
                }
            }
            nearby.addAll(Arrays.asList(snapshot.huge));
            nearby.addAll(Arrays.asList(snapshot.shapeless));
            candidates = nearby;
        }
        List<Region> found = null;
        for (Region region : candidates) {
            if (region.getShape().intersects(shape)) {
                if (found == null) {
                    found = new ArrayList<>();
                }
                found.add(region);
            }
        }
        return ordered(found, snapshot);
    }

    public @Nullable Region byId(RegionId id) {
        return index.byId(id);
    }

    public Collection<Region> byOwner(ECPluginData owner) {
        List<Region> found = null;
        for (Region region : index.allRegions()) {
            if (region.getOwner() == owner) {
                found = add(found, region);
            }
        }
        return frozen(found);
    }

    public Collection<Region> byTag(String tag) {
        List<Region> found = null;
        for (Region region : index.allRegions()) {
            if (region.getTags().contains(tag)) {
                found = add(found, region);
            }
        }
        return frozen(found);
    }

    public Collection<Region> byWorld(String world) {
        Region[] all = index.snapshot(world).all;
        return all.length == 0 ? Collections.emptyList() : Collections.unmodifiableList(Arrays.asList(all));
    }

    public Collection<Region> all() {
        return frozen(new ArrayList<>(index.allRegions()));
    }

    private static List<Region> add(@Nullable List<Region> found, Region region) {
        List<Region> list = found != null ? found : new ArrayList<>();
        list.add(region);
        return list;
    }

    private static List<Region> frozen(@Nullable List<Region> found) {
        return found == null || found.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(found);
    }

    /** Hits from the bucket, the huge and the shapeless arrays merged into the snapshot's order. */
    private static List<Region> ordered(@Nullable List<Region> found, WorldSnapshot snapshot) {
        if (found != null && found.size() > 1) {
            found.sort((a, b) -> Integer.compare(snapshot.rank.get(a), snapshot.rank.get(b)));
        }
        return frozen(found);
    }
}
