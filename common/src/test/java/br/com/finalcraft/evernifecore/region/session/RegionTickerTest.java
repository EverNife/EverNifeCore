package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionScheduler;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.TestRegions.ManualAgenda;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.TickingFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.region.usage.UsageWatch;
import br.com.finalcraft.evernifecore.eventbus.EventBuses;
import br.com.finalcraft.evernifecore.testing.FailingExceptionHandler;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The region tick exists only while some region needs it, and only visits who stands in such a region. */
@ECoreTest
class RegionTickerTest {

    @TempDir
    Path tempDir;

    /** Records each player it is ticked for. */
    static final class PulseFlag extends RegionFlag<Tristate> implements TickingFlag<Tristate> {
        final List<String> ticked = new CopyOnWriteArrayList<>();

        PulseFlag() {
            super(FlagKey.of(CoreFlags.OWNER, "pulse"), FlagTypes.TRISTATE, Collections.singleton(EventSource.TICK), null);
        }

        @Override
        public Tristate resolveTie(List<Tristate> values) {
            return values.get(0);
        }

        @Override
        public void onTick(SessionContext context, Tristate value) {
            ticked.add(context.getPlayer().getName());
        }
    }

    @Test
    void theTickRunsOnlyWhileARegionUsesItAndVisitsOnlyWhoIsInside() {
        ECPluginData core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        ManualAgenda agenda = new ManualAgenda().install();
        Duration idleTime = EventSourceUsage.get().getIdleTime();
        ECEventBus bus = EventBuses.mirroring(new FailingExceptionHandler());
        RegionMovementTracker tracker = new RegionMovementTracker(RegionLookup.get(), bus);
        PulseFlag pulse = new PulseFlag();
        UsageWatch watch = null;
        try {
            EventSourceUsage.get().setIdleTime(Duration.ZERO);
            FlagRegistry.get().register(core, pulse);
            RegionTicker ticker = RegionTicker.get();
            watch = RegionTicker.start(core);
            assertFalse(ticker.isRunning(), "no region holds a ticking flag yet");
            assertEquals(0, agenda.size(), "so no tick is scheduled");

            Region arena = TestRegions.loaded(core, "arena", "world");
            arena.setFlag(pulse, Tristate.TRUE);
            EventSourceUsage.get().recount();
            assertTrue(ticker.isRunning(), "a region holds pulse");

            TestFPlayerSender inside = new TestFPlayerSender("inside");
            TestFPlayerSender outside = new TestFPlayerSender("outside");
            tracker.onJoin(inside, WorldLocPos.of(5, 5, 5, "world"));
            tracker.onJoin(outside, WorldLocPos.of(50, 5, 5, "world"));
            agenda.runQueued();
            assertEquals(Collections.singletonList("inside"), pulse.ticked, "the player outside every pulse region is never visited");

            TestRegions.unloadAll();
            EventSourceUsage.get().recount();
            agenda.runQueued(); //the idle wait (zero here), and the tick already queued
            assertFalse(ticker.isRunning(), "the last region using it left");
            agenda.runQueued();
            assertEquals(0, agenda.size(), "a stopped tick schedules nothing more");
            assertEquals(1, pulse.ticked.size());
        } finally {
            if (watch != null) {
                watch.stop();
            }
            tracker.stop();
            TestRegions.unloadAll();
            EventSourceUsage.get().setIdleTime(idleTime);
            RegionScheduler.set(null);
            FlagRegistry.get().unregisterAll(core);
            Plugins.forget(Plugins.CORE);
        }
    }
}
