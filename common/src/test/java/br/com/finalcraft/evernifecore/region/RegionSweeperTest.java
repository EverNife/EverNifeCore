package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.region.TestRegions.ManualClock;
import br.com.finalcraft.evernifecore.region.event.RegionFlagChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionGroupChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.region.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.GroupChange;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sweeper expires what has a deadline, on the thread of the region's world, and stops when nothing has one. */
class RegionSweeperTest extends ManagedRegionFixture {

    private final Queue<Runnable> agenda = new ArrayDeque<>();
    private ManualClock clock;

    @BeforeEach
    void holdTheAgenda() {
        clock = TestRegions.manualClock();
        RegionScheduler.set((task, delayMillis) -> agenda.add(task));
    }

    /** Runs what the agenda holds now - one sweep, plus whatever writes were queued with it. */
    private void runAgenda() {
        for (int pending = agenda.size(); pending > 0; pending--) {
            agenda.poll().run();
        }
    }

    @Test
    void anExpiredRegionGoesOnTheFirstTickOnItsWorldsThread(TestPlatform platform) {
        List<RegionRemovedEvent> removed = record(RegionRemovedEvent.class);
        Region region = created(manager().create("lease", "lease_world", box(0, 0, 9, 9),
                RegionOptions.defaults().withExpiresAt(clock.instant().plusSeconds(60))));
        assertTrue(RegionSweeper.isRunning(), "a deadline wakes the sweeper");
        platform.reset();

        clock.advance(Duration.ofSeconds(61));
        runAgenda();

        assertNull(manager().get("lease"));
        assertEquals(1, removed.size());
        assertSame(region, removed.get(0).getRegion());
        assertEquals(RegionRemovedEvent.Cause.EXPIRED, removed.get(0).getCause());
        assertEquals(Collections.singletonList("lease_world"), platform.getWorldThreadRuns());
        assertEquals(0, platform.getMainThreadRuns(), "nothing of the sweep goes through the main thread");

        runAgenda();
        assertFalse(RegionSweeper.isRunning(), "nothing left with a deadline: the sweeper stops");
    }

    @Test
    void withNothingToExpireTheSweeperStops() {
        created(manager().create("forever", WORLD, box(0, 0, 9, 9)));

        RegionSweeper.wake();
        assertTrue(RegionSweeper.isRunning());
        runAgenda();

        assertFalse(RegionSweeper.isRunning());
        assertTrue(agenda.isEmpty(), "a stopped sweeper schedules nothing");
    }

    @Test
    void anExpiredTemporaryValueUncoversThePermanentOne() {
        RoomFlag flag = registeredRoomFlag(owner);
        Region region = created(manager().create("room", WORLD, box(0, 0, 9, 9)));
        region.setFlag(flag, Tristate.FALSE);
        region.setFlag(flag, Tristate.TRUE, Duration.ofMinutes(5));
        List<RegionFlagChangedEvent> changes = record(RegionFlagChangedEvent.class);

        clock.advance(Duration.ofMinutes(6));
        runAgenda();

        assertEquals(1, changes.size());
        assertEquals(FlagChangeCause.EXPIRED, changes.get(0).getCause());
        assertEquals(Tristate.TRUE, changes.get(0).getOldValue().getValue());
        assertEquals(Tristate.FALSE, changes.get(0).getNewValue().getValue());
        assertEquals(Tristate.FALSE, region.getFlag(flag));
        assertNull(region.getFlagStore().getNextDeadline());
    }

    @Test
    void anExpiredMembershipIsSweptAsMemberExpired() {
        Region region = created(manager().create("club", WORLD, box(0, 0, 9, 9)));
        AccessGroup members = region.getGroup("members");
        UUID guest = UUID.randomUUID();
        members.addMember(guest, Duration.ofMinutes(1));
        List<RegionGroupChangedEvent> changes = record(RegionGroupChangedEvent.class);

        clock.advance(Duration.ofMinutes(2));
        runAgenda();

        assertEquals(1, changes.size());
        assertEquals(GroupChange.MEMBER_EXPIRED, changes.get(0).getChange());
        assertEquals(guest, changes.get(0).getPlayer());
        assertNull(members.getNextMemberDeadline());
    }
}
