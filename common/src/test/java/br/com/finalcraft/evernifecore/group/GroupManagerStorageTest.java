package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.TristateFlag;
import br.com.finalcraft.evernifecore.storage.Persistence;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What one manager writes is what the next one on the same storage reads; a group whose kind is unknown waits for it. */
@ECoreTest
class GroupManagerStorageTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private GroupWorld parties;
    private TristateFlag buff;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        buff = new TristateFlag(FlagKey.of(parties.getOwner(), "buff"), Collections.singleton(EventSource.DAMAGE), Tristate.FALSE, Tristate.TRUE) {
            @Override
            public FlagScope getScope() {
                return FlagScope.ACTOR;
            }
        };
        FlagRegistry.get().register(parties.getOwner(), buff);
    }

    @AfterEach
    void close() {
        FlagRegistry.get().unregisterAll(parties.getOwner());
        parties.close();
        world.close();
    }

    private GroupKind partyKind() {
        return GroupKind.of(parties.getOwner(), "party").cardinality(GroupKind.Cardinality.ONE)
                .roles(GroupRole.of("member", 0), GroupRole.of("officer", 10)).build();
    }

    @Test
    void aSecondManagerReadsBackKindLeaderRolesInvitesAndFlags() {
        GroupKind party = partyKind();
        parties.getManager().registerKind(party);
        UUID leader = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        Group alpha = parties.getManager().create("alpha", party, GroupOptions.defaults().withLeader(leader).withDisplayName("&6Alpha")
                .withMetadata(JsonNodeFactory.instance.objectNode().put("color", "gold")));
        alpha.addRole(leader, party.getRole("officer"));
        alpha.invite(invited, Duration.ofDays(1), leader);
        alpha.setFlag(buff, Tristate.TRUE);
        alpha.setPriority(4);
        alpha.setMembershipCheck(MembershipCheck.onBoundary());
        parties.flush();

        GroupManager next = parties.reopen();
        next.registerKind(partyKind());
        parties.load();

        Group read = next.get("alpha");
        assertNotNull(read);
        assertEquals(party, read.getKind());
        assertEquals(Persistence.PERSISTENT, read.getPersistence());
        assertEquals(leader, read.getLeader());
        assertEquals(Collections.singleton(party.getRole("officer")), read.getMember(leader).getRoles());
        assertEquals(alpha.getMember(leader).getJoinedAt(), read.getMember(leader).getJoinedAt());
        assertEquals(leader, read.getInvite(invited).getInvitedBy());
        assertEquals(alpha.getInvite(invited).getExpiresAt(), read.getInvite(invited).getExpiresAt());
        assertEquals(Tristate.TRUE, read.getFlag(buff));
        assertEquals(4, read.getPriority());
        assertEquals(MembershipCheck.onBoundary(), read.getMembershipCheck());
        assertEquals("&6Alpha", read.getDisplayName());
        assertEquals("gold", read.getMetadata().get("color").asText());
        assertEquals(alpha.getCreatedAt(), read.getCreatedAt());
    }

    @Test
    void aGroupWhoseKindIsNotRegisteredStaysUnreadUntilTheKindRegisters() {
        GroupKind party = partyKind();
        parties.getManager().registerKind(party);
        UUID steve = UUID.randomUUID();
        parties.getManager().create("alpha", party).add(steve);
        parties.flush();

        GroupManager next = parties.reopen();
        parties.load();
        assertNull(next.get("alpha"), "its kind is unknown, so the group is not read");
        int saves = parties.getBackend().callsTo("save");

        next.registerKind(partyKind());

        assertNotNull(next.get("alpha"), "registering the kind reads it");
        assertTrue(next.get("alpha").isMember(steve));
        assertEquals(saves, parties.getBackend().callsTo("save"), "reading it is not a change");
    }

    @Test
    void aRoleTheKindNoLongerListsIsKeptAndComesBackWithTheKind() {
        GroupKind party = partyKind();
        parties.getManager().registerKind(party);
        UUID steve = UUID.randomUUID();
        Group alpha = parties.getManager().create("alpha", party);
        alpha.add(steve);
        alpha.addRole(steve, party.getRole("officer"));
        parties.flush();

        GroupManager shrunk = parties.reopen();
        shrunk.registerKind(GroupKind.of(parties.getOwner(), "party").cardinality(GroupKind.Cardinality.ONE)
                .roles(GroupRole.of("member", 0)).build());
        parties.load();
        assertEquals(Collections.singleton("officer"), shrunk.get("alpha").getMember(steve).getUnknownRoles());
        shrunk.get("alpha").setPriority(1);   //any change rewrites the whole record
        parties.flush();

        GroupManager again = parties.reopen();
        again.registerKind(partyKind());
        parties.load();
        assertTrue(again.get("alpha").getMember(steve).hasRole("officer"));
    }

    @Test
    void aTransientGroupIsNeverWritten() {
        GroupKind party = partyKind();
        parties.getManager().registerKind(party);
        int saves = parties.getBackend().callsTo("save");

        Group wave = parties.getManager().create("wave", party, GroupOptions.defaults().withPersistence(Persistence.TRANSIENT));
        wave.add(UUID.randomUUID());
        parties.flush();

        assertEquals(saves, parties.getBackend().callsTo("save"));
    }
}
