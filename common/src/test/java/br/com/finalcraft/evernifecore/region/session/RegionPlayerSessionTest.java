package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.TestRegions;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.MembershipCheck;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Every group mutation clears the group's cached membership in every open session, and the explicit invalidations. */
@ECoreTest
class RegionPlayerSessionTest {

    private static final String OWNER = "RegionSessionOwner";

    @TempDir
    Path tempDir;

    private Region region;
    private AccessGroup group;
    private AccessGroup otherGroup;
    private TestFPlayerSender alice;
    private TestFPlayerSender bob;

    @BeforeEach
    void setUp() {
        ECPluginData owner = Plugins.fakePluginData(OWNER, tempDir.toFile());
        region = TestRegions.create(owner, "arena", "world");
        group = region.getGroup("fighters");
        group.setPermission("arena.fighter");
        group.setMembershipCheck(MembershipCheck.onBoundary());
        otherGroup = TestRegions.create(owner, "lobby", "world").getGroup("guests");
        otherGroup.setPermission("lobby.guest");
        otherGroup.setMembershipCheck(MembershipCheck.onBoundary());
        alice = Senders.player("alice");
        bob = Senders.player("bob");
    }

    @AfterEach
    void tearDown() {
        RegionLookup.get().forgetSession(alice.getUniqueId());
        RegionLookup.get().forgetSession(bob.getUniqueId());
        Plugins.forget(OWNER);
    }

    private RegionPlayerSession session(TestFPlayerSender player) {
        return RegionLookup.get().getSession(player);
    }

    /** Both players have a cached answer for the group; after {@code mutation} neither has. */
    private void assertClearsBothSessions(Consumer<AccessGroup> mutation) {
        group.contains(alice);
        group.contains(bob);
        assertNotNull(session(alice).getCachedMembership(group));
        assertNotNull(session(bob).getCachedMembership(group));

        mutation.accept(group);

        assertNull(session(alice).getCachedMembership(group));
        assertNull(session(bob).getCachedMembership(group));
    }

    @Test
    void addMemberClearsEverySession() {
        assertClearsBothSessions(g -> g.addMember(UUID.randomUUID()));
    }

    @Test
    void removeMemberClearsEverySession() {
        UUID carol = UUID.randomUUID();
        group.addMember(carol);
        assertClearsBothSessions(g -> g.removeMember(carol));
    }

    @Test
    void setPredicateClearsEverySession() {
        assertClearsBothSessions(g -> g.setPredicate(player -> false));
    }

    @Test
    void setPermissionClearsEverySession() {
        assertClearsBothSessions(g -> g.setPermission("arena.other"));
    }

    @Test
    void setMembershipCheckClearsEverySession() {
        assertClearsBothSessions(g -> g.setMembershipCheck(MembershipCheck.onBoundary()));
    }

    @Test
    void removeGroupClearsEverySession() {
        assertClearsBothSessions(g -> region.removeGroup(g.getName()));
    }

    @Test
    void invalidateByPlayerClearsOnlyThatSession() {
        group.contains(alice);
        group.contains(bob);
        RegionLookup.get().invalidate(alice.getUniqueId());
        assertNull(session(alice).getCachedMembership(group));
        assertEquals(Boolean.FALSE, session(bob).getCachedMembership(group));
    }

    @Test
    void invalidateByRegionClearsOnlyThatRegionsGroups() {
        group.contains(alice);
        otherGroup.contains(alice);
        RegionLookup.get().invalidate(region);
        assertNull(session(alice).getCachedMembership(group));
        assertEquals(Boolean.FALSE, session(alice).getCachedMembership(otherGroup));
    }

    @Test
    void forgetSessionDropsIt() {
        RegionPlayerSession first = session(alice);
        assertSame(first, session(alice));
        assertEquals(alice.getUniqueId(), first.getPlayer());
        group.contains(alice);
        RegionLookup.get().forgetSession(alice.getUniqueId());
        RegionPlayerSession second = session(alice);
        assertNotSame(first, second);
        assertNull(second.getCachedMembership(group));
    }
}
