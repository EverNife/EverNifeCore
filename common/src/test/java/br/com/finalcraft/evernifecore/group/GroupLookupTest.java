package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.AmountEvery;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.flag.Resolution;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One lookup over every source of groups, and the groups' place in the flag resolution. */
@ECoreTest
class GroupLookupTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private GroupWorld parties;
    private GroupKind party;
    private ECPluginData clansPlugin;
    private FakeClans clans;

    /** A plugin of its own groups (a clans plugin), answering through a provider. */
    private static final class FakeClans implements GroupProvider {
        final GroupKind kind;
        final Map<GroupId, Set<UUID>> members = new HashMap<>();
        final Map<GroupId, UUID> leaders = new HashMap<>();

        FakeClans(GroupKind kind) {
            this.kind = kind;
        }

        GroupId clan(ECPluginData owner, String name, @Nullable UUID leader, UUID... uuids) {
            GroupId id = GroupId.of(owner, name);
            members.put(id, new LinkedHashSet<>(Arrays.asList(uuids)));
            leaders.put(id, leader);
            return id;
        }

        @Override
        public Collection<GroupKind> getKinds() {
            return Collections.singleton(kind);
        }

        @Override
        public List<GroupId> getGroupIdsOf(UUID player) {
            return members.entrySet().stream().filter(entry -> entry.getValue().contains(player)).map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        @Override
        public List<GroupId> getGroupIdsOf(UUID player, GroupKind kind) {
            return kind.equals(this.kind) ? getGroupIdsOf(player) : Collections.<GroupId>emptyList();
        }

        @Override
        public boolean exists(GroupId id) {
            return members.containsKey(id);
        }

        @Override
        public Set<UUID> getMembers(GroupId id) {
            return members.getOrDefault(id, Collections.<UUID>emptySet());
        }

        @Override
        public @Nullable UUID getLeader(GroupId id) {
            return leaders.get(id);
        }
    }

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        party = parties.kind("party", GroupKind.Cardinality.ONE);
        clansPlugin = Plugins.fakePluginData("FakeClans", tempDir.resolve("clans").toFile());
        clans = new FakeClans(GroupKind.of(clansPlugin, "clan").build());
        GroupLookup.get().register(clansPlugin, clans);
    }

    @AfterEach
    void close() {
        GroupLookup.get().unregisterAll(clansPlugin);
        Plugins.forget("FakeClans");
        parties.close();
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        world.close();
    }

    @Test
    void aProviderAnswersBesideTheManagers() {
        UUID steve = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        Group alpha = parties.group("alpha", party);
        alpha.add(steve);
        GroupId wolves = clans.clan(clansPlugin, "wolves", leader, steve, leader);

        assertEquals(new LinkedHashSet<>(Arrays.asList(alpha.getId(), wolves)), new LinkedHashSet<>(GroupLookup.get().getGroupsOf(steve)));
        assertEquals(Collections.singletonList(wolves), GroupLookup.get().getGroupsOf(steve, clans.kind));
        assertTrue(GroupLookup.get().exists(wolves));
        assertTrue(GroupLookup.get().exists(alpha.getId()));
        assertEquals(leader, GroupLookup.get().getLeader(wolves));
        assertEquals(new LinkedHashSet<>(Arrays.asList(steve, leader)), GroupLookup.get().getMembers(wolves));
        assertSame(alpha, GroupLookup.get().byId(alpha.getId()));
        assertNull(GroupLookup.get().byId(wolves), "a provider's group is not one the core manages");
    }

    @Test
    void aSecondProviderOfTheSameKindIsRefusedNamingTheFirstOwner() {
        ECPluginData copycat = Plugins.fakePluginData("CopyCat", tempDir.resolve("copycat").toFile());
        try {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> GroupLookup.get().register(copycat, new FakeClans(clans.kind)));

            assertTrue(refused.getMessage().contains("already answered by a GroupProvider of FakeClans"), refused.getMessage());
        } finally {
            Plugins.forget("CopyCat");
        }
    }

    @Test
    void unregisterAllTakesTheProvidersOfAnOwnerOut() {
        UUID steve = UUID.randomUUID();
        GroupId wolves = clans.clan(clansPlugin, "wolves", null, steve);

        GroupLookup.get().unregisterAll(clansPlugin);

        assertFalse(GroupLookup.get().exists(wolves));
        assertTrue(GroupLookup.get().getGroupsOf(steve).isEmpty());
        GroupLookup.get().register(clansPlugin, clans);   //what the teardown takes out again
    }

    @Test
    void shareGroupIsTrueForTwoMembersOfOneGroupOfAnyProviderButARegionsOwn() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        UUID wolf = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Group alpha = parties.group("alpha", party);
        alpha.add(steve);
        alpha.add(alex);
        clans.clan(clansPlugin, "wolves", null, steve, wolf);
        Group vip = parties.regions().cuboid("spawn", "world", 0, 0, 0, 9, 9, 9).getGroup("vip");
        vip.add(stranger);
        vip.add(alex);

        assertTrue(GroupLookup.get().shareGroup(steve, alex), "one party");
        assertTrue(GroupLookup.get().shareGroup(steve, wolf), "one clan of another plugin");
        assertFalse(GroupLookup.get().shareGroup(alex, wolf));
        assertFalse(GroupLookup.get().shareGroup(alex, stranger), "a region's group is a place's list, not a team");
        assertFalse(GroupLookup.get().shareGroup(steve, steve), "nobody shares a group with themselves");
    }

    @Test
    void hasOnlineMemberAsksThePlatformForEveryMemberOfAnyProvider() {
        TestFPlayerSender steve = parties.player("Steve");
        UUID offline = UUID.randomUUID();
        Group alpha = parties.group("alpha", party);
        alpha.add(offline);
        GroupId wolves = clans.clan(clansPlugin, "wolves", null, offline);

        assertFalse(GroupLookup.get().hasOnlineMember(alpha.getId()));
        assertFalse(GroupLookup.get().hasOnlineMember(wolves));
        alpha.add(steve.getUniqueId());
        clans.members.get(wolves).add(steve.getUniqueId());

        assertTrue(GroupLookup.get().hasOnlineMember(alpha.getId()));
        assertTrue(GroupLookup.get().hasOnlineMember(wolves));
        steve.online(false);
        assertFalse(alpha.hasOnlineMember(), "a player who left is not online");
    }

    @Test
    void aGroupFlagReachesRegionLookupResolveOutsideEveryRegion() {
        TestFPlayerSender member = parties.player("Member").at("world", 500, 64, 500);
        TestFPlayerSender outsider = parties.player("Outsider").at("world", 500, 64, 500);
        Group alpha = parties.group("alpha", party);
        alpha.add(member.getUniqueId());
        List<AmountEvery> heal = Collections.singletonList(AmountEvery.of(2, Duration.ofSeconds(5)));

        alpha.setFlag(CoreFlags.HEAL, heal);

        assertEquals(heal, RegionLookup.get().resolve(CoreFlags.HEAL, member));
        Resolution<List<AmountEvery>> explained = RegionLookup.get().explain(CoreFlags.HEAL, "world", 500, 64, 500, member);
        assertEquals(Resolution.Reason.ACTOR_VALUE, explained.getReason());
        assertSame(alpha, explained.getSource());
        assertNull(RegionLookup.get().resolve(CoreFlags.HEAL, outsider), "the group's value follows its members only");
    }

    @Test
    void joiningLeavingAndExpiringInvalidateTheSessionWithoutAManualCall() {
        TestFPlayerSender joiner = parties.player("Joiner").at("world", 500, 64, 500);
        TestFPlayerSender leaver = parties.player("Leaver").at("world", 500, 64, 500);
        TestFPlayerSender timed = parties.player("Timed").at("world", 500, 64, 500);
        Group alpha = parties.group("alpha", party);
        List<AmountEvery> heal = Collections.singletonList(AmountEvery.of(2, Duration.ofSeconds(5)));
        alpha.setFlag(CoreFlags.HEAL, heal);
        alpha.add(leaver.getUniqueId());
        alpha.add(timed.getUniqueId(), Duration.ofMinutes(1));
        assertEquals(heal, RegionLookup.get().resolve(CoreFlags.HEAL, leaver));
        assertNull(RegionLookup.get().resolve(CoreFlags.HEAL, joiner));
        for (TestFPlayerSender player : Arrays.asList(joiner, leaver, timed)) {
            assertFalse(RegionLookup.get().getSession(player).isRevalidateAllPending(), "a fresh session of " + player.getName());
        }

        assertTrue(alpha.join(joiner).isPassed());
        assertTrue(alpha.leave(leaver).isPassed());
        parties.advance(Duration.ofMinutes(2));
        parties.runDue();

        assertEquals(heal, RegionLookup.get().resolve(CoreFlags.HEAL, joiner), "entering: the value is there");
        assertNull(RegionLookup.get().resolve(CoreFlags.HEAL, leaver), "leaving: the value is gone");
        assertNull(RegionLookup.get().resolve(CoreFlags.HEAL, timed), "expired: the value is gone");
        assertTrue(RegionLookup.get().getSession(joiner).isRevalidateAllPending(), "entering looks at the session flags again");
        assertTrue(RegionLookup.get().getSession(leaver).isRevalidateAllPending(), "leaving looks at the session flags again");
        assertTrue(RegionLookup.get().getSession(timed).isRevalidateAllPending(), "expiring looks at the session flags again");
    }

    @Test
    void aFlagChangeOfAGroupInvalidatesTheSessionsOfItsMembers() {
        TestFPlayerSender member = parties.player("Member").at("world", 500, 64, 500);
        TestFPlayerSender outsider = parties.player("Outsider").at("world", 500, 64, 500);
        Group alpha = parties.group("alpha", party);
        alpha.add(member.getUniqueId());
        RegionLookup.get().getSession(member);
        RegionLookup.get().getSession(outsider);

        alpha.setFlag(CoreFlags.HEAL, Collections.singletonList(AmountEvery.of(1, Duration.ofSeconds(1))));

        assertTrue(RegionLookup.get().getSession(member).isRevalidateAllPending());
        assertFalse(RegionLookup.get().getSession(outsider).isRevalidateAllPending(), "not a member: nothing of theirs changed");
    }
}
