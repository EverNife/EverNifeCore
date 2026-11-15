package br.com.finalcraft.evernifecore.action.provenance;

import br.com.finalcraft.evernifecore.action.event.BreakActionEvent;
import br.com.finalcraft.evernifecore.action.event.DropActionEvent;
import br.com.finalcraft.evernifecore.action.event.KillActionEvent;
import br.com.finalcraft.evernifecore.action.event.PickupActionEvent;
import br.com.finalcraft.evernifecore.action.event.PlaceActionEvent;
import br.com.finalcraft.evernifecore.action.event.SpawnActionEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.config.plugin.ProvenanceSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.testing.ActionWorld;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one provenance record: opened by the first plugin that asks, kept by the longest retention, closed by the last to leave. */
@ECoreTest
class ProvenanceRegistryTest {

    private static final WorldBlockPos STONE = WorldBlockPos.of(1, 2, 3, "world");
    private static final List<Class<? extends IECEvent>> RECORDED = Arrays.asList(PlaceActionEvent.Post.class,
            SpawnActionEvent.Post.class, DropActionEvent.Post.class, BreakActionEvent.Post.class, KillActionEvent.Post.class,
            PickupActionEvent.Post.class);

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ActionWorld jobs;
    private ECPluginData blocks;
    private TestFPlayerSender alice;
    private List<Integer> before;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        jobs = ActionWorld.open("FakeJobs", tempDir.resolve("jobs"));
        blocks = Plugins.fakePluginData("FakeLimitedBlocks", tempDir.resolve("blocks").toFile());
        alice = Senders.player("alice");
        before = subscribers();
    }

    @AfterEach
    void close() {
        ProvenanceTracker.closeOwnedBy(blocks);
        Plugins.forget("FakeLimitedBlocks");
        jobs.close();
        world.close();
    }

    private static List<Integer> subscribers() {
        List<Integer> counts = new ArrayList<>();
        for (Class<? extends IECEvent> type : RECORDED) {
            counts.add(ECEventBus.global().getSubscriptions(type).size());
        }
        return counts;
    }

    private static List<Integer> plusOne(List<Integer> counts) {
        List<Integer> more = new ArrayList<>();
        counts.forEach(count -> more.add(count + 1));
        return more;
    }

    private static ProvenanceSettings keeping(Duration retention) {
        ProvenanceSettings settings = new ProvenanceSettings();
        settings.getRetention().setPlayerPlaced(retention);
        return settings;
    }

    private void place() {
        jobs.post(jobs.contexts().place(alice, "world", 1, 2, 3, "minecraft:stone"));
    }

    @Test
    void withNoOpenThereIsNoRecordAndNoActionEventIsSubscribed() {
        assertFalse(ProvenanceTracker.isOpen());
        assertNull(ProvenanceTracker.ofIfPresent(jobs.getOwner()));

        ProvenanceHold recording = jobs.provenance().record();
        assertEquals(plusOne(before), subscribers(), "one subscriber per action event while recording");
        recording.close();

        assertEquals(before, subscribers());
    }

    @Test
    void twoPluginsReadTheSameRecord() {
        try (ProvenanceHold ignored = jobs.provenance().record()) {
            place();
        }

        ProvenanceTracker other = ProvenanceTracker.open(blocks, new ProvenanceSettings());

        assertEquals(OriginKind.PLAYER_PLACED, other.getOrigin(STONE).join().getKind());
        assertEquals(alice.getUniqueId(), other.getOrigin(STONE).join().getPlayer());
    }

    @Test
    void theRetentionIsTheLongestOpenAndFallsWhenItsPluginLeaves() {
        ProvenanceTracker mine = jobs.provenance(keeping(Duration.ofDays(30)));
        ProvenanceTracker.open(blocks, keeping(Duration.ofDays(3650)));
        assertEquals(Duration.ofDays(3650), ProvenanceTracker.getRetention());

        try (ProvenanceHold ignored = mine.record()) {
            place();
        }
        jobs.regions().advance(Duration.ofDays(40));
        assertEquals(OriginKind.PLAYER_PLACED, mine.peekOrigin(STONE).getKind(), "kept by the longest retention open");

        ProvenanceTracker.closeOwnedBy(blocks);

        assertEquals(Duration.ofDays(30), ProvenanceTracker.getRetention());
        assertEquals(Provenance.NATURAL, mine.getOrigin(STONE).join(), "past the 30 days left, it goes on its next read");
    }

    @Test
    void theLastToLeaveClosesTheRecordAndItsSubscriptions() {
        ProvenanceTracker mine = jobs.provenance();
        ProvenanceTracker theirs = ProvenanceTracker.open(blocks, new ProvenanceSettings());
        mine.record();
        theirs.record();

        ProvenanceTracker.closeOwnedBy(jobs.getOwner());
        assertTrue(ProvenanceTracker.isOpen());
        assertTrue(theirs.isRecording());
        assertEquals(plusOne(before), subscribers());

        ProvenanceTracker.closeOwnedBy(blocks);

        assertFalse(ProvenanceTracker.isOpen());
        assertEquals(before, subscribers());
    }

    @Test
    void aPluginRecordingFromItsEnableRecordsAPlacedBlockAndItsLeavingStopsTheRecordingWhenNobodyElseRecords() {
        ProvenanceTracker mine = jobs.provenance();
        mine.record();   //in the enable: never closed by hand
        ProvenanceTracker theirs = ProvenanceTracker.open(blocks, new ProvenanceSettings());

        place();
        assertEquals(OriginKind.PLAYER_PLACED, theirs.getOrigin(STONE).join().getKind());

        ProvenanceTracker.closeOwnedBy(jobs.getOwner());

        assertTrue(ProvenanceTracker.isOpen(), "the other plugin still has it open");
        assertFalse(theirs.isRecording(), "nobody records now");
        assertEquals(before, subscribers());
    }
}
