package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.event.RegionCreatedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRedefinedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Creating, refusing, overlapping, removing, redefining and finding regions through their owner's manager. */
class RegionManagerTest extends ManagedRegionFixture {

    @Test
    void createIndexesTheRegionWithItsOptionsAndPostsCreated() {
        List<RegionCreatedEvent> createdEvents = record(RegionCreatedEvent.class);
        Instant deadline = Instant.parse("2030-01-01T00:00:00Z");

        Region arena = created(manager().create("Arena", WORLD, box(0, 0, 9, 9), RegionOptions.defaults()
                .withPriority(5).withTags("pvp").withExpiresAt(deadline)
                .withMetadata(JsonNodeFactory.instance.objectNode().put("kind", "duel"))));

        assertEquals("regionowner:arena", arena.getId().toString());
        assertEquals(Collections.singletonList(arena), RegionLookup.get().at(WORLD, 5, 50, 5));
        assertEquals(5, arena.getPriority());
        assertEquals(Collections.singleton("pvp"), arena.getTags());
        assertEquals(deadline, arena.getExpiresAt());
        assertEquals("duel", arena.getMetadata().get("kind").asText());
        assertEquals(Persistence.PERSISTENT, arena.getPersistence());
        assertSame(manager(), arena.getManager());
        assertEquals(1, createdEvents.size());
        assertSame(arena, createdEvents.get(0).getRegion());
    }

    @Test
    void aNameInUseAnInvalidNameAndARefusingPolicyAreRefusalsNotExceptions() {
        RegionManager manager = manager();
        created(manager.create("arena", WORLD, box(0, 0, 9, 9)));

        CreationResult duplicate = manager.create("ARENA", WORLD, box(50, 50, 60, 60));
        assertFalse(duplicate.isCreated());
        assertTrue(duplicate.getRefusal().contains("already exists"), duplicate.getRefusal());

        CreationResult invalid = manager.create("not a name!", WORLD, box(50, 50, 60, 60));
        assertFalse(invalid.isCreated());
        assertTrue(invalid.getRefusal().contains("Invalid region name"), invalid.getRefusal());

        CreationResult touching = manager.create("neighbour", WORLD, box(5, 5, 20, 20),
                RegionOptions.defaults().withPolicy(RegionPolicy.refuseIfTouches(OverlapSource.OURS_SAME_OWNER)));
        assertFalse(touching.isCreated());
        assertTrue(touching.getRefusal().contains("regionowner:arena"), touching.getRefusal());
        assertEquals(1, touching.getOverlaps().size());
        assertNull(manager.get("neighbour"));

        assertTrue(manager.create("neighbour", WORLD, box(5, 5, 20, 20)).isCreated(), "allowAll is the default policy");
    }

    @Test
    void aParentOfAnotherOwnerOrWorldIsARefusal() {
        Region foreign = created(RegionManager.of(other, otherStorage).create("theirs", WORLD, box(0, 0, 9, 9)));
        Region nether = created(manager().create("nether_home", "nether", box(0, 0, 9, 9)));

        CreationResult ofAnotherOwner = manager().create("child", WORLD, box(1, 1, 2, 2), RegionOptions.defaults().withParent(foreign));
        CreationResult ofAnotherWorld = manager().create("child", WORLD, box(1, 1, 2, 2), RegionOptions.defaults().withParent(nether));

        assertFalse(ofAnotherOwner.isCreated());
        assertTrue(ofAnotherOwner.getRefusal().contains("same owner"), ofAnotherOwner.getRefusal());
        assertFalse(ofAnotherWorld.isCreated());
        assertTrue(ofAnotherWorld.getRefusal().contains("same world"), ofAnotherWorld.getRefusal());
    }

    @Test
    void dryRunReportsOurRegionsOfBothOwnersAndEveryProvider() {
        created(manager().create("mine", WORLD, box(0, 0, 9, 9), RegionOptions.defaults().withPriority(3)));
        created(RegionManager.of(other, otherStorage).create("theirs", WORLD, box(5, 5, 15, 15)));
        OverlapProviders.register(owner, new OverlapProvider() {
            @Override
            public OverlapSource getSource() {
                return OverlapSource.WORLDGUARD;
            }

            @Override
            public List<Overlap> findOverlaps(String world, Shape shape) {
                return Collections.singletonList(new Overlap(OverlapSource.WORLDGUARD, "__global__", null, 0, false));
            }
        });

        List<Overlap> overlaps = manager().dryRun(WORLD, box(6, 6, 7, 7));

        List<OverlapSource> sources = new ArrayList<>();
        for (Overlap overlap : overlaps) {
            sources.add(overlap.getSource());
        }
        assertEquals(new HashSet<>(Arrays.asList(OverlapSource.OURS_SAME_OWNER, OverlapSource.OURS_OTHER_OWNER, OverlapSource.WORLDGUARD)),
                new HashSet<>(sources));
        Overlap mine = overlaps.get(sources.indexOf(OverlapSource.OURS_SAME_OWNER));
        assertEquals("regionowner:mine", mine.getId());
        assertTrue(mine.isAbove(), "priority 3 is above the dry run's 0");
        assertTrue(manager().getAll().size() == 1, "a dry run creates nothing");
    }

