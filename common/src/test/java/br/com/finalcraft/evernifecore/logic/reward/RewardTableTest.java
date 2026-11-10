package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.logic.audit.AuditQuery;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.await;
import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A reward table: steps in order, the receipt, a preview that applies nothing, {amount}/{share}, and the phase keys. */
class RewardTableTest {

    private static final String GOLD = "fakeraid:gold";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld raid;
    private TestCurrency gold;
    private TestFPlayerSender ana;
    private TestFPlayerSender bia;
    private UUID duda;
    private RewardOccasion occasion;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        raid = LogicWorld.open("FakeRaid", tempDir.resolve("raid"));
        gold = Economies.currency(GOLD, CurrencyOwner.Kind.PLAYER);
        world.withCurrency(raid.getOwner(), gold);
        raid.deliveries();
        ana = raid.player("Ana");
        bia = raid.player("Bia");
        duda = UUID.randomUUID();
        occasion = RewardOccasion.builder(raid.getOwner(), "stage-won").phase("stage").marks("alive", "dead").variables("stage").build();
    }

    @AfterEach
    void close() {
        raid.close();
        world.close();
    }

    // ------------------------------------------------------------------
    //  receipt, preview, {amount}, {share}
    // ------------------------------------------------------------------

    @Test
    void previewAnswersTheReceiptOfPayAndAppliesNothing() throws Exception {
        AuditLog log = raid.auditLog();
        raid.recorded(RewardPaidEvent.class); //recording from here on
        RewardTable table = pool("{'type': 'message', 'text': 'pool'}");
        RewardContext ctx = context(scene());

        RewardReceipt preview = table.preview(ctx);

        assertBalance(ana.getUniqueId(), "0");
        assertTrue(await(raid.deliveries().listOf(duda)).isEmpty(), "no delivery kept");
        assertTrue(raid.recorded(RewardPaidEvent.class).isEmpty(), "no RewardPaidEvent");
        raid.awaitAudit();
        assertTrue(await(log.query(new AuditQuery().kind(AuditKind.REWARD_PAID))).isEmpty(), "no audit line");
        ana.assertNoMessageSent();

        RewardReceipt paid = await(table.pay(ctx));

        assertEquals(paid.toString(), preview.toString());
        assertEquals(RewardReceipt.Status.DELIVERED, preview.getStep(0).getLine(duda).getStatus());
        assertBalance(ana.getUniqueId(), "900");
        assertEquals(3, raid.recorded(RewardPaidEvent.class).size(), "pay posts what preview did not");
    }

    @Test
    void amountIsWhatTheStepsMoneyGaveTheMember() {
        await(pool("{'type': 'message', 'text': 'Your part: {amount}'}").pay(context(scene())));

        ana.assertAnyMessageContains("Your part: 900");
        bia.assertAnyMessageContains("Your part: 600");
    }

    @Test
    void shareIsTheMembersPartOfTheStep() {
        RewardReceipt receipt = await(pool("{'type': 'message', 'text': 'Your share: {share}'}").pay(context(scene())));

        ana.assertAnyMessageContains("Your share: 0.45");
        assertEquals(0, new BigDecimal("0.45").compareTo(receipt.getStep(0).getLine(ana.getUniqueId()).getShare()));
        assertEquals(0, new BigDecimal("900").compareTo(receipt.getAmount(ana.getUniqueId())));
    }

    // ------------------------------------------------------------------
    //  phase keys
    // ------------------------------------------------------------------

    @Test
    void anExactPhaseBeatsARange() {
        PhasedRewardTable table = phased("{'5': [], '4-10': []}");

        assertEquals("5", table.getKey(5));
        assertEquals("4-10", table.getKey(6));
    }

    @Test
    void theNarrowerRangeBeatsTheWider() {
        PhasedRewardTable table = phased("{'1-20': [], '4-10': []}");

        assertEquals("4-10", table.getKey(6));
        assertEquals("1-20", table.getKey(15));
    }

    @Test
    void fromBeatsEveryAndEveryBeatsDefault() {
        assertEquals("10+", phased("{'every 5': [], '10+': [], 'default': []}").getKey(10));
        assertEquals("every 5", phased("{'every 5': [], 'default': []}").getKey(15));
    }

    @Test
    void aMobArenaPaysEveryFifthWaveApart() {
        PhasedRewardTable waves = phased("{'every 5': [], 'default': []}");

        assertEquals("every 5", waves.getKey(10));
        assertEquals("default", waves.getKey(7));
    }

    @Test
    void aMazePaysByPosition() {
        PhasedRewardTable positions = phased("{'1': [], '2': [], '4-10': [], 'default': []}");

        assertEquals("4-10", positions.getKey(6));
        assertEquals("default", positions.getKey(11));
        assertEquals(null, phased("{'1': []}").getKey(2), "no key takes it");
        assertTrue(phased("{'1': []}").getTable(2).isEmpty());
    }

    @Test
    void twoRangesThatPartlyOverlapAreRefusedNamingBoth() {
        TypeRegistryException refused = assertThrows(TypeRegistryException.class, () -> phased("{'4-10': [], '8-12': []}"));

        assertTrue(refused.getMessage().contains("'4-10'") && refused.getMessage().contains("'8-12'")
                && refused.getMessage().contains("stage 8"), refused.getMessage());
    }

    @Test
    void aKeyThatIsNoPhaseIsRefusedListingTheForms() {
        TypeRegistryException refused = assertThrows(TypeRegistryException.class, () -> phased("{'first': []}"));

        assertTrue(refused.getMessage().contains("'first'") && refused.getMessage().contains("every 5"), refused.getMessage());
    }

    // ------------------------------------------------------------------
    //  once built in code
    // ------------------------------------------------------------------

    @Test
    void aOnceRuleBuiltInCodeRefusesWhatTheLoadRefusesWithTheSameMessage() {
        String[] written = {"'eligibility': 'online'", "'distribution': 'proportional'", "'absent': 'forfeit'"};
        RewardRule.Builder[] built = {
                RewardRule.builder().once(true).eligibility(Eligibility.online()),
                RewardRule.builder().once(true).distribution(Distribution.proportional()),
                RewardRule.builder().once(true).absent(Absence.forfeit())};
        for (int i = 0; i < written.length; i++) {
            String json = "{'once': true, " + written[i] + ", 'effects': []}";
            TypeRegistryException load = assertThrows(TypeRegistryException.class, () -> RewardRule.parse(raid.getOwner(), node(json)));
            RewardRule.Builder builder = built[i];
            IllegalStateException code = assertThrows(IllegalStateException.class, builder::build);
            assertEquals(load.getMessage(), code.getMessage());
        }
        IllegalStateException membership = assertThrows(IllegalStateException.class,
                () -> RewardRule.builder().once(true).membership(RewardMembership.of(false, false)).build());
        assertTrue(membership.getMessage().contains("'membership'") && membership.getMessage().contains("Remove"), membership.getMessage());
    }

    // ------------------------------------------------------------------

    /** Ana 900, Bia 600 online, Duda 500 offline: 2000 contributed. */
    private RewardScene scene() {
        return RewardScene.builder(occasion)
                .participant(ana.getUniqueId(), "Ana", new BigDecimal(900), "alive")
                .participant(bia.getUniqueId(), "Bia", new BigDecimal(600), "alive")
                .participant(duda, "Duda", new BigDecimal(500), "alive")
                .variable("stage", 5)
                .build();
    }

    /** One step: 2000 gold split by contribution, the offline's share kept as a delivery, then {@code then}. */
    private RewardTable pool(String then) {
        return RewardTable.parse(raid.getOwner(), occasion, node("[{'distribution': 'proportional', 'absent': 'deliver', 'effects': ["
                + "{'type': 'money', 'amount': 2000, 'currency': '" + GOLD + "'}, " + then + "]}]"));
    }

    private PhasedRewardTable phased(String json) {
        return PhasedRewardTable.parse(raid.getOwner(), occasion, node(json));
    }

    private RewardContext context(RewardScene scene) {
        return RewardContext.builder(raid.caller("raid"), ProgressOwner.global()).scene(scene).build();
    }

    private void assertBalance(UUID player, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(gold.getBalance(CurrencyOwner.player(player))),
                player + " has " + gold.getBalance(CurrencyOwner.player(player)));
    }
}
