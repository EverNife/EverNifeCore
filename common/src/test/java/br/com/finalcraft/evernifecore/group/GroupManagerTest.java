package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.region.Persistence;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a manager refuses, and how it says so: no storage, a taken name, an unknown kind, a second group of a ONE kind. */
@ECoreTest
class GroupManagerTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private GroupWorld parties;
    private GroupKind party;
    private GroupKind club;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        party = parties.kind("party", GroupKind.Cardinality.ONE);
        club = parties.kind("club", GroupKind.Cardinality.MANY, GroupRole.of("member", 0), GroupRole.of("officer", 10));
    }

    @AfterEach
    void close() {
        parties.close();
        world.close();
    }

    @Test
    void aManagerWithoutStorageRefusesAPersistentGroupAndSaysHowToPersistIt() {
        ECPluginData noStorage = Plugins.fakePluginData("NoStorageParty", tempDir.resolve("nostorage").toFile());
        try {
            GroupManager manager = GroupManager.of(noStorage);
            GroupKind kind = GroupKind.of(noStorage, "party").build();
            manager.registerKind(kind);

            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> manager.create("alpha", kind, GroupOptions.defaults().withPersistence(Persistence.PERSISTENT)));

            assertEquals("NoStorageParty opened its GroupManager without a storage, so it can only hold TRANSIENT groups. Open it with"
                    + " GroupManager.of(owner, storage) to persist 'alpha'.", refused.getMessage());
            assertEquals(Persistence.TRANSIENT, manager.create("beta", kind).getPersistence(), "without a storage the default is TRANSIENT");
            assertSame(manager, GroupManager.of(noStorage), "the same manager on the next call");
        } finally {
            GroupManager.closeOwnedBy(noStorage);
            Plugins.forget("NoStorageParty");
        }
    }

    @Test
    void aTakenNameIsRefusedNamingTheGroupToGet() {
        parties.group("alpha", party);

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> parties.group("ALPHA", club));

        assertTrue(refused.getMessage().contains("already has a group named 'alpha'") && refused.getMessage().contains("get(\"alpha\")"),
                refused.getMessage());
    }

    @Test
    void aKindThatIsNotRegisteredIsRefusedWithTheCallThatFixesIt() {
        GroupKind stray = GroupKind.of(parties.getOwner(), "raid").build();

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> parties.group("alpha", stray));

        assertTrue(refused.getMessage().contains("fakeparty:raid is not registered") && refused.getMessage().contains("registerKind(kind)"),
                refused.getMessage());
    }

    @Test
    void joiningASecondGroupOfAOneKindIsRefusedByCardinality() {
        TestFPlayerSender steve = parties.player("Steve");
        Group alpha = parties.group("alpha", party);
        Group beta = parties.group("beta", party);
        assertTrue(alpha.join(steve).isPassed());

        GateResult second = beta.join(steve);

        assertFalse(second.isPassed());
        assertEquals("Steve is already in the party 'alpha' (fakeparty:party allows one group per player): leave it first, or make the"
                + " kind MANY.", second.explain().toPlainText());
        assertFalse(beta.isMember(steve.getUniqueId()));
        assertSame(alpha, parties.getManager().getGroupOf(steve.getUniqueId(), party));
    }

    @Test
    void addingASecondGroupOfAOneKindIsRefusedByCardinality() {
        UUID steve = UUID.randomUUID();
        parties.group("alpha", party).add(steve);
        Group beta = parties.group("beta", party);

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> beta.add(steve));

        assertTrue(refused.getMessage().contains("allows one group per player"), refused.getMessage());
        assertFalse(beta.isMember(steve));
    }

    @Test
    void aManyKindTakesAPlayerInSeveralGroupsAndGetGroupOfRefusesIt() {
        UUID steve = UUID.randomUUID();
        parties.group("red", club).add(steve);
        parties.group("blue", club).add(steve);

        assertEquals(2, parties.getManager().getGroupsOf(steve, club).size());
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> parties.getManager().getGroupOf(steve, club));
        assertTrue(refused.getMessage().contains("getGroupsOf(player, kind)"), refused.getMessage());
    }

    @Test
    void aRoleOffTheKindsLadderIsRefusedNamingTheLadder() {
        UUID steve = UUID.randomUUID();
        Group red = parties.group("red", club);
        red.add(steve);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> red.setRoles(steve, Collections.singleton(GroupRole.of("admiral", 99))));

        assertTrue(refused.getMessage().contains("[member(0), officer(10)]"), refused.getMessage());
        assertTrue(red.addRole(steve, club.getRole("officer")));
        assertEquals(10, red.getMember(steve).getRank());
        assertTrue(red.getMember(steve).hasRole("OFFICER"));
    }

    @Test
    void theLeaderMustBeAMemberAndLeavingLeavesTheGroupWithoutOne() {
        UUID steve = UUID.randomUUID();
        Group alpha = parties.getManager().create("alpha", party, GroupOptions.defaults().withLeader(steve));
        assertEquals(steve, alpha.getLeader());
        assertTrue(alpha.isMember(steve), "the leader of the options joins first");

        assertThrows(IllegalArgumentException.class, () -> alpha.setLeader(UUID.randomUUID()));
        assertTrue(alpha.remove(steve, LeaveCause.LEFT));

        assertNull(alpha.getLeader());
    }

    @Test
    void aGroupIsAnActorHolderOfItsOwnerAndARepeatedRoleRankIsRefused() {
        Group alpha = parties.group("alpha", party);
        assertEquals("fakeparty:alpha", alpha.getHolderId());
        assertEquals(FlagScope.ACTOR, alpha.getHolderScope());
        assertSame(parties.getOwner(), alpha.getOwner());
        assertEquals(Collections.singletonList(alpha), new ArrayList<>(parties.getManager().getByKind(party)));
        assertThrows(IllegalArgumentException.class,
                () -> GroupKind.of(parties.getOwner(), "bad").roles(GroupRole.of("a", 1), GroupRole.of("b", 1)));
    }

    @Test
    void nothingIsCreatedBeforeTheLoad() {
        GroupManager reopened = parties.reopen();

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> reopened.create("alpha", party));

        assertTrue(refused.getMessage().contains("has not loaded its groups yet"), refused.getMessage());
    }
}
