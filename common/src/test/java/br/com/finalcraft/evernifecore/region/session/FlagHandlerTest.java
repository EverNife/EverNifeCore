package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.Resolution;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.flag.BlockContext;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagOutcome;
import br.com.finalcraft.evernifecore.region.flag.FlagOutcomeContext;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyBlockBreakFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyEntryFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyEntryMessage;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyEntryWithMessageFlag;
import br.com.finalcraft.evernifecore.region.flag.stock.MoveDenySettings;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A plugin's code for a flag in a region: the way back it picks wins, it hears what a group lifted, and it never outlives its flag or owner. */
class FlagHandlerTest extends TrackerFixture {

    private static final WorldLocPos STANDS = WorldLocPos.of(0, 80, 0, WORLD);
    private static final WorldLocPos REGION_SPOT = WorldLocPos.of(1, 81, 1, WORLD);
    private static final WorldLocPos SERVER_SPOT = WorldLocPos.of(2, 82, 2, WORLD);

    private final List<FlagOutcomeContext> heard = new CopyOnWriteArrayList<>();

    private DenyEntryFlag denyEntry() {
        return registered(new DenyEntryFlag(owner));
    }

    private static MoveDenySettings revertingTo(WorldLocPos point) {
        MoveDenySettings settings = new MoveDenySettings();
        settings.setRevert(RevertStrategy.fixed(point));
        return settings;
    }

    @Test
    void theWayBackIsTheHandlersThenTheRegionsThenTheServersThenTheLastBlock() {
        DenyEntryFlag denyEntry = denyEntry();
        Region arena = region("arena", 0, 10);
        arena.setFlag(denyEntry, Tristate.TRUE);
        tracker.onJoin(player, at(12.5));

        assertEquals(at(12.5), tracker.onMove(player, at(12.5), at(10.5), MoveCause.MOVE).getRevertTo(), "nothing set: the last block");

        ((MoveDenySettings) denyEntry.getSettings()).setRevert(RevertStrategy.fixed(SERVER_SPOT));
        assertEquals(SERVER_SPOT, tracker.onMove(player, at(12.5), at(10.5), MoveCause.MOVE).getRevertTo(), "the server's settings");

        arena.setFlagSettings(denyEntry, revertingTo(REGION_SPOT));
        assertEquals(REGION_SPOT, tracker.onMove(player, at(12.5), at(10.5), MoveCause.MOVE).getRevertTo(), "the region's override");

        arena.setFlagHandler(other, denyEntry, context -> context.revertTo(STANDS));
        assertEquals(STANDS, tracker.onMove(player, at(12.5), at(10.5), MoveCause.MOVE).getRevertTo(), "the handler");
    }

    @Test
    void aHandlerThatPicksNothingFallsToTheNextLayerAndAllowLetsThePlayerIn() {
        DenyEntryFlag denyEntry = denyEntry();
        Region arena = region("arena", 0, 10);
        arena.setFlag(denyEntry, Tristate.TRUE);
        arena.setFlagSettings(denyEntry, revertingTo(REGION_SPOT));
        arena.setFlagHandler(other, denyEntry, heard::add);
        tracker.onJoin(player, at(12.5));

        assertEquals(REGION_SPOT, tracker.onMove(player, at(12.5), at(10.5), MoveCause.MOVE).getRevertTo());
        assertEquals(FlagOutcome.DENIED, heard.get(0).getOutcome());
        assertEquals(1, heard.get(0).getConsecutiveDenials());

        arena.setFlagHandler(other, denyEntry, FlagOutcomeContext::allow);
        assertTrue(tracker.onMove(player, at(12.5), at(10.5), MoveCause.MOVE).isAllowed());
        assertTrue(session().getCurrent().contains(arena), "allowed by the handler, the move took effect");
    }

    @Test
    void anInheritedDenialReachesTheHandlerOfTheParent() {
        DenyEntryFlag denyEntry = denyEntry();
        Region parent = region("parent", 50, 60);
        Region child = region("child", 0, 10);
        child.setParent(parent);
        parent.setFlag(denyEntry, Tristate.TRUE);
        parent.setFlagHandler(other, denyEntry, heard::add);
        tracker.onJoin(player, at(12.5));

        tracker.onMove(player, at(12.5), at(10.5), MoveCause.MOVE);

        assertEquals(1, heard.size());
        assertSame(parent, heard.get(0).getRegion());
        assertEquals(Resolution.Step.Reason.INHERITED, heard.get(0).getReason());
    }

