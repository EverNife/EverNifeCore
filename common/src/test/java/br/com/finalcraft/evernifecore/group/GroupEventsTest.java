package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.group.event.GroupCreatedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupDissolvedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupInviteEvent;
import br.com.finalcraft.evernifecore.group.event.GroupLeaderChangedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberJoinedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberLeftEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberPreJoinEvent;
import br.com.finalcraft.evernifecore.group.event.GroupRoleChangedEvent;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pre event keeps a player out on both paths with the listener's reason; the after events say who, how and why. */
@ECoreTest
class GroupEventsTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private GroupWorld parties;
    private GroupKind party;
    private ECEventSubscription<GroupMemberPreJoinEvent> veto;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        party = parties.kind("party", GroupKind.Cardinality.MANY, GroupRole.of("member", 0), GroupRole.of("officer", 10));
    }

    @AfterEach
    void close() {
        if (veto != null) {
            veto.unsubscribe();
        }
        parties.close();
        world.close();
    }

    @Test
    void aCancelledPreJoinKeepsThePlayerOutOfAddAndJoinWithTheReason() {
        TestFPlayerSender steve = parties.player("Steve");
        Group alpha = parties.group("alpha", party);
        List<GroupMemberJoinedEvent> joined = parties.recorded(GroupMemberJoinedEvent.class);
        veto = ECEventBus.global().subscribe(GroupMemberPreJoinEvent.class,
                event -> event.setDenyReason(FancyText.of("Banned from " + event.getGroup().getName())));

        boolean added = alpha.add(steve.getUniqueId());
        GateResult result = alpha.join(steve);

        assertFalse(added);
        assertFalse(result.isPassed());
        assertEquals("Banned from alpha", result.explain().toPlainText());
        assertFalse(alpha.isMember(steve.getUniqueId()));
        assertTrue(joined.isEmpty(), "nothing joined, so nothing is announced");
    }

    @Test
    void thePreEventSaysHowThePlayerCameAndJoinedCarriesTheMember() {
        TestFPlayerSender steve = parties.player("Steve");
        UUID alex = UUID.randomUUID();
        Group alpha = parties.group("alpha", party);
        List<GroupMemberPreJoinEvent> pre = parties.recorded(GroupMemberPreJoinEvent.class);
        List<GroupMemberJoinedEvent> joined = parties.recorded(GroupMemberJoinedEvent.class);

        alpha.add(alex);
        alpha.join(steve);

        assertEquals(JoinCause.ADDED, pre.get(0).getCause());
        assertNull(pre.get(0).getFPlayer(), "the owner's add carries no player");
        assertEquals(JoinCause.JOINED, pre.get(1).getCause());
        assertSame(steve, pre.get(1).getFPlayer());
        assertEquals(2, joined.size());
        assertEquals(steve.getUniqueId(), joined.get(1).getMember().getUuid());
    }

    @Test
    void everyWayOutPostsLeftWithItsCause() {
        TestFPlayerSender steve = parties.player("Steve");
        UUID alex = UUID.randomUUID();
        UUID bia = UUID.randomUUID();
        Group alpha = parties.group("alpha", party);
        alpha.join(steve);
        alpha.add(alex);
        alpha.add(bia, Duration.ofMinutes(1));
        List<GroupMemberLeftEvent> left = parties.recorded(GroupMemberLeftEvent.class);

        assertTrue(alpha.leave(steve).isPassed());
        assertTrue(alpha.remove(alex, LeaveCause.KICKED));
        parties.advance(Duration.ofMinutes(2));
        alpha.expire(parties.now());

        assertEquals(3, left.size());
        assertEquals(LeaveCause.LEFT, left.get(0).getCause());
        assertEquals(LeaveCause.KICKED, left.get(1).getCause());
        assertEquals(alex, left.get(1).getPlayer());
        assertEquals(LeaveCause.EXPIRED, left.get(2).getCause());
        assertFalse(alpha.leave(steve).isPassed(), "a player who is not a member cannot leave");
    }

    @Test
    void rolesLeaderInvitesAndDissolutionEachPostTheirEvent() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        List<GroupCreatedEvent> created = parties.recorded(GroupCreatedEvent.class);
        Group alpha = parties.group("alpha", party);
        List<GroupRoleChangedEvent> roles = parties.recorded(GroupRoleChangedEvent.class);
        List<GroupLeaderChangedEvent> leaders = parties.recorded(GroupLeaderChangedEvent.class);
        List<GroupInviteEvent> invites = parties.recorded(GroupInviteEvent.class);
        List<GroupDissolvedEvent> dissolved = parties.recorded(GroupDissolvedEvent.class);
        List<GroupMemberLeftEvent> left = parties.recorded(GroupMemberLeftEvent.class);

        alpha.add(steve);
        alpha.setRoles(steve, Collections.singleton(party.getRole("officer")));
        alpha.setLeader(steve);
        alpha.invite(alex, Duration.ofMinutes(5), steve);
        alpha.revokeInvite(alex);
        alpha.dissolve(DissolveCause.OWNER);

        assertSame(alpha, created.get(0).getGroup());
        assertEquals(Collections.emptySet(), roles.get(0).getOldRoles());
        assertEquals(Collections.singleton(party.getRole("officer")), roles.get(0).getNewRoles());
        assertEquals(steve, leaders.get(0).getNewLeader());
        assertEquals(GroupInviteEvent.Change.SENT, invites.get(0).getChange());
        assertEquals(steve, invites.get(0).getInvite().getInvitedBy());
        assertEquals(GroupInviteEvent.Change.REVOKED, invites.get(1).getChange());
        assertEquals(DissolveCause.OWNER, dissolved.get(0).getCause());
        assertEquals(steve, dissolved.get(0).getLastMembers().iterator().next().getUuid());
        assertEquals(LeaveCause.DISSOLVED, left.get(0).getCause());
        assertNull(parties.getManager().get("alpha"), "a dissolved group is forgotten");
        assertTrue(alpha.isDissolved());
    }

    @Test
    void anAcceptedInviteIsConsumedAndJoinsAsInvited() {
        TestFPlayerSender steve = parties.player("Steve");
        Group alpha = parties.group("alpha", party);
        List<GroupMemberJoinedEvent> joined = parties.recorded(GroupMemberJoinedEvent.class);
        List<GroupInviteEvent> invites = parties.recorded(GroupInviteEvent.class);

        assertFalse(alpha.accept(steve).isPassed(), "no invite, nothing to accept");
        alpha.invite(steve.getUniqueId(), Duration.ofMinutes(5), null);
        assertTrue(alpha.accept(steve).isPassed());

        assertNull(alpha.getInvite(steve.getUniqueId()));
        assertEquals(GroupInviteEvent.Change.ACCEPTED, invites.get(1).getChange());
        assertEquals(JoinCause.INVITED, joined.get(0).getCause());
    }
}
