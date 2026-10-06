package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.region.shape.BoundingBox;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.ShapeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Internal: the per-world spatial index behind {@link RegionLookup}. Plugins read through {@link RegionLookup}.
 *
 * <p>Each world has one immutable {@link WorldSnapshot}; a write builds the next one copy-on-write and swaps it
 * in, so readers never lock and never see a half-built index. Writes to the same world are serialized; a write
 * rebuilds only the buckets of the chunks the region touches - every other bucket array is carried over as the
 * very same object.</p>
 */
public final class RegionIndex {

    /** Above this many chunks a region skips the per-chunk buckets and is checked on every lookup instead. */
    public static final int DEFAULT_HUGE_CHUNKS = 4096;

    /** Bucket order: priority desc, then depth desc (a child before its parent), then id. A total order. */
    static final Comparator<Region> ORDER = (a, b) -> {
        int byPriority = Integer.compare(b.getPriority(), a.getPriority());
        if (byPriority != 0) {
            return byPriority;
        }
        int byDepth = Integer.compare(b.getDepth(), a.getDepth());
        return byDepth != 0 ? byDepth : a.getId().compareTo(b.getId());
    };

    static final Region[] NO_REGIONS = new Region[0];

    /**
     * Every writer holds it - a manager around its mutation and the publish that follows, and every write here -
     * so a sort key (priority, depth) never changes under a running publish.
     */
    static final Object WRITE_LOCK = new Object();   // ponytail: one write lock for every manager; per-world locks if region writes ever contend

    private final int hugeChunks;
    //compute() on this map is the per-world write lock; get() is the lock-free read
    private final ConcurrentHashMap<String, WorldSnapshot> worlds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RegionId, Region> byId = new ConcurrentHashMap<>();
    private final List<Consumer<String>> publishListeners = new CopyOnWriteArrayList<>();

    public RegionIndex() {
        this(DEFAULT_HUGE_CHUNKS);
    }

    public RegionIndex(int hugeChunks) {
        this.hugeChunks = hugeChunks;
    }

    /** The current snapshot of {@code world}; {@link WorldSnapshot#EMPTY} when it has never had a region. */
    WorldSnapshot snapshot(String world) {
        WorldSnapshot snapshot = world == null ? null : worlds.get(world);
        return snapshot != null ? snapshot : WorldSnapshot.EMPTY;
    }

    Region byId(RegionId id) {
        return byId.get(id);
    }

    Collection<Region> allRegions() {
        return byId.values();
    }

    /** @throws IllegalArgumentException when a region with the same id is already indexed */
    public void add(Region region) {
        synchronized (WRITE_LOCK) {
            if (byId.putIfAbsent(region.getId(), region) != null) {
                throw new IllegalArgumentException("A region with id " + region.getId() + " is already indexed - remove it first,"
                        + " or replace(region) to re-index the same object.");
            }
            publish(region.getWorld(), null, region);
        }
    }

    /**
     * Indexes many regions with one publish per world - the path for a bulk load, where {@link #add} in a loop
     * would copy every world's buckets once per region.
     *
     * @throws IllegalArgumentException when any id is already indexed; then nothing is added
     */
    public void addAll(Collection<Region> regions) {
        synchronized (WRITE_LOCK) {
            Map<String, List<Region>> byWorld = new HashMap<>();
            Set<RegionId> seen = new HashSet<>();
            for (Region region : regions) {
                if (byId.containsKey(region.getId()) || !seen.add(region.getId())) {
                    throw new IllegalArgumentException("A region with id " + region.getId() + " is already indexed - remove it first,"
                            + " or load each region once.");
                }
                byWorld.computeIfAbsent(region.getWorld(), world -> new ArrayList<>()).add(region);
            }
            for (Region region : regions) {
                byId.put(region.getId(), region);
            }
            byWorld.forEach((world, arriving) -> {
                worlds.compute(world, (name, old) -> {
                    WorldSnapshot base = old != null ? old : WorldSnapshot.EMPTY;
                    Builder builder = new Builder(base);
                    for (Region region : arriving) {
                        builder.place(region);
                    }
                    return builder.build(base.version + 1);
                });
                published(world);
            });
        }
    }

    public void remove(Region region) {
        synchronized (WRITE_LOCK) {
            if (byId.remove(region.getId(), region)) {
                publish(region.getWorld(), region, null);
            }
        }
    }

    /**
     * Re-indexes a region already in the index after its shape, priority, parent or tags changed. Only the buckets it
     * left and the buckets it now touches are rebuilt.
     */
    public void replace(Region region) {
        if (byId.get(region.getId()) != region) {
            throw new IllegalArgumentException("Region " + region.getId() + " is not indexed (or another object holds its id) - add(region) it first.");
        }
        synchronized (WRITE_LOCK) {
            publish(region.getWorld(), region, region);
        }
    }

    /** Rebuilds the whole snapshot of {@code world} from the regions indexed in it. */
    public void rebuild(String world) {
        synchronized (WRITE_LOCK) {
            worlds.compute(world, (name, old) -> {
                WorldSnapshot base = old != null ? old : WorldSnapshot.EMPTY;
                Builder builder = new Builder(WorldSnapshot.EMPTY);
                for (Region region : base.all) {
                    builder.place(region);
                }
                return builder.build(base.version + 1);
            });
            published(world);
        }
    }

