package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.Costs;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.audit.CostRefundEvent;
import br.com.finalcraft.evernifecore.logic.value.Contribution;
import br.com.finalcraft.evernifecore.logic.value.ItemRequirement;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestInventory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stock {@code item} cost: it counts and takes from the inventory, and a gate gives it back in reverse with the money. */
class StockItemsTest {

    private static final String DIAMOND = "minecraft:diamond";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private TestFPlayerSender alex;
    private CurrencyOwner alexWallet;
    private TestCurrency gold;
    private TestInventory inventory;

    @BeforeEach
    void open() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().withInventory(4).install();
        shop = LogicWorld.open("FakeShop", tempDir);
        alex = shop.player("Alex");
        alexWallet = CurrencyOwner.player(alex.getUniqueId());
        gold = Economies.currency("fakeshop:gold", CurrencyOwner.Kind.PLAYER).balance(alexWallet, 100);
        world.withCurrency(shop.getOwner(), gold);
        inventory = world.platform().getTestInventory();
    }

    @AfterEach
    void close() {
        shop.close();
        world.close();
    }

    private Cost cost(String json) {
        try {
            return Costs.parse(shop.getOwner(), new ObjectMapper().readTree(json.replace('\'', '"')), ZoneOffset.UTC);
        } catch (IOException unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }

    private int diamonds() {
        return inventory.count(alex, ItemMatcher.of(DIAMOND));
    }

    @Test
    void theCostCountsWhatTheInventoryHoldsAndTakesExactlyThat() {
        inventory.add(alex, ItemSpec.of(DIAMOND, 2));
        Cost three = cost("{'type': 'item', 'item': 'diamond', 'amount': 3}");
        ExecutionContext ctx = shop.context(alex).build();

        ConditionResult shortOne = three.test(ctx);
        assertFalse(shortOne.isPassed());
        assertTrue(shortOne.getWhy().getText().contains("2 of 3"), shortOne.getWhy().getText());
        assertFalse(three.take(ctx), "all or nothing");
        assertEquals(2, diamonds());

        inventory.add(alex, ItemSpec.of(DIAMOND, 2));
        assertTrue(three.test(ctx).isPassed());
        assertTrue(three.take(ctx));
        assertEquals(1, diamonds());
    }

    @Test
    void aGateGivesTheItemsAndTheMoneyBackInReverseWhenALaterTakeFails() {
        inventory.add(alex, ItemSpec.of(DIAMOND, 3));
        Cost money = cost("{'type': 'money', 'amount': 10, 'currency': 'fakeshop:gold'}");
        Cost items = cost("{'type': 'item', 'item': '" + DIAMOND + "', 'amount': 3}");
        Cost refusesToCome = new Cost() {
            @Override
            public ConditionResult test(ExecutionContext ctx) {
                return ConditionResult.pass();
            }

            @Override
            public boolean take(ExecutionContext ctx) {
                return false;
            }

            @Override
            public void refund(ExecutionContext ctx) {
            }

            @Override
            public FancyText describe(ExecutionContext ctx) {
                return FancyText.of("a stubborn cost");
            }
        };
        List<CostRefundEvent> refunds = shop.recorded(CostRefundEvent.class);

        GateResult result = Gate.builder().cost(money, items, refusesToCome).build().pay(shop.context(alex).build());

        assertEquals(GateResult.Outcome.REFUNDED, result.getOutcome());
        assertEquals(Arrays.asList(items, money), refunds.stream().map(CostRefundEvent::getCost).collect(Collectors.toList()),
                "the item comes back before the money: reverse order of taking");
        assertTrue(refunds.stream().allMatch(CostRefundEvent::isRefunded));
        assertEquals(3, diamonds());
        assertEquals(0, new BigDecimal("100").compareTo(gold.getBalance(alexWallet)));
    }

    @Test
    void anItemContributionTakesFromEachDepositorUpToWhatIsMissing() {
        TestFPlayerSender bia = shop.player("Bia");
        inventory.add(alex, ItemSpec.of(DIAMOND, 3));
        inventory.add(bia, ItemSpec.of(DIAMOND, 4));
        Contribution<ItemMatcher> altar = shop.contributions().define("altar", ProgressOwner.global(), ItemRequirement.of(ItemMatcher.of(DIAMOND), 5));

        assertEquals(0, new BigDecimal(3).compareTo(altar.deposit(shop.context(alex).build()).getCounted()));
        assertTrue(altar.deposit(shop.context(bia).build()).isCompleted());

        assertEquals(0, diamonds());
        assertEquals(2, inventory.count(bia, ItemMatcher.of(DIAMOND)), "only what was missing left Bia's inventory");
    }
}
