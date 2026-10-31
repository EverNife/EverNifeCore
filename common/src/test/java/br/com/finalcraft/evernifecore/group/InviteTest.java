package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.group.event.GroupInviteEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberJoinedEvent;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Invites: accepted (and consumed), revoked, expired by the sweeper; accepting walks the join path. */
@ECoreTest
class InviteTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private GroupWorld parties;
    private Group alpha;
    private TestFPlayerSender steve;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        alpha = parties.group("alpha", parties.kind("party", GroupKind.Cardinality.ONE));
        steve = parties.player("Steve");
    }

    @AfterEach
    void close() {
        parties.close();
        world.close();
    }

    @Test
    void anAcceptedInviteIsConsumedAndJoinsAsInvited() {
        UUID leader = UUID.randomUUID();
        List<GroupInviteEvent> invites = parties.recorded(GroupInviteEvent.class);
        List<GroupMemberJoinedEvent> joined = parties.recorded(GroupMemberJoinedEvent.class);

        Invite invite = alpha.invite(steve.getUniqueId(), Duration.ofMinutes(5), leader);
        GateResult accepted = alpha.accept(steve);

        assertTrue(accepted.isPassed());
        assertEquals(leader, invite.getInvitedBy());
        assertTrue(alpha.isMember(steve.getUniqueId()));
        assertNull(alpha.getInvite(steve.getUniqueId()), "consumed");
        assertEquals(GroupInviteEvent.Change.SENT, invites.get(0).getChange());
        assertEquals(GroupInviteEvent.Change.ACCEPTED, invites.get(1).getChange());
        assertEquals(JoinCause.INVITED, joined.get(0).getCause());
    }

    @Test
    void aRevokedInviteCannotBeAccepted() {
        alpha.invite(steve.getUniqueId(), Duration.ofMinutes(5), null);

        assertTrue(alpha.revokeInvite(steve.getUniqueId()));
        assertFalse(alpha.revokeInvite(steve.getUniqueId()), "nothing left to revoke");
        GateResult refused = alpha.accept(steve);

        assertFalse(refused.isPassed());
        assertTrue(refused.explain().toPlainText().contains("has no invite"), refused.explain().toPlainText());
        assertFalse(alpha.isMember(steve.getUniqueId()));
    }

    @Test
    void anInviteExpiresThroughTheSweeperWithAnExpiredEvent() {
        List<GroupInviteEvent> invites = parties.recorded(GroupInviteEvent.class);
        alpha.invite(steve.getUniqueId(), Duration.ofMinutes(5), null);

        parties.advance(Duration.ofMinutes(4));
        parties.runDue();
        assertEquals(1, invites.size(), "not yet");
        parties.advance(Duration.ofMinutes(2));
        parties.runDue();

        assertEquals(2, invites.size());
        assertEquals(GroupInviteEvent.Change.EXPIRED, invites.get(1).getChange());
        assertTrue(alpha.getStoredInvites().isEmpty(), "the sweeper dropped it from what is stored");
        assertFalse(alpha.accept(steve).isPassed());
    }

    @Test
    void acceptingGoesThroughTheJoinEdge() {
        alpha.setJoinEdge(Edge.gate(Gate.builder().require(ctx -> ConditionResult.fail(FancyText.of("the party is locked"))).build()));
        alpha.invite(steve.getUniqueId(), Duration.ofMinutes(5), null);

        GateResult refused = alpha.accept(steve);

        assertFalse(refused.isPassed());
        assertEquals("the party is locked", refused.explain().toPlainText());
        assertFalse(alpha.isMember(steve.getUniqueId()));
    }

    @Test
    void anInviteWithoutAPositiveDurationIsRefusedAndSaysInvitesExpire() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> alpha.invite(steve.getUniqueId(), Duration.ZERO, null));

        assertTrue(refused.getMessage().contains("an invite always expires"), refused.getMessage());
    }
}
