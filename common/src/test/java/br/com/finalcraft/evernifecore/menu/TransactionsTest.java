package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.testing.CountedGoods;
import br.com.finalcraft.evernifecore.testing.Menus;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The order and the outcomes of one trade: quote, buy, sell, the two events, and the refusals that charge nothing. */
class TransactionsTest extends MenuTestWorlds {

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }

    @Test
    void aQuoteChangesNeitherTheBalanceNorTheGoodsAndPostsNothing() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        CountedGoods swords = Menus.counted("sword", 0);
        Offer offer = menus.buy(swords, 100).build();
        menus.recorded(TransactionPreEvent.class);
        menus.recorded(TransactionEvent.class);

        TransactionResult quoted = Transactions.quote(menus.context(alex), offer, 2);

        assertTrue(quoted.isSuccess(), quoted.toString());
        assertAmount("200", quoted.getQuote().getTotal(menus.coins().getId()));
        assertAmount("500", menus.balance(menus.coins(), alex));
        assertEquals(0, swords.getHeld());
        assertTrue(menus.recorded(TransactionPreEvent.class).isEmpty());
        assertTrue(menus.recorded(TransactionEvent.class).isEmpty());
    }

    @Test
    void aBuyChargesThePriceAndHandsTheGoodsOver() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        CountedGoods swords = Menus.counted("sword", 0);

        TransactionResult bought = Transactions.buy(menus.context(alex), menus.buy(swords, 100).build(), 3);

        assertTrue(bought.isSuccess(), bought.toString());
        assertAmount("200", menus.balance(menus.coins(), alex));
        assertEquals(3, swords.getHeld());
        assertTrue(bought.describe(menus.context(alex)).toPlainText().startsWith("Bought 3x sword for "),
                bought.describe(menus.context(alex)).toPlainText());
    }

    @Test
    void aCancelledPreEventEndsCancelledAndChargesNothing() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        CountedGoods swords = Menus.counted("sword", 0);
        onPreEvent(pre -> pre.setCancelled(true));

        TransactionResult result = Transactions.buy(menus.context(alex), menus.buy(swords, 100).build(), 1);

        assertEquals(TransactionResult.Failure.CANCELLED, result.getFailure());
        assertAmount("500", menus.balance(menus.coins(), alex));
        assertEquals(0, menus.coins().getTakes(), "nothing was taken before the pre-event said no");
        assertEquals(0, swords.getHeld());
    }

    @Test
    void aListenerThatChangesTheTotalChargesTheNewOneAndKeepsTheOriginal() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        onPreEvent(pre -> pre.getQuote().setTotal(menus.coins().getId(), new BigDecimal("80")));

        TransactionResult result = Transactions.buy(menus.context(alex), menus.buy(Menus.counted("sword", 0), 100).build(), 1);

        assertTrue(result.isSuccess(), result.toString());
        assertAmount("420", menus.balance(menus.coins(), alex));
        assertAmount("80", result.getQuote().getTotal(menus.coins().getId()));
        assertAmount("100", result.getQuote().getOriginal().getTotal(menus.coins().getId()));
    }

    @Test
    void aShortBalanceIsInsufficientFundsAndChargesNothing() {
        TestFPlayerSender alex = menus.player("Alex", 50);
        CountedGoods swords = Menus.counted("sword", 0);

        TransactionResult result = Transactions.buy(menus.context(alex), menus.buy(swords, 100).build(), 1);

        assertEquals(TransactionResult.Failure.INSUFFICIENT_FUNDS, result.getFailure());
        assertEquals(menus.coins().getId(), result.getMissingLine().getId());
        assertAmount("50", menus.balance(menus.coins(), alex));
        assertEquals(0, swords.getHeld());
    }

    @Test
    void theTransactionEventIsPostedOnceAfterASuccessAndNeverAfterAFailure() {
        TestFPlayerSender alex = menus.player("Alex", 150);
        Offer offer = menus.buy(Menus.counted("sword", 0), 100).build();
        menus.recorded(TransactionEvent.class);

        assertTrue(Transactions.buy(menus.context(alex), offer, 1).isSuccess());
        assertEquals(1, menus.recorded(TransactionEvent.class).size());

        assertEquals(TransactionResult.Failure.INSUFFICIENT_FUNDS, Transactions.buy(menus.context(alex), offer, 1).getFailure());
        assertEquals(1, menus.recorded(TransactionEvent.class).size(), "a failure posts no TransactionEvent");
        assertTrue(menus.recorded(TransactionEvent.class).get(0).getResult().isSuccess());
    }

    @Test
    void aSaleTakesTheGoodsAndPaysThePlayer() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        CountedGoods ores = Menus.counted("ore", 5);

        TransactionResult sold = Transactions.sell(menus.context(alex), menus.sell(ores, 30).build(), 2);

        assertTrue(sold.isSuccess(), sold.toString());
        assertEquals(3, ores.getHeld());
        assertAmount("60", menus.balance(menus.coins(), alex));
    }

    @Test
    void aDeniedGateRefusesBeforeThePreEventAndNoListenerHearsIt() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        AtomicInteger heard = new AtomicInteger();
        onPreEvent(pre -> heard.incrementAndGet());
        Gate vipOnly = Gate.builder().require(ctx -> ConditionResult.fail(FancyText.of("Only VIPs buy here."))).build();

        TransactionResult result = Transactions.buy(menus.context(alex), menus.buy(Menus.counted("sword", 0), 100).gate(vipOnly).build(), 1);

        assertEquals(TransactionResult.Failure.GATE_DENIED, result.getFailure());
        assertEquals("Only VIPs buy here.", result.describe(menus.context(alex)).toPlainText());
        assertEquals(0, heard.get(), "the pre-event is not posted for a refused gate");
        assertAmount("500", menus.balance(menus.coins(), alex));
    }

    @Test
    void goodsThatDoNotFitGoToAPendingDeliveryByDefault() throws Exception {
        TestFPlayerSender alex = menus.player("Alex", 500);
        menus.inventory().fill(alex, "minecraft:stone");
        ExecutionContext ctx = menus.context(alex);

        TransactionResult bought = Transactions.buy(ctx, menus.buy(Goods.ofItem(ItemSpec.of("minecraft:diamond", 1)), 100).build(), 1);

        assertTrue(bought.isSuccess(), bought.toString());
        assertNotNull(bought.getDelivery(), "what did not fit waits as a pending delivery");
        assertNotNull(bought.getDelivery().get(5, TimeUnit.SECONDS));
        assertEquals(0, menus.inventory().count(alex, ItemMatcher.of("minecraft:diamond")));
        assertAmount("400", menus.balance(menus.coins(), alex));
    }

    @Test
    void refusingWhenFullIsNoSpaceAndChargesNothing() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        menus.inventory().fill(alex, "minecraft:stone");

        TransactionResult result = Transactions.buy(menus.context(alex), menus.buy(Goods.ofItem(ItemSpec.of("minecraft:diamond", 1)), 100)
                .whenNoSpace(NoSpacePolicy.REFUSE).build(), 1);

        assertEquals(TransactionResult.Failure.NO_SPACE, result.getFailure());
        assertNull(result.getDelivery());
        assertAmount("500", menus.balance(menus.coins(), alex));
    }
}
