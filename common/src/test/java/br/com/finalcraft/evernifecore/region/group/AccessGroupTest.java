package br.com.finalcraft.evernifecore.region.group;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.TestRegions.ManualClock;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Membership by UUID, predicate and permission, each under the three checks; deadlines; the group's own values. */
@ECoreTest
class AccessGroupTest {

    private static final String OWNER = "AccessGroupOwner";
    private static final String PERMISSION = "arena.fighter";

    enum Mode { UUID, PREDICATE, PERMISSION }

    enum Check { EXACT, ON_BOUNDARY, INTERVAL }

    @TempDir
    Path tempDir;

    private ECPluginData owner;
    private ManualClock clock;
    private TristateFlag flag;
    private Region region;
    private final List<TestFPlayerSender> players = new ArrayList<>();

    @BeforeEach
    void setUp() {
        owner = Plugins.fakePluginData(OWNER, tempDir.toFile());
        clock = TestRegions.manualClock();
        flag = new TristateFlag(FlagKey.of(owner, "deny-pvp-test"), Collections.singleton(EventSource.DAMAGE), Tristate.FALSE, Tristate.TRUE);
        FlagRegistry.get().register(owner, flag);
        region = TestRegions.create(owner, "arena", "world");
    }

    @AfterEach
    void tearDown() {
        for (TestFPlayerSender player : players) {
            RegionLookup.get().forgetSession(player.getUniqueId());
        }
        FlagRegistry.get().unregisterAll(owner);
        TestRegions.restoreClock();
        Plugins.forget(OWNER);
    }

    private TestFPlayerSender player(String name) {
        TestFPlayerSender player = Senders.player(name);
        players.add(player);
        return player;
    }

    private static MembershipCheck check(Check check) {
        switch (check) {
            case ON_BOUNDARY:
                return MembershipCheck.onBoundary();
            case INTERVAL:
                return MembershipCheck.interval(Duration.ofSeconds(30));
            default:
                return MembershipCheck.exact();
        }
    }

