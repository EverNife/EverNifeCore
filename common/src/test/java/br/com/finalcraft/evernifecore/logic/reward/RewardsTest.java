package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.group.GroupRole;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.EffectList;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.logic.audit.AuditQuery;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Rewards.pay} over a real group (GroupWorld) and the logic engine's deliveries and audit log (LogicWorld), with
 * the five modes the owner listed as named cases.
 */
class RewardsTest {

    private static final String GOLD = "fakeduel:gold";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private GroupWorld parties;
    private LogicWorld duel;
    private GroupKind party;
    private TestCurrency gold;
    private Deliveries deliveries;
    private TestFPlayerSender steve;
    private TestFPlayerSender alex;
    private UUID offline;
    private Group alpha;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        duel = LogicWorld.open("FakeDuel", tempDir.resolve("duel"));
        gold = Economies.currency(GOLD, CurrencyOwner.Kind.PLAYER);
        world.withCurrency(duel.getOwner(), gold);
        deliveries = duel.deliveries();
        party = parties.kind("party", GroupKind.Cardinality.ONE, GroupRole.of("officer", 10));
        steve = parties.player("Steve");
        alex = parties.player("Alex");
        offline = UUID.randomUUID();
        alpha = parties.group("alpha", party);
        alpha.add(steve.getUniqueId());
        alpha.add(alex.getUniqueId());
        alpha.add(offline);
        duel.recorded(RewardPaidEvent.class); //recording from here on
    }

    @AfterEach
    void close() {
        duel.close();
        parties.close();
        world.close();
    }

    // ------------------------------------------------------------------
    //  The five modes
    // ------------------------------------------------------------------

    @Test
    void theLeaderTakesTheWholeReward() {
        alpha.setLeader(offline);
        RewardRule rule = RewardRule.builder().eligibility(Eligibility.role("leader")).distribution(Distribution.each())
                .absent(Absence.deliver()).effects(gold(900)).build();

        Map<UUID, BigDecimal> paid = pay(rule, context().build());

        assertShares(paid, offline, "1");
        assertPaidEvents(offline);
        assertEquals(1, pending(offline).size(), "the offline leader's whole reward waits as one delivery");
        assertBalance(steve.getUniqueId(), "0");
        assertBalance(alex.getUniqueId(), "0");
        assertTrue(duel.recorded(RewardPaidEvent.class).get(0).isDelivered());
    }

    @Test
    void theLastActorTakesTheWholeReward() {
        RewardRule rule = RewardRule.builder().eligibility(Eligibility.lastActor()).distribution(Distribution.each())
                .absent(Absence.deliver()).effects(gold(900)).build();

        Map<UUID, BigDecimal> paid = pay(rule, context().lastActor(alex.getUniqueId()).build());

        assertShares(paid, alex.getUniqueId(), "1");
        assertPaidEvents(alex.getUniqueId());
        assertBalance(alex.getUniqueId(), "900");
        assertBalance(steve.getUniqueId(), "0");
        assertTrue(pending(offline).isEmpty(), "the offline member was not the last actor");
    }

    @Test
    void splitEqualAmongOnlineMembersForfeitsTheAbsent() {
        RewardRule rule = RewardRule.builder().eligibility(Eligibility.online()).distribution(Distribution.splitEqual())
                .absent(Absence.forfeit()).effects(gold(900)).build();

        Map<UUID, BigDecimal> paid = pay(rule, context().build());

        assertShares(paid, steve.getUniqueId(), "0.5", alex.getUniqueId(), "0.5");
        assertPaidEvents(steve.getUniqueId(), alex.getUniqueId());
        assertBalance(steve.getUniqueId(), "450");
        assertBalance(alex.getUniqueId(), "450");
        assertTrue(pending(offline).isEmpty(), "the absent forfeits: no delivery");
        assertBalance(offline, "0");
    }

    @Test
    void proportionalFollowsTheContributionsTheCallerGave() {
        TestFPlayerSender carl = parties.player("Carl");
        alpha.add(carl.getUniqueId());
        RewardRule rule = RewardRule.builder().eligibility(Eligibility.contributionAtLeast(BigDecimal.ONE))
                .distribution(Distribution.proportional()).absent(Absence.deliver()).effects(gold(1000)).build();

        Map<UUID, BigDecimal> paid = pay(rule, context().contributions(contributions(steve.getUniqueId(), 30, alex.getUniqueId(), 10,
                offline, 60, carl.getUniqueId(), 0)).build());

        assertShares(paid, steve.getUniqueId(), "0.3", alex.getUniqueId(), "0.1", offline, "0.6");
        assertPaidEvents(steve.getUniqueId(), alex.getUniqueId(), offline);
        assertBalance(steve.getUniqueId(), "300");
        assertBalance(alex.getUniqueId(), "100");
        assertBalance(carl.getUniqueId(), "0");
        List<Delivery> kept = pending(offline);
        assertEquals(1, kept.size(), "the offline contributor's share waits as one delivery");
        assertTrue(kept.get(0).getEffects().toString().contains("600"), kept.toString());
    }

    @Test
    void splitEqualIncludingOfflineDeliversTheAbsentShare() {
        RewardRule rule = RewardRule.builder().eligibility(Eligibility.anyMember()).distribution(Distribution.splitEqual())
                .absent(Absence.deliver()).effects(gold(900)).build();

        Map<UUID, BigDecimal> paid = pay(rule, context().build());

        assertShares(paid, steve.getUniqueId(), "0.3333333333", alex.getUniqueId(), "0.3333333333", offline, "0.3333333333");
        BigDecimal sum = paid.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(sum.compareTo(BigDecimal.ONE) <= 0, "a split never pays more than the whole: " + sum);
        assertPaidEvents(steve.getUniqueId(), alex.getUniqueId(), offline);
        assertBalance(steve.getUniqueId(), "300"); //the currency rounds 299.99999997 to its two places
        assertEquals(1, pending(offline).size());
    }

    // ------------------------------------------------------------------
    //  The rest of the contract
    // ------------------------------------------------------------------

    @Test
    void parseReadsTheQuestFormLikeTheBuilder() throws Exception {
        RewardRule parsed = RewardRule.parse(duel.getOwner(), node("{'eligibility': [{'type': 'contribution-share', 'at-least': '60%'}],"
                + " 'distribution': {'type': 'proportional'}, 'absent': 'deliver', 'leaver': 'forfeits',"
                + " 'effects': [{'type': 'money', 'amount': 1000, 'currency': '" + GOLD + "'}]}"));
        RewardRule built = RewardRule.builder().eligibility(Eligibility.contributionShare(new BigDecimal("0.6")))
                .distribution(Distribution.proportional()).absent(Absence.deliver()).membership(RewardMembership.of(false, false))
                .effects(gold(1000)).build();

        assertEquals(built.getEligibility().toString(), parsed.getEligibility().toString());
        assertEquals(Distribution.proportional(), parsed.getDistribution());
        assertEquals(Absence.deliver(), parsed.getAbsent());
        assertEquals(Moment.onComplete(), parsed.getMoment(), "the default moment");
        assertFalse(parsed.getMembership().leaverKeeps());
        //fair share = 100 / 3: Steve (50) and Alex (40) reach 60% of it, the offline member (10) does not
        RewardContext ctx = context().contributions(contributions(steve.getUniqueId(), 50, alex.getUniqueId(), 40, offline, 10)).build();
        Map<UUID, BigDecimal> fromParsed = pay(parsed, ctx);
        assertShares(fromParsed, steve.getUniqueId(), "0.5555555555", alex.getUniqueId(), "0.4444444444");
        assertEquals(fromParsed, pay(built, ctx));

        TypeRegistryException misplaced = assertThrows(TypeRegistryException.class,
                () -> RewardRule.parse(duel.getOwner(), node("{'distribution': 'deliver'}")));
        assertTrue(misplaced.getMessage().contains("ABSENCE") && misplaced.getMessage().contains("move it under"), misplaced.getMessage());
    }

    @Test
    void aMomentThatDoesNotTakeTheTriggerPaysNothingAndPostsNothing() {
        RewardRule rule = RewardRule.builder().moment(Moment.onWindowEnd()).effects(gold(900)).build();

        Map<UUID, BigDecimal> paid = pay(rule, context().trigger(RewardTrigger.ON_COMPLETE).build());

        assertTrue(paid.isEmpty());
        assertTrue(duel.recorded(RewardPaidEvent.class).isEmpty());
        assertBalance(steve.getUniqueId(), "0");
        assertTrue(pending(offline).isEmpty());
        assertShares(pay(rule, context().trigger(RewardTrigger.ON_WINDOW_END).build()), steve.getUniqueId(), "1", alex.getUniqueId(), "1",
                offline, "1");
    }

    @Test
    void aLeaverWhoForfeitsIsNoLongerACandidate() {
        TestFPlayerSender gone = parties.player("Gone");
        Map<UUID, BigDecimal> book = contributions(steve.getUniqueId(), 10, gone.getUniqueId(), 30);
        RewardRule.Builder rule = RewardRule.builder().eligibility(Eligibility.contributionAtLeast(BigDecimal.ONE))
                .distribution(Distribution.proportional()).effects(gold(400));

        Map<UUID, BigDecimal> keeps = pay(rule.membership(RewardMembership.of(true, false)).build(), context().contributions(book).build());
        Map<UUID, BigDecimal> forfeits = pay(rule.membership(RewardMembership.of(false, false)).build(), context().contributions(book).build());

        assertShares(keeps, steve.getUniqueId(), "0.25", gone.getUniqueId(), "0.75");
        assertShares(forfeits, steve.getUniqueId(), "1");
        assertBalance(gone.getUniqueId(), "300");
    }

    @Test
    void everyPaymentPostsAnEventAndWritesALineInTheCallersAuditLog() throws Exception {
        AuditLog log = duel.auditLog();
        RewardRule rule = RewardRule.builder().distribution(Distribution.each()).effects(gold(10)).build();

        pay(rule, context().build());

        List<RewardPaidEvent> events = duel.recorded(RewardPaidEvent.class);
        assertEquals(3, events.size(), events.toString());
        for (RewardPaidEvent event : events) {
            assertEquals(caller(), event.getCaller());
            assertEquals(ProgressOwner.group(alpha.getId()), event.getOwner());
            assertEquals(rule, event.getRule());
            assertEquals(event.getMember().equals(offline), event.isDelivered());
        }
        List<AuditEntry> lines = await(log.query(new AuditQuery().kind(AuditKind.REWARD_PAID)));
        for (long deadline = System.currentTimeMillis() + 5000; lines.size() < 3 && System.currentTimeMillis() < deadline; ) {
            Thread.sleep(20); //the log writes off the paying thread
            lines = await(log.query(new AuditQuery().kind(AuditKind.REWARD_PAID)));
        }
        assertEquals(3, lines.size(), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> offline.equals(line.getPlayer()) && line.getText().contains("pending delivery")), lines.toString());
        assertTrue(lines.stream().allMatch(line -> ("group:" + alpha.getId()).equals(line.getSubject())), lines.toString());
    }

    @Test
    void aRoleRewardOnAnOwnerThatIsNotAGroupIsRefusedWithTheWayOut() {
        RewardRule rule = RewardRule.builder().eligibility(Eligibility.role("leader")).effects(gold(10)).build();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Rewards.pay(rule, RewardContext.builder(caller(), ProgressOwner.player(steve.getUniqueId())).build()));

        assertTrue(refused.getMessage().contains("has no roles") && refused.getMessage().contains("ProgressOwner.group(id)"), refused.getMessage());
    }

    @Test
    void aRoleOtherThanLeaderReadsTheGroupsRoles() {
        alpha.addRole(alex.getUniqueId(), party.getRole("officer"));
        RewardRule rule = RewardRule.builder().eligibility(Eligibility.role("officer")).effects(gold(50)).build();

        assertShares(pay(rule, context().build()), alex.getUniqueId(), "1");
        assertBalance(alex.getUniqueId(), "50");
    }

    @Test
    void redistributeSplitsTheAbsentShareAmongTheOnlineEligible() {
        RewardRule rule = RewardRule.builder().distribution(Distribution.splitEqual()).absent(Absence.redistribute()).effects(gold(900)).build();

        Map<UUID, BigDecimal> paid = pay(rule, context().build());

        assertShares(paid, steve.getUniqueId(), "0.4999999999", alex.getUniqueId(), "0.4999999999");
        assertTrue(pending(offline).isEmpty());
        assertEquals(2, duel.recorded(RewardPaidEvent.class).size());
    }

    @Test
    void aLastActorRewardWithoutALastActorPaysNobody() {
        RewardRule rule = RewardRule.builder().eligibility(Eligibility.lastActor()).effects(gold(900)).build();

        assertTrue(pay(rule, context().build()).isEmpty());
        assertTrue(duel.recorded(RewardPaidEvent.class).isEmpty());
    }

    @Test
    void topAndTiersRankTheContributionsHighestFirst() {
        Map<UUID, BigDecimal> book = contributions(steve.getUniqueId(), 5, alex.getUniqueId(), 20, offline, 10);

        Map<UUID, BigDecimal> tiers = pay(RewardRule.builder().distribution(Distribution.tiers(new BigDecimal("0.5"), new BigDecimal("0.3")))
                .effects(gold(100)).build(), context().contributions(book).build());
        Map<UUID, BigDecimal> top = pay(RewardRule.builder().eligibility(Eligibility.top(1)).distribution(Distribution.topTakesAll())
                .effects(gold(100)).build(), context().contributions(book).build());

        assertShares(tiers, alex.getUniqueId(), "0.5", offline, "0.3");
        assertShares(top, alex.getUniqueId(), "1");
        IllegalArgumentException tooMuch = assertThrows(IllegalArgumentException.class,
                () -> Distribution.tiers(new BigDecimal("0.7"), new BigDecimal("0.4")));
        assertTrue(tooMuch.getMessage().contains("100% or less"), tooMuch.getMessage());
    }

    // ------------------------------------------------------------------

    private Caller caller() {
        return duel.caller("boss");
    }

    private RewardContext.Builder context() {
        return RewardContext.builder(caller(), ProgressOwner.group(alpha.getId()));
    }

    private Map<UUID, BigDecimal> pay(RewardRule rule, RewardContext ctx) {
        return await(Rewards.pay(rule, ctx));
    }

    private EffectList gold(int amount) {
        return Effects.parseList(duel.getOwner(), node("[{'type': 'money', 'amount': " + amount + ", 'currency': '" + GOLD + "'}]"),
                ZoneOffset.UTC);
    }

    private List<Delivery> pending(UUID member) {
        return await(deliveries.listOf(member));
    }

    private void assertBalance(UUID member, String expected) {
        BigDecimal balance = gold.getBalance(CurrencyOwner.player(member));
        assertEquals(0, new BigDecimal(expected).compareTo(balance), "balance of " + member + ": " + balance);
    }

    private void assertPaidEvents(UUID... members) {
        List<UUID> seen = new ArrayList<>();
        for (RewardPaidEvent event : duel.recorded(RewardPaidEvent.class)) {
            seen.add(event.getMember());
        }
        assertEquals(new HashMap<>(count(Arrays.asList(members))), new HashMap<>(count(seen)), "one RewardPaidEvent per paid member");
    }

    private static Map<UUID, Integer> count(List<UUID> members) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID member : members) {
            counts.merge(member, 1, Integer::sum);
        }
        return counts;
    }

    private static void assertShares(Map<UUID, BigDecimal> paid, Object... memberThenShare) {
        Map<UUID, BigDecimal> expected = new LinkedHashMap<>();
        for (int i = 0; i < memberThenShare.length; i += 2) {
            expected.put((UUID) memberThenShare[i], new BigDecimal((String) memberThenShare[i + 1]));
        }
        assertEquals(expected.keySet(), paid.keySet(), "who was paid: " + paid);
        for (Map.Entry<UUID, BigDecimal> share : expected.entrySet()) {
            assertEquals(0, share.getValue().compareTo(paid.get(share.getKey())), "share of " + share.getKey() + ": " + paid);
        }
    }

    private static Map<UUID, BigDecimal> contributions(Object... memberThenAmount) {
        Map<UUID, BigDecimal> book = new LinkedHashMap<>();
        for (int i = 0; i < memberThenAmount.length; i += 2) {
            book.put((UUID) memberThenAmount[i], BigDecimal.valueOf((Integer) memberThenAmount[i + 1]));
        }
        return book;
    }

    private static JsonNode node(String singleQuotedJson) {
        try {
            return new ObjectMapper().readTree(singleQuotedJson.replace('\'', '"'));
        } catch (IOException unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
