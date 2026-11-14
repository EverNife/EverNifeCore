package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.booster.Booster;
import br.com.finalcraft.evernifecore.booster.BoosterDomain;
import br.com.finalcraft.evernifecore.booster.BoosterJoin;
import br.com.finalcraft.evernifecore.booster.BoosterSource;
import br.com.finalcraft.evernifecore.booster.Boosters;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.await;
import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A duel plugin pays its win by a reward table boosted by its own domain: {@code {amount}} is the final number, frozen
 * once per member and step on the receipt, and the wallet, the message and a pending delivery all get that number.
 */
class BoostedRewardTest {

    private static final String GOLD = "fakeduels:gold";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld duels;
    private TestCurrency gold;
    private TestFPlayerSender ana;
    private RewardOccasion won;
    private BoosterDomain win;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        duels = LogicWorld.open("FakeDuels", tempDir.resolve("duels"));
        gold = Economies.currency(GOLD, CurrencyOwner.Kind.PLAYER);
        world.withCurrency(duels.getOwner(), gold);
        duels.deliveries();
        ana = duels.player("Ana");
        won = RewardOccasion.builder(duels.getOwner(), "duel-won").phase("round").build();
        win = BoosterDomain.of(duels.getOwner(), "win").join(BoosterJoin.sumBonus().cap(new BigDecimal(3)))
                .permission("fakeduels.booster.win", 2).register();
    }

    @AfterEach
    void close() {
        duels.close();
        world.close();
    }

    /** Ana holds {@code fakeduels.booster.win.2} and a granted +50%: sum-bonus 1 + 1 + 0.5 = 2.5, under the cap of 3. */
    private void boostAna() {
        ana.grant("fakeduels.booster.win.2");
        await(duels.boosters().grant(Booster.on(win).bonus(0.5).forOwner(ProgressOwner.player(ana.getUniqueId())).build()));
    }

    @Test
    void theWalletTheMessageAndTheReceiptGetTheFinalAmount() {
        boostAna();

        RewardReceipt receipt = await(winTable().pay(context(ana.getUniqueId()).boostedBy(win).build()));

        assertBalance(ana.getUniqueId(), "1250");
        ana.assertAnyMessageContains("+1250 (base 500)");
        RewardReceipt.Line line = receipt.getStep(0).getLine(ana.getUniqueId());
        assertNumber("1250", line.getAmount());
        assertNumber("500", line.getBaseAmount());
        assertNumber("2.5", line.getMultiplier());
    }

    @Test
    void aPaymentNotBoostedByTheDomainPaysTheBase() {
        boostAna();

        RewardReceipt receipt = await(winTable().pay(context(ana.getUniqueId()).build()));

        assertBalance(ana.getUniqueId(), "500");
        ana.assertAnyMessageContains("+500 (base 500)");
        assertNumber("500", receipt.getStep(0).getLine(ana.getUniqueId()).getAmount());
        assertNumber("1", receipt.getStep(0).getLine(ana.getUniqueId()).getMultiplier());
    }

    @Test
    void anOfflineMembersDeliveryKeepsTheFinalAmountAfterTheBoosterEnds() {
        TestFPlayerSender cris = duels.player("Cris");
        duels.quit(cris);
        ProgressOwner crisOwner = ProgressOwner.player(cris.getUniqueId());
        //offline, only granted boosters count: x2 and +50% sum to 2.5 as Ana's do
        Booster doubled = await(duels.boosters().grant(Booster.on(win).factor(2).forOwner(crisOwner).build()));
        Booster half = await(duels.boosters().grant(Booster.on(win).bonus(0.5).forOwner(crisOwner).build()));

        RewardReceipt receipt = await(winTable().pay(context(cris.getUniqueId()).boostedBy(win).build()));

        assertEquals(RewardReceipt.Status.DELIVERED, receipt.getStep(0).getLine(cris.getUniqueId()).getStatus());
        List<Delivery> kept = await(duels.deliveries().listOf(cris.getUniqueId()));
        assertEquals(1, kept.size(), kept.toString());
        assertNumber("1250", new BigDecimal(kept.get(0).getEffects().get(0).path("amount").asText()));
        assertEquals("1250", kept.get(0).getVariables().get("amount"), "the texts of the delivery read the same number");

        await(duels.boosters().revoke(doubled.getId()));
        await(duels.boosters().revoke(half.getId()));
        assertNumber("1", win.resolve(cris.getUniqueId()).getMultiplier());
        duels.rejoin(cris);
        await(duels.deliveries().claimAll(cris));

        assertBalance(cris.getUniqueId(), "1250");
    }

    @Test
    void eachMemberIsResolvedOncePerStepAndTheMoneyResolvesNothing() {
        boostAna();
        TestFPlayerSender bia = duels.player("Bia");
        AtomicInteger asked = new AtomicInteger();
        Boosters.addSource(duels.getOwner(), new BoosterSource() {
            @Override
            public String getId() {
                return "fakeduels:counter";
            }

            @Override
            public Collection<Booster> boostersFor(ExecutionContext ctx, BoosterDomain domain) {
                asked.incrementAndGet();
                return Collections.emptyList();
            }
        });
        RewardTable twoSteps = RewardTable.parse(duels.getOwner(), won, node("["
                + "{'effects': [{'type': 'money', 'amount': 500, 'currency': '" + GOLD + "'}]},"
                + "{'effects': [{'type': 'money', 'amount': 100, 'currency': '" + GOLD + "'}]}]"));
        RewardScene scene = RewardScene.builder(won)
                .participant(ana.getUniqueId(), "Ana", BigDecimal.ONE)
                .participant(bia.getUniqueId(), "Bia", BigDecimal.ONE)
                .build();

        await(twoSteps.pay(RewardContext.builder(duels.caller("duel"), ProgressOwner.global()).scene(scene).boostedBy(win).build()));

        assertEquals(4, asked.get(), "2 members x 2 steps, nothing more while paying");
        assertBalance(ana.getUniqueId(), "1500");
        assertBalance(bia.getUniqueId(), "600");
    }

    @Test
    void previewAndPayAnswerTheSameBoostedReceipt() {
        boostAna();
        RewardContext ctx = context(ana.getUniqueId()).boostedBy(win).build();

        RewardReceipt preview = winTable().preview(ctx);
        RewardReceipt paid = await(winTable().pay(ctx));

        assertEquals(paid.toString(), preview.toString());
        assertTrue(preview.toString().contains("amount 500 x 2.5 = 1250"), preview.toString());
    }

    // ------------------------------------------------------------------

    private RewardTable winTable() {
        return RewardTable.parse(duels.getOwner(), won, node("[{'effects': ["
                + "{'type': 'money', 'amount': 500, 'currency': '" + GOLD + "'},"
                + "{'type': 'message', 'text': '+{amount} (base {base-amount})'}]}]"));
    }

    private RewardContext.Builder context(UUID winner) {
        String name = winner.equals(ana.getUniqueId()) ? "Ana" : "Cris";
        RewardScene scene = RewardScene.builder(won).participant(winner, name, BigDecimal.ONE).build();
        return RewardContext.builder(duels.caller("duel"), ProgressOwner.global()).scene(scene);
    }

    private static void assertNumber(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }

    private void assertBalance(UUID player, String expected) {
        assertNumber(expected, gold.getBalance(CurrencyOwner.player(player)));
    }
}
