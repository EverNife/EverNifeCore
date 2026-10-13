package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.EventBuses;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionScheduler;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.TestRegions.ManualAgenda;
import br.com.finalcraft.evernifecore.region.TestRegions.ManualClock;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.testing.FailingExceptionHandler;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A tracker over a lookup and a bus of its own, with a held agenda and a clock that moves only when told: regions
 * are boxes along X, from {@code x1} to {@code x2}, 0..10 on Y and Z of world {@value #WORLD}.
 */
@ECoreTest
abstract class TrackerFixture {

    static final String OWNER = "TrackerOwner";
    static final String OTHER = "TrackerOther";
    static final String WORLD = "world";

    @TempDir
    Path tempDir;

    ECPluginData core;
    ECPluginData owner;
    ECPluginData other;
    RegionLookup lookup;
    ECEventBus bus;
    RegionMovementTracker tracker;
    ManualAgenda agenda;
    ManualClock clock;
    TestFPlayerSender player;

    @BeforeEach
    void openTracker() {
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        owner = Plugins.fakePluginData(OWNER, tempDir.resolve("owner").toFile());
        other = Plugins.fakePluginData(OTHER, tempDir.resolve("other").toFile());
        lookup = TestRegions.newLookup();
        bus = EventBuses.mirroring(new FailingExceptionHandler());
        agenda = new ManualAgenda().install();
        clock = TestRegions.manualClock();
        tracker = new RegionMovementTracker(lookup, bus);
        player = new TestFPlayerSender("walker");
    }

    @AfterEach
    void closeTracker() {
        tracker.stop();
        RegionScheduler.set(null);
        TestRegions.restoreClock();
        FlagRegistry.get().unregisterAll(owner);
        FlagRegistry.get().unregisterAll(other);
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(OWNER);
        Plugins.forget(OTHER);
        Plugins.forget(Plugins.CORE);
    }

    <F extends RegionFlag<?>> F registered(F flag) {
        //register under the fake plugin the key names
        String keyOwner = flag.getKey().getOwner();
        ECPluginData by = keyOwner.equals(CoreFlags.OWNER) ? core : keyOwner.equals(OWNER.toLowerCase(Locale.ROOT)) ? owner : other;
        FlagRegistry.get().register(by, (RegionFlag<?>) flag);
        return flag;
    }

    /** An indexed box from {@code x1} to {@code x2}. */
    Region region(String name, int x1, int x2) {
        Region region = TestRegions.box(owner, name, WORLD, x1, 0, 0, x2, 10, 10);
        TestRegions.index(lookup, region);
        return region;
    }

    static WorldLocPos at(double x) {
        return WorldLocPos.of(x, 5, 5.5, WORLD);
    }

    /** Joins at {@code from} and walks to {@code to}. */
    MoveVerdict walk(double from, double to) {
        tracker.onJoin(player, at(from));
        return tracker.onMove(player, at(from), at(to), MoveCause.MOVE);
    }

    <T extends IECEvent> List<T> record(Class<T> type) {
        List<T> seen = new CopyOnWriteArrayList<>();
        bus.subscribe(type, seen::add);
        return seen;
    }

    RegionPlayerSession session() {
        return lookup.getSessionIfPresent(player.getUniqueId());
    }
}
