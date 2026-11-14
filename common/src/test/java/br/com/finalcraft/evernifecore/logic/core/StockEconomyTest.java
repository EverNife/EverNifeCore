package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.booster.Booster;
import br.com.finalcraft.evernifecore.booster.BoosterDomain;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.Costs;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stock {@code money} cost and effect: neither multiplies by a booster - the domain's payer does. */
class StockEconomyTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private TestFPlayerSender alex;
    private CurrencyOwner alexWallet;
    private TestCurrency gold;

    @BeforeEach
    void open() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().install();
        shop = LogicWorld.open("FakeShop", tempDir);
        alex = shop.player("Alex");
        alexWallet = CurrencyOwner.player(alex.getUniqueId());
        gold = Economies.currency("fakeshop:gold", CurrencyOwner.Kind.PLAYER).balance(alexWallet, 100);
        world.withCurrency(shop.getOwner(), gold);
    }

    @AfterEach
    void close() {
        shop.close();
        world.close();
    }

    private static JsonNode node(String singleQuotedJson) {
        try {
            return new ObjectMapper().readTree(singleQuotedJson.replace('\'', '"'));
        } catch (IOException unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }

    private Cost cost(String json) {
        return Costs.parse(shop.getOwner(), node(json), ZoneOffset.UTC);
    }

    private Effect effect(String json) {
        return Effects.parse(shop.getOwner(), node(json), ZoneOffset.UTC);
    }

    private void assertBalance(String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(gold.getBalance(alexWallet)), "balance " + gold.getBalance(alexWallet));
    }

    @Test
    void neitherTheEffectNorTheCostReadsABoosterOfADomainNamedAfterTheCurrency() {
        BoosterDomain gold = BoosterDomain.of(shop.getOwner(), "gold").register();
        shop.boosters().grant(Booster.on(gold).factor(2).forOwner(ProgressOwner.player(alex.getUniqueId())).build()).join();
        ExecutionContext ctx = shop.context(alex).build();

        effect("{'type': 'money', 'amount': 10, 'currency': 'fakeshop:gold'}").apply(ctx);
        assertBalance("110");
        assertEquals(0, new BigDecimal(2).compareTo(gold.resolve(ctx).getMultiplier()), "the booster is there, nobody asked it");
        assertFalse(Effects.registry().find("evernifecore:money").get().getParams().stream().anyMatch(param -> param.getName().equals("boosted")),
                "the money effect has no boosted param");
        effect("{'type': 'money', 'amount': 20, 'currency': 'fakeshop:gold'}").apply(ctx);
        assertBalance("130");

        Cost cost = cost("{'type': 'money', 'amount': 30, 'currency': 'fakeshop:gold'}");
        assertTrue(cost.test(ctx).isPassed());
        assertTrue(cost.take(ctx));
        assertBalance("100");
    }

    @Test
    void aMoneyEffectOutsideARewardPaysItsAmountToAPlayerHoldingTheDomainsPermission() {
        BoosterDomain sales = BoosterDomain.of(shop.getOwner(), "sales").permission("fakeshop.booster.sales", 2).register();
        alex.grant("fakeshop.booster.sales.2");
        ExecutionContext ctx = shop.context(alex).build();

        Effects.apply(ctx, effect("{'type': 'money', 'amount': 10, 'currency': 'fakeshop:gold'}"));

        assertBalance("110");
        assertEquals(0, new BigDecimal(2).compareTo(sales.resolve(ctx).getMultiplier()), "the permission boosts the domain, nobody asked it");
    }

    @Test
    void theBalanceVariableReadsTheOwnersWallet() {
        ExecutionContext ctx = shop.context(alex).build();
        Condition enough = Conditions.parse(shop.getOwner(), node("{'type': 'compare', 'variable': 'fakeshop:gold', 'op': '>=', 'amount': 100}"),
                ZoneOffset.UTC);
        Condition more = Conditions.parse(shop.getOwner(), node("{'type': 'compare', 'variable': 'fakeshop:gold', 'op': '>', 'amount': 100}"),
                ZoneOffset.UTC);

        assertTrue(enough.test(ctx).isPassed());
        assertFalse(more.test(ctx).isPassed());
        assertEquals("100", ctx.resolve("{fakeshop:gold}"), "the currency's id is the variable of its balance");
        assertEquals(0, new BigDecimal(100).compareTo(gold.getBalance(alexWallet)), "reading takes nothing");
    }

    @Test
    void theCurrencyIdResolvesItsVariables() {
        ExecutionContext ctx = shop.context(alex).variable("cur", "gold").build();
        Cost cost = cost("{'type': 'money', 'amount': 30, 'currency': 'fakeshop:{cur}'}");

        assertTrue(cost.test(ctx).isPassed());
        assertTrue(cost.take(ctx));
        assertBalance("70");
        effect("{'type': 'money', 'amount': 5, 'currency': 'fakeshop:{cur}'}").apply(ctx);
        assertBalance("75");
        assertTrue(cost.describe(ctx).toPlainText().contains("30"), cost.describe(ctx).toPlainText());
        assertTrue(String.valueOf(cost).contains("fakeshop:{cur}"), "toString keeps what was written: " + cost);
    }

    @Test
    void theCostTakesWhatItSaysAndARefundGivesItBack() {
        ExecutionContext ctx = shop.context(alex).variable("price", "25").build();
        Cost cost = cost("{'type': 'money', 'amount': '{price}', 'currency': 'fakeshop:gold'}");

        assertTrue(cost.take(ctx));
        assertBalance("75");
        cost.refund(ctx);
        assertBalance("100");
        assertTrue(cost.describe(ctx).getText().startsWith("25"), cost.describe(ctx).getText());
    }

    @Test
    void aShortBalanceFailsTheTestWithBothAmountsAndTakesNothing() {
        ExecutionContext ctx = shop.context(alex).build();
        Cost cost = cost("{'type': 'money', 'amount': 1000, 'currency': 'fakeshop:gold'}");

        ConditionResult result = cost.test(ctx);
        assertFalse(result.isPassed());
        String why = result.getWhy().getText();
        assertTrue(why.contains("100") && why.contains("1000"), why);
        assertFalse(cost.take(ctx));
        assertBalance("100");
    }

    @Test
    void aCostOnAnOwnerTheCurrencyKeepsNoWalletForFailsWithWhatToDoInstead() {
        ExecutionContext ctx = shop.context(alex).owner(ProgressOwner.of("group", "fakeclans/red")).build();

        ConditionResult result = cost("{'type': 'money', 'amount': 5, 'currency': 'fakeshop:gold'}").test(ctx);

        assertFalse(result.isPassed());
        assertTrue(result.getWhy().getText().contains("fakeshop:gold has no wallet for group"), result.getWhy().getText());
    }

    @Test
    void anEffectWithoutAWalletIsAuditedAndNeverThrows() {
        ExecutionContext ctx = shop.context(alex).owner(ProgressOwner.world("world")).build();

        effect("{'type': 'money', 'amount': 5, 'currency': 'fakeshop:gold'}").apply(ctx);

        assertEquals(1, ctx.getAudit().getEntries(AuditKind.EFFECT_FAILED).size());
        assertBalance("100");
    }

    @Test
    void moneyWithoutAnEconomyRefusesTheCostWithTheWayToInstallOne() {
        ConditionResult result = cost("{'type': 'money', 'amount': 5}").test(shop.context(alex).build());

        assertFalse(result.isPassed());
        assertTrue(result.getWhy().getText().contains("No economy on this server"), result.getWhy().getText());
    }

    @Test
    void anUnregisteredCurrencyCannotPayNowAndNeverThrows() {
        ExecutionContext ctx = shop.context(alex).build();
        Cost cost = cost("{'type': 'money', 'amount': 5, 'currency': 'fakeshop:silver'}");

        ConditionResult result = cost.test(ctx);
        assertFalse(result.isPassed());
        assertTrue(result.getWhy().getText().contains("No currency 'fakeshop:silver' is registered right now"), result.getWhy().getText());
        assertFalse(cost.take(ctx), "the gate refunds what it took before");
        effect("{'type': 'money', 'amount': 5, 'currency': 'fakeshop:silver'}").apply(ctx);
        assertEquals(1, ctx.getAudit().getEntries(AuditKind.EFFECT_FAILED).size());
        assertBalance("100");
    }
}
