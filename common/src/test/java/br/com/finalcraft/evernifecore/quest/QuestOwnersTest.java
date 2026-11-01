package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.group.DissolveCause;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.reward.RewardPaidEvent;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.quest.event.QuestExpireEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestFailEvent;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.finalcraft.evernifecore.testing.QuestWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owners that are not the player - a party, a region - and what they are paid through logic.reward. */
class QuestOwnersTest {

    private static final String GOLD = "fakequests:gold";
    private static final WorldLocPos INSIDE = WorldLocPos.of(5, 64, 5, "world");
    private static final WorldLocPos OUTSIDE = WorldLocPos.of(500, 64, 500, "world");

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private GroupWorld parties;
    private QuestWorld quests;
    private QuestEngine engine;
    private TestCurrency gold;
    private Group alpha;
    private TestFPlayerSender steve;
    private TestFPlayerSender alex;
    private TestFPlayerSender carl;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        engine = quests.getEngine();
        gold = Economies.currency(GOLD, CurrencyOwner.Kind.PLAYER);
        world.withCurrency(quests.getOwner(), gold);
        GroupKind party = parties.kind("party", GroupKind.Cardinality.ONE);
        steve = quests.player("Steve");
        alex = quests.player("Alex");
        carl = quests.player("Carl");
        alpha = parties.group("alpha", party);
        alpha.add(steve.getUniqueId());
        alpha.add(alex.getUniqueId());
        alpha.add(carl.getUniqueId());
        quests.region("arena", "world", 0, 0, 0, 20, 128, 20);
        quests.recorded(RewardPaidEvent.class); //recording from here on
    }

    @AfterEach
    void close() {
        quests.close();
        parties.close();
        world.close();
    }

    private ProgressOwner party() {
        return ProgressOwner.group(alpha.getId());
    }

    private void startFor(TestFPlayerSender player, String quest) {
        StartResult result = await(engine.start(quests.owner(player), quests.id(quest), QuestParams.NONE));
        assertTrue(result.isSuccess(), result.toString());
    }

    private void kills(TestFPlayerSender player, int count) {
        for (int i = 0; i < count; i++) {
            quests.post(quests.kill(player, "minecraft:zombie"));
        }
    }

    private void enterArena(TestFPlayerSender... players) {
        for (TestFPlayerSender player : players) {
            quests.move(player, INSIDE);
        }
    }

    /** The share of each RewardPaidEvent, by member, in the order paid. */
    private Map<UUID, BigDecimal> paidShares() {
        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
        for (RewardPaidEvent event : quests.recorded(RewardPaidEvent.class)) {
            shares.merge(event.getMember(), event.getShare(), BigDecimal::add);
        }
        return shares;
    }

    private void assertBalance(TestFPlayerSender player, String expected) {
        BigDecimal balance = gold.getBalance(CurrencyOwner.player(player.getUniqueId()));
        assertEquals(0, new BigDecimal(expected).compareTo(balance), player.getName() + "'s balance: " + balance);
    }

    private static void assertShare(Map<UUID, BigDecimal> shares, TestFPlayerSender player, String expected) {
        BigDecimal share = shares.get(player.getUniqueId());
        assertTrue(share != null && new BigDecimal(expected).compareTo(share) == 0, player.getName() + "'s share: " + share + " in " + shares);
    }

    // ------------------------------------------------------------------
    //  Group owner
    // ------------------------------------------------------------------

    @Test
    void aMemberStartsTheQuestOfTheirPartyAndEveryMemberCountsForIt() {
        engine.register(quests.parse("{id: raid, owner: {type: group}, stages: [{id: s, objectives: [{id: z, type: kill, entity: 'minecraft:zombie', goal: 3}]}]}"));

        startFor(steve, "raid");
        kills(alex, 1);
        kills(carl, 1);

        QuestState state = engine.getState(party(), quests.id("raid")).orElseThrow(AssertionError::new);
        assertEquals(2, state.getCount("z"), "a member's action counts for the party's instance");
        assertFalse(engine.getState(quests.owner(steve), quests.id("raid")).isPresent(), "nothing was started for Steve alone");
        assertEquals(1, state.getContributions().get(alex.getUniqueId().toString()).getCount("z"));
    }

    @Test
    void aGroupQuestIsRefusedToAPlayerInNoGroup() {
        engine.register(quests.parse("{id: raid, owner: {type: group}, stages: [{id: s, objectives: [{id: z, type: kill, goal: 3}]}]}"));
        TestFPlayerSender loner = quests.player("Loner");

        StartResult result = await(engine.start(quests.owner(loner), quests.id("raid"), QuestParams.NONE));

        assertFalse(result.isSuccess());
        assertTrue(result.toString().contains("in no such group"), result.toString());
    }

    @Test
    void aGroupQuestGatedByPermissionStartsForAMemberWhoHoldsIt() {
        engine.register(quests.parse("{id: raid, owner: {type: group}, gate: {require: [{type: permission, node: fq.raid}]},"
                + " stages: [{id: s, objectives: [{id: z, type: kill, goal: 3}]}]}"));
        steve.grant("fq.raid");

        assertEquals(QuestStatus.AVAILABLE, engine.getStatus(party(), quests.id("raid"), steve), "the list asks with the member who looks");
        assertEquals(QuestStatus.LOCKED, engine.getStatus(party(), quests.id("raid"), alex), "Alex lacks the permission");
        StartResult refused = await(engine.start(quests.owner(alex), quests.id("raid"), QuestParams.NONE));
        StartResult started = await(engine.start(quests.owner(steve), quests.id("raid"), QuestParams.NONE));

        assertEquals(StartResult.Reason.GATE, refused.getReason(), refused.toString());
        assertTrue(started.isSuccess(), started.toString());
        assertEquals(QuestStatus.ACTIVE, engine.getStatus(party(), quests.id("raid")));
    }

    // ------------------------------------------------------------------
    //  Rewards through logic.reward
    // ------------------------------------------------------------------

    @Test
    void aPartyQuestPaysEachMemberByWhatTheyCounted() {
        engine.register(quests.parse("{id: raid, owner: {type: group}, stages: [{id: s, objectives: [{id: z, type: kill, goal: 10}]}],"
                + " rewards: [{distribution: proportional, effects: [{type: money, amount: 10000, currency: '" + GOLD + "'}]}]}"));
        startFor(steve, "raid");

        kills(steve, 5);
        kills(alex, 3);
        kills(carl, 2);

        Map<UUID, BigDecimal> shares = paidShares();
        assertEquals(3, shares.size(), shares.toString());
        assertShare(shares, steve, "0.5");
        assertShare(shares, alex, "0.3");
        assertShare(shares, carl, "0.2");
        assertBalance(steve, "5000");
        assertBalance(alex, "3000");
        assertBalance(carl, "2000");
    }

    @Test
    void anAbsentMemberGetsADeliveryOnlyFromTheRuleThatSaysDeliver() {
        Deliveries deliveries = quests.logic().deliveries();
        engine.register(quests.parse("{id: raid, owner: {type: group}, stages: [{id: s, objectives: [{id: z, type: kill, goal: 3}]}], rewards: ["
                + "{absent: deliver, effects: [{type: money, amount: 100, currency: '" + GOLD + "'}]},"
                + "{absent: forfeit, effects: [{type: money, amount: 50, currency: '" + GOLD + "'}]}]}"));
        startFor(steve, "raid");
        kills(carl, 1);
        quests.logic().quit(carl);

        kills(steve, 2);

        assertEquals(1, await(deliveries.listOf(carl.getUniqueId())).size(), "deliver kept carl's share; forfeit kept nothing");
        assertBalance(steve, "150");
        assertBalance(alex, "150");
        assertBalance(carl, "0");
    }

    @Test
    void aLastActorRulePaysWhoseActionClosedTheObjective() {
        engine.register(quests.parse("{id: raid, owner: {type: group}, stages: [{id: s, objectives: [{id: z, type: kill, goal: 3}]}],"
                + " rewards: [{eligibility: last-actor, effects: [{type: money, amount: 70, currency: '" + GOLD + "'}]}]}"));
        startFor(steve, "raid");

        kills(steve, 2);
        kills(alex, 1);

        assertEquals(Arrays.asList(alex.getUniqueId()), new ArrayList<>(paidShares().keySet()));
        assertBalance(alex, "70");
        assertBalance(steve, "0");
    }

    @Test
    void completedObjectivePaysOnlyWhoCountedOnThatObjective() {
        engine.register(quests.parse("{id: raid, owner: {type: group}, stages: ["
                + "{id: a, objectives: [{id: ore, type: break-block, goal: 1}]},"
                + "{id: b, objectives: [{id: z, type: kill, goal: 1}]}],"
                + " rewards: [{eligibility: {type: completed-objective, id: ore}, effects: [{type: money, amount: 10, currency: '" + GOLD + "'}]}]}"));
        startFor(steve, "raid");

        quests.post(quests.breakBlock(carl, "minecraft:stone"));
        kills(alex, 1);

        assertEquals(Arrays.asList(carl.getUniqueId()), new ArrayList<>(paidShares().keySet()));
    }

    @Test
    void aPlayersOwnQuestPaysThePlayer() {
        engine.register(quests.parse("{id: solo, stages: [{id: s, objectives: [{id: z, type: kill, goal: 1}]}],"
                + " rewards: [{effects: [{type: money, amount: 300, currency: '" + GOLD + "'}]}]}"));
        startFor(alex, "solo");

        kills(alex, 1);

        assertEquals(Arrays.asList(alex.getUniqueId()), new ArrayList<>(paidShares().keySet()));
        assertBalance(alex, "300");
    }

    // ------------------------------------------------------------------
    //  The arena night (fixture B): owner gone, fail-when, window end
    // ------------------------------------------------------------------

    @Test
    void dissolvingThePartyFailsItsQuestAsOwnerGoneAndPaysWhoEarnedIt() {
        quests.registerFixtures("arena-night");
        enterArena(steve, alex, carl);
        startFor(steve, "arena-night");
        kills(steve, 60);
        kills(alex, 30);
        List<QuestFailEvent> failed = quests.recorded(QuestFailEvent.class);

        alpha.dissolve(DissolveCause.OWNER);

        assertEquals(1, failed.size());
        QuestFailEvent fail = failed.get(0);
        assertEquals(FailCause.OWNER_GONE, fail.getCause());
        assertEquals(QuestStatus.FAILED, fail.getState().getStatus());
        assertEquals(60, fail.getState().getContributions().get(steve.getUniqueId().toString()).getCount("mobs"));
        assertEquals(30, fail.getState().getContributions().get(alex.getUniqueId().toString()).getCount("mobs"));
        assertEquals(Arrays.asList(steve.getUniqueId()), new ArrayList<>(paidShares().keySet()), "only a contribution of 50 or more is paid");
        assertFalse(engine.getState(party(), quests.id("arena-night")).isPresent(), "a dissolved party's states go with it");
    }

    @Test
    void theWholePartyLeavingTheArenaFailsTheQuest() {
        quests.registerFixtures("arena-night");
        enterArena(steve, alex, carl);
        startFor(steve, "arena-night");
        quests.move(steve, OUTSIDE);
        quests.move(alex, OUTSIDE);
        quests.advance(Duration.ofSeconds(61));
        quests.runDue();
        assertEquals(QuestStatus.ACTIVE, engine.getState(party(), quests.id("arena-night")).get().getStatus(), "Carl is still inside");

        quests.move(carl, OUTSIDE);
        quests.advance(Duration.ofSeconds(61));
        quests.runDue();

        QuestState state = engine.getState(party(), quests.id("arena-night")).orElseThrow(AssertionError::new);
        assertEquals(QuestStatus.FAILED, state.getStatus());
        assertEquals(FailCause.CONDITION, state.getFailCause());
    }

    @Test
    void theEndOfTheWindowPaysItsRankingAndExpiresTheInstance() {
        quests.registerFixtures("arena-night");
        enterArena(steve, alex, carl);
        startFor(steve, "arena-night");
        kills(alex, 7);
        kills(steve, 3);
        List<QuestExpireEvent> expired = quests.recorded(QuestExpireEvent.class);

        quests.advance(Duration.ofHours(1));
        quests.runDue();

        assertEquals(1, expired.size());
        assertEquals(QuestStatus.EXPIRED, engine.getState(party(), quests.id("arena-night")).get().getStatus());
        assertEquals(Arrays.asList(alex.getUniqueId()), new ArrayList<>(paidShares().keySet()), "the top contributor of the night");
        StartResult late = await(engine.start(quests.owner(steve), quests.id("arena-night"), QuestParams.NONE));
        assertFalse(late.isSuccess(), "the window closed");
    }

    // ------------------------------------------------------------------
    //  Region owner
    // ------------------------------------------------------------------

    @Test
    void aRegionOwnedQuestCountsTheActionsOfWhoeverIsInside() {
        Region arena = quests.regions().get("arena");
        engine.register(quests.parse("{id: cleanup, owner: region, stages: [{id: s, objectives: [{id: z, type: kill, goal: 5}]}]}"));
        assertTrue(await(engine.start(ProgressOwner.region(arena.getId()), quests.id("cleanup"), QuestParams.NONE)).isSuccess());

        quests.post(quests.kill(steve, "minecraft:zombie", INSIDE));
        quests.post(quests.kill(alex, "minecraft:zombie", INSIDE));
        quests.post(quests.kill(carl, "minecraft:zombie", OUTSIDE));

        QuestState state = engine.getState(ProgressOwner.region(arena.getId()), quests.id("cleanup")).orElseThrow(AssertionError::new);
        assertEquals(2, state.getCount("z"), "the kill outside the arena does not count for it");
    }
}
