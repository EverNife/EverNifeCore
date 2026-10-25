package br.com.finalcraft.evernifecore.action.provenance;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.BreakActionEvent;
import br.com.finalcraft.evernifecore.action.event.KillActionEvent;
import br.com.finalcraft.evernifecore.action.event.PickupActionEvent;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.testing.ActionWorld;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tracker records origins only while held, as a subscriber of the action events, and answers them back. */
@ECoreTest
class ProvenanceTrackerTest {

    private static final WorldBlockPos STONE = WorldBlockPos.of(1, 2, 3, "world");

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ActionWorld actions;
    private TestFPlayerSender alice;
    private TestFPlayerSender bob;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        actions = ActionWorld.open("FakeJobs", tempDir);
        alice = Senders.player("alice");
        bob = Senders.player("bob");
    }

    @AfterEach
    void close() {
        ECSettings.PROVENANCE_ALWAYS = false;
        actions.close();
        world.close();
    }

    private void place() {
        actions.post(actions.contexts().place(alice, "world", 1, 2, 3, "minecraft:stone"));
    }

    @Test
    void aPlacedBlockReadsPlayerPlacedByItsPlacerUntilBroken() {
        ProvenanceTracker tracker = actions.provenance();
        try (ProvenanceHold ignored = tracker.record()) {
            place();
            Provenance origin = tracker.getOrigin(STONE).join();
            assertEquals(OriginKind.PLAYER_PLACED, origin.getKind());
            assertEquals(alice.getUniqueId(), origin.getPlayer());
            assertEquals("minecraft:stone", String.valueOf(origin.getKey()));
            assertTrue(origin.isOwn(alice));
            assertFalse(origin.isOwn(bob));

            actions.post(actions.contexts().breakBlock(bob, "world", 1, 2, 3, "minecraft:stone"));
            assertEquals(Provenance.NATURAL, tracker.getOrigin(STONE).join());
        }
    }

    @Test
    void aBreakSubscriberStillReadsTheOriginOfTheBlockBeingBroken() {
        ProvenanceTracker tracker = actions.provenance();
        List<Provenance> seen = new ArrayList<>();
        ECEventSubscription<BreakActionEvent.Post> reader = ECEventBus.global().subscribe(BreakActionEvent.Post.class,
                event -> seen.add(tracker.getOrigin(STONE).join()));
        try (ProvenanceHold ignored = tracker.record()) {
            place();
            actions.post(actions.contexts().breakBlock(bob, "world", 1, 2, 3, "minecraft:stone"));

            assertEquals(1, seen.size());
            assertEquals(OriginKind.PLAYER_PLACED, seen.get(0).getKind());
            assertEquals(alice.getUniqueId(), seen.get(0).getPlayer());
            assertEquals(Provenance.NATURAL, tracker.getOrigin(STONE).join(), "the record goes once every subscriber read it");
        } finally {
            reader.unsubscribe();
        }
    }

    @Test
    void killAndPickupSubscribersStillReadTheOriginOfWhatLeaves() {
        ProvenanceTracker tracker = actions.provenance();
        UUID zombie = UUID.randomUUID();
        UUID diamond = UUID.randomUUID();
        List<OriginKind> seen = new ArrayList<>();
        ECEventSubscription<KillActionEvent.Post> killReader = ECEventBus.global().subscribe(KillActionEvent.Post.class,
                event -> seen.add(tracker.getOrigin(zombie).getKind()));
        ECEventSubscription<PickupActionEvent.Post> pickupReader = ECEventBus.global().subscribe(PickupActionEvent.Post.class,
                event -> seen.add(tracker.getOrigin(diamond).getKind()));
        try (ProvenanceHold ignored = tracker.record()) {
            actions.post(actions.contexts().spawn(zombie, "minecraft:zombie", "SPAWNER", "world"));
            actions.post(actions.contexts().drop(alice, diamond, "minecraft:diamond"));
            actions.post(actions.contexts().kill(alice, zombie, "minecraft:zombie"));
            actions.post(actions.contexts().pickup(bob, diamond, "minecraft:diamond"));

            assertEquals(Arrays.asList(OriginKind.SPAWNER, OriginKind.PLAYER_DROP), seen);
            assertEquals(Provenance.NATURAL, tracker.getOrigin(zombie));
            assertEquals(Provenance.NATURAL, tracker.getOrigin(diamond));
        } finally {
            killReader.unsubscribe();
            pickupReader.unsubscribe();
        }
    }

    @Test
    void aSpawnerSpawnReadsSpawnerAndAnEggEggWhileANaturalSpawnRecordsNothing() {
        ProvenanceTracker tracker = actions.provenance();
        UUID zombie = UUID.randomUUID();
        UUID chicken = UUID.randomUUID();
        UUID wolf = UUID.randomUUID();
        try (ProvenanceHold ignored = tracker.record()) {
            actions.post(actions.contexts().spawn(zombie, "minecraft:zombie", "SPAWNER", "world"));
            actions.post(actions.contexts().spawn(chicken, "minecraft:chicken", "EGG", "world"));
            actions.post(actions.contexts().spawn(wolf, "minecraft:wolf", "NATURAL", "world"));

            assertEquals(OriginKind.SPAWNER, tracker.getOrigin(zombie).getKind());
            assertEquals(OriginKind.EGG, tracker.getOrigin(chicken).getKind());
            assertEquals(Provenance.NATURAL, tracker.getOrigin(wolf));

            actions.post(actions.contexts().kill(alice, zombie, "minecraft:zombie"));
            assertEquals(Provenance.NATURAL, tracker.getOrigin(zombie), "a dead creature is forgotten");
        }
    }

    @Test
    void aDroppedItemReadsPlayerDropAndIsOwnForItsDropper() {
        ProvenanceTracker tracker = actions.provenance();
        UUID diamond = UUID.randomUUID();
        try (ProvenanceHold ignored = tracker.record()) {
            actions.post(actions.contexts().drop(alice, diamond, "minecraft:diamond"));

            Provenance origin = tracker.getOrigin(diamond);
            assertEquals(OriginKind.PLAYER_DROP, origin.getKind());
            assertTrue(origin.isOwn(alice));
            assertFalse(origin.isOwn(bob));

            actions.post(actions.contexts().pickup(bob, diamond, "minecraft:diamond"));
            assertEquals(Provenance.NATURAL, tracker.getOrigin(diamond), "a picked up item is forgotten");
        }
    }

    @Test
    void nothingIsRecordedWhileNobodyHoldsARecording() {
        ProvenanceTracker tracker = actions.provenance();
        ProvenanceHold hold = tracker.record();
        hold.close();
        hold.close(); //twice is once

        place();

        assertFalse(tracker.isRecording());
        assertEquals(Provenance.NATURAL, tracker.getOrigin(STONE).join());
    }

    @Test
    void alwaysRecordsFromOpen() {
        ECSettings.PROVENANCE_ALWAYS = true;
        ProvenanceTracker tracker = actions.provenance();

        place();

        assertTrue(tracker.isRecording());
        assertEquals(OriginKind.PLAYER_PLACED, tracker.getOrigin(STONE).join().getKind());
    }

    @Test
    void anEntryPastRetentionReadsNatural() {
        ProvenanceTracker tracker = actions.provenance();
        try (ProvenanceHold ignored = tracker.record()) {
            place();
            actions.regions().advance(ECSettings.PROVENANCE_RETENTION_PLAYER_PLACED.minus(Duration.ofMinutes(1)));
            assertEquals(OriginKind.PLAYER_PLACED, tracker.getOrigin(STONE).join().getKind());

            actions.regions().advance(Duration.ofMinutes(2));
            assertEquals(Provenance.NATURAL, tracker.getOrigin(STONE).join());
            assertNull(tracker.peekOrigin(STONE), "the expired record was dropped when read");
        }
    }

    @Test
    void subscribingIsWhatSwitchesPlaceOn() {
        ProvenanceTracker tracker = actions.provenance();
        assertFalse(actions.isNeeded(EventSource.PLACE));

        try (ProvenanceHold ignored = tracker.record()) {
            assertTrue(actions.isNeeded(EventSource.PLACE));
            assertTrue(actions.isNeeded(EventSource.SPAWN));
            assertTrue(actions.isNeeded(EventSource.ITEM_DROP));
        }
    }
}