    @Test
    void allowedIsHeardForWhomAGroupOrTheBypassLiftedTheDenialAndForNobodyElse() {
        DenyEntryFlag denyEntry = denyEntry();
        Region arena = region("arena", 0, 10);
        Region plain = region("plain", 30, 40);
        arena.setFlag(denyEntry, Tristate.TRUE);
        TestFPlayerSender fighter = new TestFPlayerSender("fighter");
        TestFPlayerSender admin = new TestFPlayerSender("admin").grant("evernifecore.region.bypass.*");
        arena.getGroup("fighters").addMember(fighter.getUniqueId());
        arena.getGroup("fighters").setFlag(denyEntry, Tristate.FALSE);
        arena.setFlagHandler(other, denyEntry, heard::add);
        plain.setFlagHandler(other, denyEntry, heard::add);

        for (TestFPlayerSender lifted : new TestFPlayerSender[]{fighter, admin}) {
            tracker.onJoin(lifted, at(12.5));
            assertTrue(tracker.onMove(lifted, at(12.5), at(10.5), MoveCause.MOVE).isAllowed());
        }
        tracker.onJoin(player, at(28.5));
        tracker.onMove(player, at(28.5), at(30.5), MoveCause.MOVE);   //a region without the flag
        tracker.onMove(player, at(30.5), at(28.5), MoveCause.MOVE);

        assertEquals(2, heard.size());
        assertEquals(FlagOutcome.ALLOWED, heard.get(0).getOutcome());
        assertEquals(Resolution.Step.Reason.GROUP_VALUE, heard.get(0).getReason());
        assertEquals(Resolution.Step.Reason.BYPASS, heard.get(1).getReason());
    }

    @Test
    void outsideMovementTheHandlerOnlyReacts() {
        DenyBlockBreakFlag denyBreak = registered(new DenyBlockBreakFlag(owner));
        Region arena = region("arena", 0, 10);
        arena.setFlag(denyBreak, Tristate.TRUE);
        List<IllegalStateException> refused = new CopyOnWriteArrayList<>();
        arena.setFlagHandler(other, denyBreak, context -> {
            heard.add(context);
            try {
                context.revertTo(STANDS);
            } catch (IllegalStateException outsideMove) {
                refused.add(outsideMove);
            }
        });

        Decision decision = lookup.decide(new BlockContext(EventSource.BREAK, at(5.5), player, null, "minecraft:stone", null, false));

        assertEquals(Decision.DENY, decision);
        assertEquals(1, heard.size());
        assertEquals(FlagOutcome.DENIED, heard.get(0).getOutcome());
        assertTrue(refused.get(0).getMessage().contains("outside MOVE a handler only reacts"), refused.get(0).getMessage());
    }

    @Test
    void theFlagsOwnMessageStillGoesOutWithAHandler() {
        DenyEntryWithMessageFlag denyWithMessage = registered(new DenyEntryWithMessageFlag(owner));
        Region arena = region("arena", 0, 10);
        arena.setFlag(denyWithMessage, new DenyEntryMessage(Tristate.TRUE, "Boss fight in progress"));
        arena.setFlagHandler(other, denyWithMessage, context -> context.revertTo(STANDS));
        tracker.onJoin(player, at(12.5));

        assertEquals(STANDS, tracker.onMove(player, at(12.5), at(10.5), MoveCause.MOVE).getRevertTo());

        assertTrue(player.getMessages().stream().anyMatch(message -> message.contains("Boss fight in progress")), player.getMessages().toString());
    }

    @Test
    void aHandlerGoesWithItsFlagsValueAndWithItsOwner() {
        DenyEntryFlag denyEntry = denyEntry();
        Region removed = region("removed", 0, 10);
        removed.setFlag(denyEntry, Tristate.TRUE);
        removed.setFlagHandler(other, denyEntry, heard::add);
        removed.removeFlag(denyEntry);
        assertNull(removed.getFlagHandler(denyEntry));

        Region expiring = region("expiring", 20, 30);
        expiring.setFlag(denyEntry, Tristate.TRUE, Duration.ofSeconds(5));
        expiring.setFlagHandler(other, denyEntry, heard::add);
        clock.advance(Duration.ofSeconds(6));
        expiring.getFlagStore().expire(clock.instant());
        assertNull(expiring.getFlagHandler(denyEntry));

        Region foreign = TestRegions.loaded(owner, "foreign", WORLD);
        try {
            foreign.setFlagHandler(other, denyEntry, heard::add);
            RegionManager.closeOwnedBy(other);
            assertNull(foreign.getFlagHandler(denyEntry), "the plugin that set it shut down");
            assertFalse(foreign.hasFlagHandlers());
        } finally {
            TestRegions.unloadAll();
        }
    }

    @Test
    void withoutHandlersNothingIsBuiltForThem() {
        DenyEntryFlag denyEntry = denyEntry();
        Region arena = region("arena", 0, 10);
        arena.setFlag(denyEntry, Tristate.TRUE);

        assertFalse(arena.hasFlagHandlers());
        assertEquals(at(12.5), walk(12.5, 10.5).getRevertTo());
        assertNull(arena.getFlagHandler(denyEntry));
        assertSame(RegionLookup.get(), RegionLookup.get(), "the global lookup is untouched by this tracker");
    }
}