    private void publish(String world, Region leaving, Region arriving) {
        worlds.compute(world, (name, old) -> {
            WorldSnapshot base = old != null ? old : WorldSnapshot.EMPTY;
            Builder builder = new Builder(base);
            if (leaving != null) {
                builder.unplace(leaving);
            }
            if (arriving != null) {
                builder.place(arriving);
            }
            return builder.build(base.version + 1);
        });
        published(world);
    }

    void addPublishListener(Consumer<String> listener) {
        publishListeners.add(listener);
    }

    void removePublishListener(Consumer<String> listener) {
        publishListeners.remove(listener);
    }

    /** Called with the write lock held, after the new snapshot is in: a listener only queues work, never writes back. */
    private void published(String world) {
        for (Consumer<String> listener : publishListeners) {
            listener.accept(world);
        }
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** Chunks a box spans on the current grid - an upper bound of what its shape touches, known without iterating. */
    static long boxChunkCount(BoundingBox box) {
        int shift = RegionGridOptions.getCurrent().getChunkShift();
        return ((long) (box.getMaxX() >> shift) - (box.getMinX() >> shift) + 1)
                * ((long) (box.getMaxZ() >> shift) - (box.getMinZ() >> shift) + 1);
    }

    boolean isHuge(Shape shape) {
        return boxChunkCount(shape.getBounds()) > hugeChunks;
    }

    /** One world's index at one moment. Never mutated after construction. */
    static final class WorldSnapshot {

        static final WorldSnapshot EMPTY = new WorldSnapshot(Collections.emptyMap(), Collections.emptyMap(),
                NO_REGIONS, NO_REGIONS, NO_REGIONS, 0);

        final Map<Long, Region[]> buckets;
        /** Where each bucketed region sits, so it can leave exactly the buckets it entered. */
        final Map<Region, long[]> placements;
        final Region[] huge;
        final Region[] shapeless;
        /** Every region of the world, in {@link #ORDER} as of this snapshot. */
        final Region[] all;
        /** Position of each region in {@link #all}: a frozen order readers can sort by while priorities change. */
        final Map<Region, Integer> rank;
        final long version;

        WorldSnapshot(Map<Long, Region[]> buckets, Map<Region, long[]> placements, Region[] huge, Region[] shapeless,
                      Region[] all, long version) {
            this.buckets = buckets;
            this.placements = placements;
            this.huge = huge;
            this.shapeless = shapeless;
            this.all = all;
            this.version = version;
            this.rank = new IdentityHashMap<>(all.length * 2);
            for (int i = 0; i < all.length; i++) {
                rank.put(all[i], i);
            }
        }
    }

    /** Copies a snapshot's structure, applies the changes and freezes the result. */
    private final class Builder {
        private final Map<Long, Region[]> buckets;
        private final Map<Region, long[]> placements;
        private final List<Region> huge;
        private final List<Region> shapeless;
        private final List<Region> all;
        private final Map<Long, List<Region>> touched = new HashMap<>();

        Builder(WorldSnapshot base) {
            buckets = new HashMap<>(base.buckets);
            placements = new IdentityHashMap<>(base.placements);
            huge = new ArrayList<>(Arrays.asList(base.huge));
            shapeless = new ArrayList<>(Arrays.asList(base.shapeless));
            all = new ArrayList<>(Arrays.asList(base.all));
        }

        void unplace(Region region) {
            all.remove(region);
            if (huge.remove(region) || shapeless.remove(region)) {
                return;
            }
            long[] keys = placements.remove(region);
            if (keys != null) {
                for (long key : keys) {
                    bucket(key).remove(region);
                }
            }
        }

        void place(Region region) {
            all.add(region);
            Shape shape = region.getShape();
            if (shape.getType() == ShapeType.WORLD) {
                shapeless.add(region);
            } else if (isHuge(shape)) {
                huge.add(region);
            } else {
                List<Long> keys = new ArrayList<>();
                for (ChunkPos chunk : shape.getChunks()) {
                    long key = chunkKey(chunk.getX(), chunk.getZ());
                    keys.add(key);
                    bucket(key).add(region);
                }
                long[] keyArray = new long[keys.size()];
                for (int i = 0; i < keyArray.length; i++) {
                    keyArray[i] = keys.get(i);
                }
                placements.put(region, keyArray);
            }
        }

        private List<Region> bucket(long key) {
            return touched.computeIfAbsent(key, k -> {
                Region[] current = buckets.get(k);
                return current == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(current));
            });
        }

        WorldSnapshot build(long version) {
            for (Map.Entry<Long, List<Region>> entry : touched.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    buckets.remove(entry.getKey());
                } else {
                    buckets.put(entry.getKey(), sorted(entry.getValue()));
                }
            }
            return new WorldSnapshot(buckets, placements, sorted(huge), sorted(shapeless), sorted(all), version);
        }

        private Region[] sorted(List<Region> regions) {
            if (regions.isEmpty()) {
                return NO_REGIONS;
            }
            Region[] array = regions.toArray(NO_REGIONS);
            Arrays.sort(array, ORDER);
            return array;
        }
    }
}
