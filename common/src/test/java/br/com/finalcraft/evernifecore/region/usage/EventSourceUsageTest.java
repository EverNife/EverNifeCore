package br.com.finalcraft.evernifecore.region.usage;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.EventBuses;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionAgenda;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.TestRegions.ManualAgenda;
import br.com.finalcraft.evernifecore.region.event.RegionEnterEvent;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.testing.FailingExceptionHandler;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RecordingAudience;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which event sources the regions need, with the idle delay that keeps a flapping source from churning listeners. */
@ECoreTest
class EventSourceUsageTest {

    private static final String OWNER = "UsageOwner";
    private static final String WORLD = "world";

    @TempDir
    Path tempDir;

    private ECPluginData owner;
    private RegionLookup lookup;
    private ECEventBus bus;
    private ManualAgenda agenda;
    private EventSourceUsage usage;
    private TristateFlag breakFlag;
    private final AtomicInteger needed = new AtomicInteger();
    private final AtomicInteger idle = new AtomicInteger();

    @BeforeEach
    void setUp() {
        owner = Plugins.fakePluginData(OWNER, tempDir.toFile());
        lookup = TestRegions.newLookup();
        bus = EventBuses.mirroring(new FailingExceptionHandler());
        agenda = new ManualAgenda().install();
        breakFlag = new TristateFlag(FlagKey.of(owner, "no-break"), EnumSet.of(EventSource.BREAK), Tristate.FALSE, Tristate.TRUE);
        FlagRegistry.get().register(owner, breakFlag);
        usage = new EventSourceUsage(lookup, bus);
    }

    @AfterEach
    void tearDown() {
        usage.stop();
        RegionAgenda.set(null);
        FlagRegistry.get().unregisterAll(owner);
        Plugins.forget(OWNER);
    }

    private UsageWatch watchBreak() {
        return usage.watch(owner, EventSource.BREAK, needed::incrementAndGet, idle::incrementAndGet);
    }

    private Region breakRegion(String name) {
        Region region = TestRegions.box(owner, name, WORLD, 0, 0, 0, 10, 10, 10);
        region.setFlag(breakFlag, Tristate.TRUE);
        return region;
    }

    @Test
    void aSourceThatComesBackBeforeTheIdleDelayFiresNothing() {
        watchBreak();
        Region region = breakRegion("flapping");

        TestRegions.index(lookup, region);
        assertEquals(1, needed.get());
        TestRegions.unindex(lookup, region);
        assertEquals(0, idle.get(), "unneeded is not idle until the delay passes");
        TestRegions.index(lookup, region);
        agenda.runQueued();

        assertEquals(1, needed.get());
        assertEquals(0, idle.get());
        assertTrue(usage.isNeeded(EventSource.BREAK));
    }

    @Test
    void aSourceUnneededForTheWholeDelayGoesIdleOnce() {
        watchBreak();
        Region region = breakRegion("once");
        TestRegions.index(lookup, region);
        TestRegions.unindex(lookup, region);

        assertEquals(Long.valueOf(EventSourceUsage.DEFAULT_IDLE_TIME.toMillis()), agenda.getDelays().get(0));
        agenda.runQueued();
        agenda.runQueued();

        assertEquals(1, idle.get());
        assertFalse(usage.isNeeded(EventSource.BREAK));
    }

    @Test
    void watchingASourceAlreadyNeededFiresAtOnce() {
        TestRegions.index(lookup, breakRegion("already"));

        UsageWatch watch = watchBreak();

        assertEquals(1, needed.get());
        assertTrue(watch.isNeeded());
        assertEquals(1, usage.getUses(EventSource.BREAK));
    }

    @Test
    void aListenerOfRegionEnterNeedsMoveWithoutAnyRegion() {
        assertFalse(usage.isNeeded(EventSource.MOVE));

        ECEventSubscription<RegionEnterEvent> subscription = bus.subscribe(RegionEnterEvent.class, event -> {
        });
        assertTrue(usage.isNeeded(EventSource.MOVE));
        subscription.unsubscribe();
        agenda.runQueued();

        RecordingAudience nativeSide = new RecordingAudience();
        bus.addNativeAudience(nativeSide);
        nativeSide.setHasListeners(true);
        bus.refreshListenerWatches();
        assertTrue(usage.isNeeded(EventSource.MOVE), "a native listener counts as one");
    }

    @Test
    void aHoldKeepsTheSourceNeededUntilReleased() {
        watchBreak();
        UsageHold hold = usage.acquire(EventSource.BREAK);
        assertEquals(1, needed.get());

        hold.release();
        hold.release();
        agenda.runQueued();

        assertEquals(1, idle.get(), "a second release does nothing");
        assertFalse(usage.isNeeded(EventSource.BREAK));
    }

    @Test
    void aGroupValueCountsAsTheRegionsOwn() {
        Region region = TestRegions.box(owner, "grouped", WORLD, 0, 0, 0, 10, 10, 10);
        region.getGroup("members").setFlag(breakFlag, Tristate.TRUE);
        TestRegions.index(lookup, region);

        assertTrue(usage.isNeeded(EventSource.BREAK));
    }

    @Test
    void registeringAFlagLinksItsRawValuesIntoTheCount() {
        FlagRegistry.get().unregisterAll(owner);
        EventSourceUsage global = new EventSourceUsage(RegionLookup.get(), bus);
        try {
            Region region = TestRegions.loaded(owner, "raw", WORLD);
            region.getFlagStore().putRaw("usageowner:no-break", JsonNodeFactory.instance.objectNode().put("value", "true"));
            global.recount();
            assertFalse(global.isNeeded(EventSource.BREAK), "a raw value of an unknown flag needs nothing");

            FlagRegistry.get().register(owner, breakFlag);

            assertTrue(global.isNeeded(EventSource.BREAK), "registering relinked the raw value, and the count followed");
        } finally {
            global.stop();
            TestRegions.unloadAll();
        }
    }

    @Test
    void noneIsNeverNeededAndAStoppedUsageFiresNothing() {
        TristateFlag dataFlag = new TristateFlag(FlagKey.of(owner, "just-data"), EnumSet.of(EventSource.NONE), Tristate.FALSE, Tristate.TRUE);
        FlagRegistry.get().register(owner, dataFlag);
        Region region = TestRegions.box(owner, "data", WORLD, 0, 0, 0, 10, 10, 10);
        region.setFlag(dataFlag, Tristate.TRUE);
        TestRegions.index(lookup, region);
        assertFalse(usage.isNeeded(EventSource.NONE));

        watchBreak();
        usage.stop();
        TestRegions.index(lookup, breakRegion("after-stop"));

        assertEquals(0, needed.get());
    }
}