    @Test
    void removeRefusesAParentAndHonoursBothStrategies() {
        RegionManager manager = manager();
        List<RegionRemovedEvent> removedEvents = record(RegionRemovedEvent.class);
        Region parent = created(manager.create("parent", WORLD, box(0, 0, 20, 20)));
        Region child = created(manager.create("child", WORLD, box(1, 1, 5, 5), RegionOptions.defaults().withParent(parent)));
        Region grandchild = created(manager.create("grandchild", WORLD, box(2, 2, 3, 3), RegionOptions.defaults().withParent(child)));

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> manager.remove(parent));
        assertTrue(refused.getMessage().contains("ORPHAN_CHILDREN") && refused.getMessage().contains("CASCADE"), refused.getMessage());

        manager.remove(child, RemoveStrategy.ORPHAN_CHILDREN);
        assertNull(manager.get("child"));
        assertNull(grandchild.getParent(), "the orphan keeps living, with no parent");
        assertSame(grandchild, manager.get("grandchild"));

        grandchild.setParent(parent);
        manager.remove(parent, RemoveStrategy.CASCADE);
        assertTrue(manager.getAll().isEmpty());
        assertEquals(3, removedEvents.size());
        for (RegionRemovedEvent event : removedEvents) {
            assertEquals(RegionRemovedEvent.Cause.API, event.getCause());
        }
        assertSame(grandchild, removedEvents.get(1).getRegion(), "a cascade removes the deepest first");
    }

    @Test
    void redefineMovesTheShapeTheLookupSeesAndPostsBothShapes() {
        List<RegionRedefinedEvent> redefined = record(RegionRedefinedEvent.class);
        Region region = created(manager().create("moving", WORLD, box(0, 0, 9, 9)));

        manager().redefine(region, box(100, 100, 109, 109));

        assertTrue(RegionLookup.get().at(WORLD, 5, 50, 5).isEmpty());
        assertEquals(Collections.singletonList(region), RegionLookup.get().at(WORLD, 105, 50, 105));
        assertEquals(1, redefined.size());
        assertEquals(box(0, 0, 9, 9), redefined.get(0).getOldShape());
        assertEquals(box(100, 100, 109, 109), redefined.get(0).getNewShape());
    }

    @Test
    void queriesSeeOnlyTheOwnersRegions() {
        Region mine = created(manager().create("mine", WORLD, box(0, 0, 9, 9), RegionOptions.defaults().withTags("shop")));
        Region nether = created(manager().create("hell", "nether", box(0, 0, 9, 9)));
        created(RegionManager.of(other, otherStorage).create("theirs", WORLD, box(0, 0, 9, 9), RegionOptions.defaults().withTags("shop")));

        assertSame(mine, manager().get("MINE"));
        assertNull(manager().get("theirs"));
        assertNull(manager().get("no such name!"));
        assertEquals(new HashSet<>(Arrays.asList(mine, nether)), new HashSet<>(manager().getAll()));
        assertEquals(Collections.singletonList(mine), new ArrayList<>(manager().getByWorld(WORLD)));
        assertEquals(Collections.singletonList(mine), new ArrayList<>(manager().getByTag("shop")));
    }

    @Test
    void ofIsMemoizedPerOwnerAndRefusesASecondStorage() {
        RegionManager first = RegionManager.of(owner, storage);

        assertSame(first, RegionManager.of(owner, storage));
        assertSame(first, RegionManager.ofIfPresent(owner));
        IllegalStateException otherStorageRefused = assertThrows(IllegalStateException.class, () -> RegionManager.of(owner, otherStorage));
        assertTrue(otherStorageRefused.getMessage().contains("close() it"), otherStorageRefused.getMessage());
        IllegalStateException sharedRefused = assertThrows(IllegalStateException.class, () -> RegionManager.of(other, storage));
        assertTrue(sharedRefused.getMessage().contains("its own storage"), sharedRefused.getMessage());

        first.close().join();
        assertTrue(first.isClosed());
        assertNull(RegionManager.ofIfPresent(owner));
        RegionManager second = RegionManager.of(owner, otherStorage);
        assertFalse(second == first);
    }

    @Test
    void playersInsideAreTheOnlinePlayersStandingInTheShape() {
        TestFPlayerSender inside = new TestFPlayerSender("inside").at(WORLD, 5, 50, 5);
        TestFPlayerSender outside = new TestFPlayerSender("outside").at(WORLD, 50, 50, 50);
        TestFPlayerSender elsewhere = new TestFPlayerSender("elsewhere").at("nether", 5, 50, 5);
        Region region = created(manager().create("room", WORLD, box(0, 0, 9, 9)));

        try (ECoreTestWorld ignored = Platforms.lenient().onlinePlayers(Arrays.<FPlayer>asList(inside, outside, elsewhere)).install()) {
            assertEquals(Collections.singletonList(inside), region.getPlayersInside());
        }
    }

    @Test
    void foreignAccessIsAllowedUntilTheOwnerSaysOtherwise() {
        assertTrue(manager().isForeignAccessAllowed());
        manager().setForeignAccess(false);
        assertFalse(manager().isForeignAccessAllowed());
    }

    @Test
    void anyThreadMayCreateAndTheEventComesOutOnIt() throws InterruptedException {
        List<RegionCreatedEvent> createdEvents = record(RegionCreatedEvent.class);
        AtomicReference<Thread> eventThread = new AtomicReference<>();
        onEvent(RegionCreatedEvent.class, event -> eventThread.set(Thread.currentThread()));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                created(manager().create("async", WORLD, box(0, 0, 9, 9)));
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        }, "region-test-worker");

        worker.start();
        worker.join();

        assertNull(failure.get());
        assertEquals(1, createdEvents.size());
        assertSame(worker, eventThread.get());
    }
}
