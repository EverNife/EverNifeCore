package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Regions for tests outside this package, where the constructor and the global index are out of reach. Every
 * region {@link #loaded} puts in the global lookup must be {@link #unloadAll unloaded} again by the test.
 */
public final class TestRegions {

    private static final List<Region> LOADED = new ArrayList<>();

    private TestRegions() {
    }

    /** A 0..10 box in {@code world}, not indexed. */
    public static Region create(ECPluginData owner, String name, String world) {
        return create(owner, name, world, CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(10, 10, 10)));
    }

    /**
     * A region of a manager that stores nothing and never attaches it: its setters change the fields and nothing
     * else - no event, no write, no re-index.
     */
    public static Region create(ECPluginData owner, String name, String world, Shape shape) {
        return new Region(new RegionManager(owner, null), name, world, shape, RegionOptions.defaults());
    }

    /** As {@link #create}, and published to {@link RegionLookup#get()}. */
    public static Region loaded(ECPluginData owner, String name, String world) {
        Region region = create(owner, name, world);
        RegionLookup.get().getIndex().add(region);
        LOADED.add(region);
        return region;
    }

    public static void unloadAll() {
        for (Region region : LOADED) {
            RegionLookup.get().getIndex().remove(region);
        }
        LOADED.clear();
    }

    /** A lookup over an index of its own, apart from the global one - with sessions of its own. */
    public static RegionLookup newLookup() {
        return new RegionLookup(new RegionIndex());
    }

    /** Indexes {@code region} in {@code lookup}; its publish listeners hear it. */
    public static void index(RegionLookup lookup, Region region) {
        lookup.getIndex().add(region);
    }

    public static void unindex(RegionLookup lookup, Region region) {
        lookup.getIndex().remove(region);
    }

    /** Gives an indexed region another shape the way a bulk load does: in place, then one rebuild of its world. */
    public static void reshapeAndRebuild(RegionLookup lookup, Region region, Shape shape) {
        region.setShape(shape);
        lookup.getIndex().rebuild(region.getWorld());
    }

    /** A box from (x1, y1, z1) to (x2, y2, z2) in {@code world}, not indexed. */
    public static Region box(ECPluginData owner, String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        return create(owner, name, world, CuboidShape.of(BlockPos.of(x1, y1, z1), BlockPos.of(x2, y2, z2)));
    }

    /** An agenda that runs nothing until told to; {@link #install()} it and {@code RegionScheduler.set(null)} afterwards. */
    public static final class ManualAgenda implements RegionScheduler.Backend {

        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        private final List<Long> delays = new CopyOnWriteArrayList<>();

        public ManualAgenda install() {
            RegionScheduler.set(this);
            return this;
        }

        @Override
        public void scheduleAsync(Runnable task, long delayMillis) {
            tasks.add(task);
            delays.add(delayMillis);
        }

        /** Runs what is queued now; what those tasks queue waits for the next call. */
        public void runQueued() {
            for (int pending = tasks.size(); pending > 0; pending--) {
                tasks.poll().run();
            }
        }

        public int size() {
            return tasks.size();
        }

        /** Drops what is queued without running it. */
        public void discardQueued() {
            tasks.clear();
        }

        /** The delay of every task ever scheduled, in order. */
        public List<Long> getDelays() {
            return delays;
        }
    }

    /** Installs a clock that only moves when told to; {@link #restoreClock()} puts the system clock back. */
    public static ManualClock manualClock() {
        ManualClock clock = new ManualClock(Instant.parse("2026-10-06T12:00:00Z"));
        RegionTime.setClock(clock);
        return clock;
    }

    public static void restoreClock() {
        RegionTime.setClock(null);
    }

    public static final class ManualClock extends Clock {

        private volatile Instant now;

        ManualClock(Instant now) {
            this.now = now;
        }

        public void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
