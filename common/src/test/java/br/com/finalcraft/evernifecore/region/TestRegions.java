package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

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
        return new Region(owner, name, world, CuboidShape.of(BlockPos.of(0, 0, 0), BlockPos.of(10, 10, 10)));
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
