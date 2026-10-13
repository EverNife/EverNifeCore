package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionScheduler;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionTime;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One owner's regions for a test: created through the real manager, gone from the index once closed. */
@ECoreTest
class RegionWorldTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void opensCreatesAndClosesOutOfTheIndex() {
        Region arena;
        try (RegionWorld prot = RegionWorld.open("RegionWorldProt", tempDir)) {
            arena = prot.cuboid("arena", "world", 0, 0, 0, 10, 10, 10);

            assertTrue(prot.getLookup().at("world", 5, 5, 5).contains(arena));
            assertTrue(ECPluginManager.getECPluginsMap().containsKey("RegionWorldProt"));
        }

        assertFalse(RegionLookup.get().at("world", 5, 5, 5).contains(arena), "closing takes the owner's regions out");
        assertFalse(ECPluginManager.getECPluginsMap().containsKey("RegionWorldProt"),
                "closing forgets the plugin");
    }

    @Test
    void theClockMovesOnlyWhenToldAndRunDueRunsWhatItReached() {
        try (RegionWorld prot = RegionWorld.open("RegionWorldClock", tempDir)) {
            Instant start = RegionTime.now();
            AtomicInteger ran = new AtomicInteger();
            RegionScheduler.scheduleAsync(ran::incrementAndGet, 1000);

            assertEquals(start, RegionTime.now());
            prot.runDue();
            assertEquals(0, ran.get(), "not due yet");

            prot.advance(Duration.ofSeconds(1));
            assertEquals(start.plusSeconds(1), RegionTime.now());
            prot.runDue();
            assertEquals(1, ran.get());
        }
    }
}
