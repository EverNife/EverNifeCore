package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.logic.audit.AuditQuery;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Senders;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The keys of a reward step beyond who-and-how-much: marks, any-of/not, once, name, offline on every effect, target. */
class RewardStepsTest {

    private static final String GOLD = "fakeraid:gold";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld raid;
    private TestCurrency gold;
    private TestFPlayerSender steve;
    private TestFPlayerSender alex;
    private RewardOccasion occasion;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        raid = LogicWorld.open("FakeRaid", tempDir.resolve("raid"));
        gold = Economies.currency(GOLD, CurrencyOwner.Kind.PLAYER);
        world.withCurrency(raid.getOwner(), gold);
        raid.deliveries();
        steve = raid.player("Steve");
        alex = raid.player("Alex");
        occasion = RewardOccasion.builder(raid.getOwner(), "raid").marks("alive", "dead", "mvp").variables("stage").build();
    }

    @AfterEach
    void close() {
        raid.close();
        world.close();
    }

    // ------------------------------------------------------------------
    //  mark, any-of, not
    // ------------------------------------------------------------------

    @Test
    void markPaysOnlyTheParticipantsCarryingIt() {
        RewardScene scene = RewardScene.builder(occasion)
                .participant(steve.getUniqueId(), "Steve", BigDecimal.ONE, "alive")
                .participant(alex.getUniqueId(), "Alex", BigDecimal.ONE, "dead")
                .build();

        Map<UUID, BigDecimal> paid = pay(step("{'eligibility': {'type': 'mark', 'mark': 'alive'}," + gold(100) + "}"), scene);

        assertEquals(Collections.singleton(steve.getUniqueId()), paid.keySet());
        assertBalance(alex.getUniqueId(), "0");
    }

    @Test
    void aMarkTheOccasionDoesNotDeclareIsRefusedListingTheDeclared() {
        TypeRegistryException refused = assertThrows(TypeRegistryException.class,
                () -> step("{'eligibility': {'type': 'mark', 'mark': 'ghost'}," + gold(100) + "}"));

        assertTrue(refused.getMessage().contains("'ghost'") && refused.getMessage().contains("[alive, dead, mvp]"), refused.getMessage());
    }

    @Test
    void aMarkReadWithoutAnOccasionIsRefusedSayingItNeedsOne() {
        TypeRegistryException refused = assertThrows(TypeRegistryException.class, () -> RewardRule.parse(raid.getOwner(),
                node("{'eligibility': {'type': 'any-of', 'of': [{'type': 'online'}, {'type': 'mark', 'mark': 'alive'}]}," + gold(100) + "}")));

        assertTrue(refused.getMessage().contains("occasion"), refused.getMessage());
    }

    @Test
    void anyOfAcceptsWhoPassesOneOfItsEligibilities() {
        UUID bob = UUID.randomUUID();
        RewardScene scene = RewardScene.builder(occasion)
                .participant(steve.getUniqueId(), "Steve", BigDecimal.ONE, "mvp")
                .participant(alex.getUniqueId(), "Alex", BigDecimal.TEN)
                .participant(bob, "Bob", BigDecimal.ONE)
                .build();

        Map<UUID, BigDecimal> paid = pay(step("{'eligibility': {'type': 'any-of', 'of': [{'type': 'mark', 'mark': 'mvp'},"
                + " {'type': 'contribution-at-least', 'min': 5}]}," + gold(100) + "}"), scene);

        assertEquals(Arrays.asList(steve.getUniqueId(), alex.getUniqueId()), Arrays.asList(paid.keySet().toArray()));
    }

    @Test
    void notInvertsAMark() {
        RewardScene scene = RewardScene.builder(occasion)
                .participant(steve.getUniqueId(), "Steve", BigDecimal.ONE, "alive")
                .participant(alex.getUniqueId(), "Alex", BigDecimal.ONE, "dead")
                .build();

        Map<UUID, BigDecimal> paid = pay(step("{'eligibility': {'type': 'not', 'of': {'type': 'mark', 'mark': 'alive'}}," + gold(100) + "}"), scene);

        assertEquals(Collections.singleton(alex.getUniqueId()), paid.keySet());
    }

    @Test
    void anyOfReadsAndPaysInAQuestRuleWithoutAnOccasion() {
        UUID bob = UUID.randomUUID();
        UUID carl = UUID.randomUUID();
        RewardRule rule = RewardRule.parse(raid.getOwner(), node("{'eligibility': {'type': 'any-of', 'of': [{'type': 'online'},"
                + " {'type': 'contribution-at-least', 'min': 5}]}, 'absent': 'forfeit'," + gold(100) + "}"));
        Map<UUID, BigDecimal> book = new LinkedHashMap<>();
        book.put(steve.getUniqueId(), BigDecimal.ONE);
        book.put(bob, BigDecimal.TEN);
        book.put(carl, BigDecimal.ONE);

        Map<UUID, BigDecimal> paid = await(Rewards.pay(rule, RewardContext.builder(caller(), ProgressOwner.global()).contributions(book).build()));

        assertEquals(Collections.singleton(steve.getUniqueId()), paid.keySet(), "Bob passes but is offline and forfeits; Carl passes neither");
        assertBalance(steve.getUniqueId(), "100");
    }

    // ------------------------------------------------------------------
    //  once
    // ------------------------------------------------------------------

    @Test
    void onceRunsItsCommandOneTimeFromTheConsoleWithTheSceneVariables() {
        RewardScene scene = RewardScene.builder(occasion)
                .participant(steve.getUniqueId(), "Steve", BigDecimal.ONE, "alive")
                .participant(alex.getUniqueId(), "Alex", BigDecimal.ONE, "alive")
                .participant(UUID.randomUUID(), "Bob", BigDecimal.ONE, "dead")
                .participant(UUID.randomUUID(), "Carl", BigDecimal.ONE)
                .variable("stage", 5)
                .build();

        Map<UUID, BigDecimal> paid = pay(step("{'once': true, 'effects': [{'type': 'command', 'command': 'broadcast Stage {stage} cleared'}]}"), scene);

        assertTrue(paid.isEmpty(), "once pays nobody in particular: " + paid);
        assertEquals(Collections.singletonList("console: broadcast Stage 5 cleared"), commands());
    }

    @Test
    void onceRunsEvenWithNoParticipant() {
        RewardScene empty = RewardScene.builder(occasion).variable("stage", 2).build();

        pay(step("{'once': true, 'effects': [{'type': 'command', 'command': 'broadcast Stage {stage} lost'}]}"), empty);

        assertEquals(Collections.singletonList("console: broadcast Stage 2 lost"), commands());
    }

    @Test
    void onceWithDistributionOrAbsentIsRefusedSayingWhy() {
        TypeRegistryException distribution = assertThrows(TypeRegistryException.class,
                () -> step("{'once': true, 'distribution': 'proportional'," + gold(100) + "}"));
        TypeRegistryException absent = assertThrows(TypeRegistryException.class, () -> step("{'once': true, 'absent': 'forfeit'," + gold(100) + "}"));

        assertTrue(distribution.getMessage().contains("'distribution'") && distribution.getMessage().contains("once"), distribution.getMessage());
        assertTrue(absent.getMessage().contains("'absent'") && absent.getMessage().contains("once"), absent.getMessage());
    }

    @Test
    void onceWithEligibilityLeaverOrJoinerIsRefusedSayingWhy() {
        for (String key : Arrays.asList("'eligibility': 'online'", "'leaver': 'forfeits'", "'joiner': 'inherits-zero'")) {
            TypeRegistryException refused = assertThrows(TypeRegistryException.class, () -> step("{'once': true, " + key + "," + gold(100) + "}"));
            String name = key.substring(1, key.indexOf('\'', 1));
            assertTrue(refused.getMessage().contains("'" + name + "'") && refused.getMessage().contains("Remove"), refused.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  offline on every effect
    // ------------------------------------------------------------------

    @Test
    void aMessageOnLoginWaitsForTheLoginAndArrivesThen() {
        UUID bob = UUID.randomUUID();
        RewardScene scene = RewardScene.builder(occasion).participant(bob, "Bob", BigDecimal.ONE).variable("stage", 3).build();

        pay(step("{'effects': [{'type': 'message', 'text': 'Stage {stage} paid', 'offline': 'on-login'}]}"), scene);

        assertEquals(1, pending(bob).size(), "the message waits as a delivery");
        TestFPlayerSender login = Senders.player("Bob", bob);
        login.assertNoMessageSent();

        await(raid.deliveries().claimAll(login));

        login.assertAnyMessageContains("Stage 3 paid");
        assertTrue(pending(bob).isEmpty());
    }

    @Test
    void aMessageThatSkipsAnOfflineMemberIsNotAppliedAndIsAudited() throws Exception {
        AuditLog log = raid.auditLog();
        UUID bob = UUID.randomUUID();
        RewardScene scene = RewardScene.builder(occasion).participant(bob, "Bob", BigDecimal.ONE).build();

        pay(step("{'effects': [{'type': 'message', 'text': 'hello', 'offline': 'skip'}]}"), scene);

        assertTrue(pending(bob).isEmpty());
        raid.awaitAudit();
        List<AuditEntry> skipped = await(log.query(new AuditQuery().kind(AuditKind.EFFECT_SKIPPED)));
        assertEquals(1, skipped.size(), skipped.toString());
        assertEquals(bob, skipped.get(0).getPlayer());
        assertTrue(await(log.query(new AuditQuery().kind(AuditKind.EFFECT_FAILED))).isEmpty(), "a skip is no failure");
    }

    @Test
    void aDeliveryKeepsTheSceneVariablesAndTheParticipantsName() {
        UUID bob = UUID.randomUUID();
        RewardScene scene = RewardScene.builder(occasion).participant(bob, "RaidBob", BigDecimal.ONE).variable("stage", 5).build();

        pay(step("{'effects': [{'type': 'message', 'text': 'Stage {stage}, {player}', 'offline': 'on-login'},"
                + " {'type': 'command', 'command': 'give {player} stage {stage}', 'offline': 'on-login'}]}"), scene);
        TestFPlayerSender login = Senders.player("LoginBob", bob);
        await(raid.deliveries().claimAll(login));

        login.assertAnyMessageContains("Stage 5, RaidBob");
        assertEquals(Collections.singletonList("console: give RaidBob stage 5"), commands());
    }

    // ------------------------------------------------------------------
    //  target, name
    // ------------------------------------------------------------------

    @Test
    void aTargetInsideAStepIsWarnedNamingTheStep() {
        List<String> logged = Logs.capture(() -> step("{'name': 'everyone-online', 'eligibility': {'type': 'mark', 'mark': 'alive'},"
                + " 'effects': [{'type': 'message', 'text': 'hi', 'target': 'online-players'}]}"));

        assertTrue(logged.stream().anyMatch(line -> line.contains("'everyone-online'") && line.contains("target: online-players")), logged.toString());
    }

    @Test
    void theNameOfAStepIsInTheAuditLineOfItsPayment() throws Exception {
        AuditLog log = raid.auditLog();
        RewardScene scene = RewardScene.builder(occasion).participant(steve.getUniqueId(), "Steve", BigDecimal.ONE, "alive").build();

        pay(step("{'name': 'bonus-vivos', 'eligibility': {'type': 'mark', 'mark': 'alive'}," + gold(100) + "}"), scene);

        raid.awaitAudit();
        List<AuditEntry> lines = await(log.query(new AuditQuery().kind(AuditKind.REWARD_PAID)));
        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.get(0).getText().contains("bonus-vivos"), lines.toString());
    }

    // ------------------------------------------------------------------

    private RewardRule step(String json) {
        return RewardRule.parse(raid.getOwner(), occasion, node(json));
    }

    private Map<UUID, BigDecimal> pay(RewardRule rule, RewardScene scene) {
        return await(Rewards.pay(rule, RewardContext.builder(caller(), ProgressOwner.global()).scene(scene).build()));
    }

    private static String gold(int amount) {
        return " 'effects': [{'type': 'money', 'amount': " + amount + ", 'currency': '" + GOLD + "'}]";
    }

    private Caller caller() {
        return raid.caller("raid");
    }

    private List<String> commands() {
        return world.platform().getTestPlayerState().getCommands();
    }

    private List<Delivery> pending(UUID player) {
        return await(raid.deliveries().listOf(player));
    }

    private void assertBalance(UUID player, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(gold.getBalance(CurrencyOwner.player(player))),
                player + " has " + gold.getBalance(CurrencyOwner.player(player)));
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
