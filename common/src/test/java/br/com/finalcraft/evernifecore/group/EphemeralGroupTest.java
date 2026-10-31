package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.group.event.GroupDissolvedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberJoinedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberLeftEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberPreJoinEvent;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.region.Persistence;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.RecordingNotifier;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ephemeral groups: rosters, edges with gates and notices, kick rules, dissolution and the quit policy. */
@ECoreTest
class EphemeralGroupTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private GroupWorld duel;
    private GroupKind team;
    private RecordingNotifier notifier;
    /** Who the "in the arena" condition passes for. */
    private final Set<UUID> inArena = ConcurrentHashMap.newKeySet();
    private final Condition arena = ctx -> ConditionResult.of(inArena.contains(ctx.getPlayer().getUniqueId()), FancyText.of("not in the arena"));

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        duel = GroupWorld.open("FakeDuel", tempDir.resolve("duel"));
        team = duel.kind("team", GroupKind.Cardinality.MANY);
        notifier = duel.notifier();
    }

    @AfterEach
    void close() {
        duel.close();
        world.close();
    }

    private Groups.Ephemeral ephemeral() {
        return Groups.ephemeral(duel.getOwner(), team);
    }

    /** A cost of one token per crossing, with a balance per player. */
    private static final class Tokens implements Cost {
        final Map<UUID, Integer> balance = new ConcurrentHashMap<>();

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            return ConditionResult.of(balance.getOrDefault(ctx.getPlayer().getUniqueId(), 0) >= 1, FancyText.of("you need a token"));
        }

        @Override
        public boolean take(ExecutionContext ctx) {
            balance.merge(ctx.getPlayer().getUniqueId(), -1, Integer::sum);
            return true;
        }

        @Override
        public void refund(ExecutionContext ctx) {
            balance.merge(ctx.getPlayer().getUniqueId(), 1, Integer::sum);
        }

        @Override
        public FancyText describe(ExecutionContext ctx) {
            return FancyText.of("1 token");
        }
    }

    @Test
    void anEphemeralGroupIsTransientNamedUniquelyAndFoundByItsScope() {
        Group first = ephemeral().scope("fakeduel:arena-1/wave-1").create();
        Group second = ephemeral().scope("fakeduel:arena-2/wave-1").create();

        assertEquals(Persistence.TRANSIENT, first.getPersistence());
        assertFalse(first.getName().equals(second.getName()), "two default names: " + first + ", " + second);
        assertTrue(first.getName().startsWith("e"));
        assertEquals(Collections.singletonList(first), GroupLookup.get().byScope("fakeduel:arena-1"));
        assertEquals(QuitPolicy.LEAVE, first.getQuitPolicy(), "an ephemeral group lets a quitting member go by default");
    }

    @Test
    void aLiveRosterTakesInWhoStartsPassingAndLetsGoWhoStops() {
        TestFPlayerSender steve = duel.player("Steve");
        TestFPlayerSender alex = duel.player("Alex");
        inArena.add(steve.getUniqueId());
        List<GroupMemberLeftEvent> left = duel.recorded(GroupMemberLeftEvent.class);

        Group wave = ephemeral().roster(Roster.live(arena)).create();
        assertTrue(wave.isMember(steve.getUniqueId()), "who passes at creation is in at once");
        assertFalse(wave.isMember(alex.getUniqueId()));

        inArena.add(alex.getUniqueId());
        inArena.remove(steve.getUniqueId());
        duel.advance(Duration.ofSeconds(2));
        duel.runDue();

        assertTrue(wave.isMember(alex.getUniqueId()), "Alex started passing: the sweep took him in");
        assertFalse(wave.isMember(steve.getUniqueId()), "Steve stopped passing: the sweep let him go");
        assertEquals(LeaveCause.ROSTER, left.get(left.size() - 1).getCause());
        assertEquals(steve.getUniqueId(), left.get(left.size() - 1).getPlayer());
    }

    @Test
    void aSnapshotRosterIsTakenOnceAtCreation() {
        TestFPlayerSender steve = duel.player("Steve");
        TestFPlayerSender alex = duel.player("Alex");
        inArena.add(steve.getUniqueId());
        UUID offline = UUID.randomUUID();

        Group byCondition = ephemeral().roster(Roster.snapshot(arena)).create();
        Group byList = ephemeral().roster(Roster.snapshot(Arrays.asList(alex.getUniqueId(), offline))).create();
        inArena.add(alex.getUniqueId());
        inArena.remove(steve.getUniqueId());
        duel.advance(Duration.ofSeconds(2));
        duel.runDue();

        assertTrue(byCondition.isMember(steve.getUniqueId()), "a snapshot keeps who passed at creation");
        assertFalse(byCondition.isMember(alex.getUniqueId()), "and never looks again");
        assertTrue(byList.isMember(alex.getUniqueId()));
        assertTrue(byList.isMember(offline), "a listed player joins online or not");
        assertFalse(byCondition.isSampled(), "a snapshot is not sampled");
    }

    @Test
    void kickWhenKicksTheMemberItPassesForWithTheReasonThroughTheNotifier() {
        TestFPlayerSender steve = duel.player("Steve");
        TestFPlayerSender alex = duel.player("Alex");
        Set<UUID> fellThreeTimes = ConcurrentHashMap.newKeySet();
        Group wave = ephemeral().kickWhen(ctx -> ConditionResult.of(fellThreeTimes.contains(ctx.getPlayer().getUniqueId()),
                FancyText.of("still standing")), FancyText.of("§cYou fell 3 times")).create();
        wave.add(steve.getUniqueId());
        wave.add(alex.getUniqueId());
        List<GroupMemberLeftEvent> left = duel.recorded(GroupMemberLeftEvent.class);

        fellThreeTimes.add(steve.getUniqueId());
        duel.advance(Duration.ofSeconds(2));
        duel.runDue();

        assertFalse(wave.isMember(steve.getUniqueId()));
        assertTrue(wave.isMember(alex.getUniqueId()));
        assertEquals(1, left.size());
        assertEquals(LeaveCause.KICKED, left.get(0).getCause());
        assertEquals(1, notifier.to(steve).size(), "the kicked player is told: " + notifier.getDeliveries());
        assertEquals("You fell 3 times", notifier.to(steve).get(0).getText());
        assertTrue(notifier.to(alex).isEmpty());
    }

    @Test
    void kickTellsAnOnlineMemberTheReasonAndAnswersFalseForAStranger() {
        TestFPlayerSender steve = duel.player("Steve");
        Group wave = ephemeral().create();
        wave.add(steve.getUniqueId());

        assertTrue(wave.kick(steve.getUniqueId(), FancyText.of("bye")));
        assertFalse(wave.kick(steve.getUniqueId(), FancyText.of("bye")), "no longer a member");
        assertEquals(1, notifier.to(steve).size());
        assertTrue(steve.anyMessageContains("bye"), "the recorder still sends the chat line");
    }

    @Test
    void joinThroughAGateThatDeniesKeepsThePlayerOutAndPostsNoJoin() {
        TestFPlayerSender steve = duel.player("Steve");
        Group wave = ephemeral().onJoin(Edge.gate(Gate.builder().require(arena).build())).create();
        List<GroupMemberJoinedEvent> joined = duel.recorded(GroupMemberJoinedEvent.class);
        List<GroupMemberPreJoinEvent> preJoin = duel.recorded(GroupMemberPreJoinEvent.class);
        wave.add(UUID.randomUUID());   //the owner's add skips the edge
        assertEquals(1, preJoin.size());
        assertEquals(1, joined.size());

        GateResult result = wave.join(steve);

        assertFalse(result.isPassed());
        assertEquals("not in the arena", result.explain().toPlainText());
        assertFalse(wave.isMember(steve.getUniqueId()));
        assertEquals(1, joined.size(), "no GroupMemberJoinedEvent for a denied join");
        assertEquals(1, preJoin.size(), "the gate refused before the pre event: " + preJoin);
    }

    @Test
    void aJoinGateChargesItsCostOnlyWhenTheJoinGoesThrough() {
        TestFPlayerSender steve = duel.player("Steve");
        TestFPlayerSender alex = duel.player("Alex");
        TestFPlayerSender broke = duel.player("Broke");
        Tokens tokens = new Tokens();
        tokens.balance.put(steve.getUniqueId(), 1);
        tokens.balance.put(alex.getUniqueId(), 1);
        Group wave = ephemeral().onJoin(Edge.gate(Gate.builder().cost(tokens).build())).create();
        ECEventSubscription<GroupMemberPreJoinEvent> refuseAlex = ECEventBus.global().subscribe(GroupMemberPreJoinEvent.class, event -> {
            if (event.getPlayer().equals(alex.getUniqueId())) {
                event.setCancelled(true);
                event.setDenyReason(FancyText.of("no Alex today"));
            }
        });

        GateResult paid = wave.join(steve);
        GateResult cancelled = wave.join(alex);
        GateResult noToken = wave.join(broke);
        refuseAlex.unsubscribe();

        assertEquals(GateResult.Outcome.PAID, paid.getOutcome());
        assertEquals(0, (int) tokens.balance.get(steve.getUniqueId()), "Steve paid his token");
        assertFalse(cancelled.isPassed());
        assertEquals("no Alex today", cancelled.explain().toPlainText());
        assertEquals(1, (int) tokens.balance.get(alex.getUniqueId()), "a join a listener cancelled gives the token back");
        assertFalse(noToken.isPassed(), "no token, no entry");
        assertEquals("you need a token", noToken.explain().toPlainText());
    }

    @Test
    void theLeaveGateCanKeepAMemberIn() {
        TestFPlayerSender steve = duel.player("Steve");
        Group wave = ephemeral().onLeave(Edge.gate(Gate.builder().require(arena).build())).create();
        wave.add(steve.getUniqueId());

        GateResult refused = wave.leave(steve);
        inArena.add(steve.getUniqueId());
        GateResult allowed = wave.leave(steve);

        assertFalse(refused.isPassed());
        assertTrue(allowed.isPassed());
        assertFalse(wave.isMember(steve.getUniqueId()));
    }

    @Test
    void joinNoticesReachTheirAudienceResolvedThroughTheNotifier() {
        TestFPlayerSender leader = duel.player("Leader");
        TestFPlayerSender alex = duel.player("Alex");
        TestFPlayerSender steve = duel.player("Steve");
        Group wave = ephemeral().leader(leader.getUniqueId())
                .onJoin(Edge.silent().notify(EdgeAudience.MEMBERS, "&a{player} joined {group}").notify(EdgeAudience.PLAYER, "welcome")
                        .notify(EdgeAudience.LEADER, "{player} is in"))
                .name("wave-7").create();
        wave.add(alex.getUniqueId());
        notifier.clear();

        assertTrue(wave.join(steve).isPassed());

        assertEquals(Collections.singletonList("welcome"), texts(notifier.to(steve)));
        assertEquals(Collections.singletonList("Steve joined wave-7"), texts(notifier.to(alex)));
        assertEquals(Arrays.asList("Steve joined wave-7", "Steve is in"), texts(notifier.to(leader)));
    }

    private static List<String> texts(List<RecordingNotifier.Delivery> deliveries) {
        String[] texts = new String[deliveries.size()];
        for (int i = 0; i < texts.length; i++) {
            texts[i] = deliveries.get(i).getText();
        }
        return Arrays.asList(texts);
    }

    @Test
    void theLastMemberLeavingDissolvesAWhenEmptyGroupAsEmpty() {
        Group wave = ephemeral().name("wave-1").dissolveWhen(Dissolution.whenEmpty()).create();
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        wave.add(steve);
        wave.add(alex);
        List<GroupDissolvedEvent> dissolved = duel.recorded(GroupDissolvedEvent.class);

        wave.remove(steve, LeaveCause.LEFT);
        assertTrue(dissolved.isEmpty(), "one member left: still alive");
        wave.remove(alex, LeaveCause.KICKED);

        assertEquals(1, dissolved.size());
        assertEquals(DissolveCause.EMPTY, dissolved.get(0).getCause());
        assertTrue(wave.isDissolved());
        assertNull(duel.getManager().get("wave-1"));
    }

    @Test
    void aTimedDissolutionIsSweptAsExpired() {
        Group wave = ephemeral().name("timed").dissolveWhen(Dissolution.after(Duration.ofMinutes(20))).create();
        UUID steve = UUID.randomUUID();
        wave.add(steve);
        List<GroupDissolvedEvent> dissolved = duel.recorded(GroupDissolvedEvent.class);

        duel.advance(Duration.ofMinutes(19));
        duel.runDue();
        assertFalse(wave.isDissolved());
        duel.advance(Duration.ofMinutes(2));
        duel.runDue();

        assertEquals(1, dissolved.size());
        assertEquals(DissolveCause.EXPIRED, dissolved.get(0).getCause());
        assertEquals(steve, dissolved.get(0).getLastMembers().iterator().next().getUuid());
        assertNull(duel.getManager().get("timed"));
    }

    @Test
    void aLeavePolicyTakesAQuittingMemberOutAndKeepLeavesThemOffline() {
        TestFPlayerSender steve = duel.player("Steve");
        Group leaves = ephemeral().create();
        Group keeps = ephemeral().onQuit(QuitPolicy.KEEP).create();
        leaves.add(steve.getUniqueId());
        keeps.add(steve.getUniqueId());
        List<GroupMemberLeftEvent> left = duel.recorded(GroupMemberLeftEvent.class);

        ECEventBus.global().post(new ECPlayerQuitEvent(new PlayerData(steve.getUniqueId(), steve.getName())));

        assertFalse(leaves.isMember(steve.getUniqueId()));
        assertTrue(keeps.isMember(steve.getUniqueId()));
        assertEquals(1, left.size());
        assertEquals(LeaveCause.QUIT, left.get(0).getCause());
    }

    @Test
    void theQuitListenerIsOnTheBusOnlyWhileAGroupLetsQuittersGo() {
        int before = ECEventBus.global().getSubscriptions(ECPlayerQuitEvent.class).size();
        Group leaves = ephemeral().create();
        assertEquals(before + 1, ECEventBus.global().getSubscriptions(ECPlayerQuitEvent.class).size());
        Group second = ephemeral().create();
        assertEquals(before + 1, ECEventBus.global().getSubscriptions(ECPlayerQuitEvent.class).size(), "one listener for every group");

        leaves.setQuitPolicy(QuitPolicy.KEEP);
        second.dissolve(DissolveCause.OWNER);

        assertEquals(before, ECEventBus.global().getSubscriptions(ECPlayerQuitEvent.class).size(), "the last one gone, the listener goes");
    }

    @Test
    void aRosterOnAStoredGroupIsRefusedAndSaysWhereRostersBelong() {
        Group stored = duel.group("alpha", team);

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> stored.setRoster(Roster.live(arena)));

        assertTrue(refused.getMessage().contains("Groups.ephemeral(owner, kind)"), refused.getMessage());
    }

    @Test
    void anEphemeralLeaderJoinsFirstAndLeads() {
        UUID leader = UUID.randomUUID();

        Group wave = ephemeral().leader(leader).create();

        assertEquals(leader, wave.getLeader());
        assertTrue(wave.isMember(leader));
    }
}