    static Stream<Arguments> modesTimesChecks() {
        List<Arguments> cases = new ArrayList<>();
        for (Mode mode : Mode.values()) {
            for (Check check : Check.values()) {
                cases.add(Arguments.of(mode, check));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} under {1}")
    @MethodSource("modesTimesChecks")
    void membershipFollowsTheCheck(Mode mode, Check checkKind) {
        TestFPlayerSender alice = player("alice");
        AccessGroup group = region.getGroup("fighters");
        AtomicBoolean predicateSays = new AtomicBoolean(false);
        if (mode == Mode.PREDICATE) {
            group.setPredicate(p -> predicateSays.get());
        } else if (mode == Mode.PERMISSION) {
            group.setPermission(PERMISSION);
        }
        group.setMembershipCheck(check(checkKind));
        assertFalse(group.contains(alice));

        //the change below goes around the group, so only the check decides when the group notices
        switch (mode) {
            case UUID:
                group.addMember(alice.getUniqueId());
                assertTrue(group.contains(alice), "an explicit member is a set lookup, never cached");
                assertNull(RegionLookup.get().getSession(alice).getCachedMembership(group));
                return;
            case PREDICATE:
                predicateSays.set(true);
                break;
            default:
                alice.grant(PERMISSION);
        }

        if (checkKind == Check.EXACT) {
            assertTrue(group.contains(alice));
            assertNull(RegionLookup.get().getSession(alice).getCachedMembership(group), "exact never caches");
        } else if (checkKind == Check.ON_BOUNDARY) {
            assertFalse(group.contains(alice), "still the cached answer");
            assertEquals(Boolean.FALSE, RegionLookup.get().getSession(alice).getCachedMembership(group));
            clock.advance(Duration.ofHours(1));
            assertFalse(group.contains(alice), "time alone does not refresh a boundary check");
            RegionLookup.get().getSession(alice).markBoundaryCrossed();
            assertTrue(group.contains(alice));
        } else {
            assertFalse(group.contains(alice), "still the cached answer");
            clock.advance(Duration.ofSeconds(29));
            assertFalse(group.contains(alice));
            clock.advance(Duration.ofSeconds(1));
            assertTrue(group.contains(alice), "the interval passed");
            assertEquals(Boolean.TRUE, RegionLookup.get().getSession(alice).getCachedMembership(group));
        }
    }

    @Test
    void intervalCheckAlsoRefreshesOnTheBoundary() {
        TestFPlayerSender alice = player("alice");
        AccessGroup group = region.getGroup("fighters");
        group.setPermission(PERMISSION);
        group.setMembershipCheck(MembershipCheck.interval(Duration.ofMinutes(5)));
        assertFalse(group.contains(alice));
        alice.grant(PERMISSION);
        assertFalse(group.contains(alice));
        RegionLookup.get().getSession(alice).markBoundaryCrossed();
        assertTrue(group.contains(alice));
    }

    @Test
    void temporaryMemberDisappearsOnReadOnceExpired() {
        TestFPlayerSender alice = player("alice");
        AccessGroup group = region.getGroup("fighters");
        Membership membership = group.addMember(alice.getUniqueId(), Duration.ofMinutes(15));
        assertEquals(Duration.ofMinutes(15), membership.getRemaining());
        assertTrue(group.contains(alice));

        clock.advance(Duration.ofMinutes(10));
        assertEquals(Duration.ofMinutes(5), group.getMembership(alice.getUniqueId()).getRemaining());
        assertEquals(1, group.getMembers().size());

        clock.advance(Duration.ofMinutes(5));
        assertFalse(group.contains(alice));
        assertNull(group.getMembership(alice.getUniqueId()));
        assertTrue(group.getMembers().isEmpty());
        assertTrue(membership.isExpired());
    }

    @Test
    void permanentMemberAndRemoval() {
        TestFPlayerSender alice = player("alice");
        AccessGroup group = region.getGroup("fighters");
        Membership membership = group.addMember(alice.getUniqueId());
        assertNull(membership.getExpiresAt());
        assertNull(membership.getRemaining());
        assertTrue(group.removeMember(alice.getUniqueId()));
        assertFalse(group.removeMember(alice.getUniqueId()));
        assertFalse(group.contains(alice));
        assertThrows(IllegalArgumentException.class, () -> group.addMember(alice.getUniqueId(), Duration.ZERO));
    }

    @Test
    void groupValueOnlyForMembers() {
        TestFPlayerSender alice = player("alice");
        TestFPlayerSender bob = player("bob");
        AccessGroup group = region.getGroup("fighters");
        group.setFlag(flag, Tristate.FALSE);
        group.addMember(alice.getUniqueId());
        assertEquals(Tristate.FALSE, group.resolve(flag, alice));
        assertNull(group.resolve(flag, bob));
        assertEquals(Tristate.FALSE, group.getFlags().get(flag.getKey()).getValue());
        assertTrue(group.removeFlag(flag));
        assertNull(group.resolve(flag, alice));
    }

    @Test
    void getGroupAppliesTheNameRuleAndCreatesOnce() {
        AccessGroup fighters = region.getGroup("Fighters");
        assertEquals("fighters", fighters.getName());
        assertSame(fighters, region.getGroup("fighters"));
        assertSame(fighters, region.getGroupIfPresent("FIGHTERS"));
        assertSame(region, fighters.getRegion());
        assertEquals(0, fighters.getPriority());
        assertEquals(MembershipCheck.exact(), fighters.getMembershipCheck());
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> region.getGroup("a b"));
        assertTrue(refused.getMessage().contains("'a b'") && refused.getMessage().contains("[a-z0-9_-]{1,64}"), refused.getMessage());
        assertThrows(UnsupportedOperationException.class, () -> region.getGroups().clear());
        assertTrue(region.removeGroup("fighters"));
        assertFalse(region.removeGroup("fighters"));
        assertNull(region.getGroupIfPresent("fighters"));
        assertNotNull(region.getGroup("fighters"));
    }
}
