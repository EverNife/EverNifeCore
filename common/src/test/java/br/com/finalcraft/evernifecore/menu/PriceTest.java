package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Variable;
import br.com.finalcraft.evernifecore.testing.Menus;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A price of several lines: each charged and described in its own currency, all or nothing, read when quoted. */
class PriceTest extends MenuTestWorlds {

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }

    @Test
    void twoCurrenciesAreEachChargedAndDescribedWithTheirOwnFormat() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        menus.gold().balance(CurrencyOwner.player(alex.getUniqueId()), 10);
        Offer offer = Offer.buy(Menus.counted("sword", 0), Price.of(menus.coins(), 100).and(menus.gold(), 2)).build();

        TransactionResult bought = Transactions.buy(menus.context(alex), offer, 1);

        assertTrue(bought.isSuccess(), bought.toString());
        assertAmount("400", menus.balance(menus.coins(), alex));
        assertAmount("8", menus.balance(menus.gold(), alex));
        String text = bought.describe(menus.context(alex)).toPlainText();
        assertTrue(text.contains(menus.coins().format(new BigDecimal("100")).toPlainText()), text);
        assertTrue(text.contains(menus.gold().format(new BigDecimal("2")).toPlainText()), text);
    }

    @Test
    void whenTheSecondLineFailsToBeTakenTheFirstIsGivenBack() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        menus.gold().balance(CurrencyOwner.player(alex.getUniqueId()), 10).refuseTakes("the gold bank is down");
        Offer offer = Offer.buy(Menus.counted("sword", 0), Price.of(menus.coins(), 100).and(menus.gold(), 2)).build();

        TransactionResult result = Transactions.buy(menus.context(alex), offer, 1);

        assertEquals(TransactionResult.Failure.INSUFFICIENT_FUNDS, result.getFailure());
        assertEquals(menus.gold().getId(), result.getMissingLine().getId(), "the line that did not come out is named");
        assertEquals(1, menus.coins().getTakes(), "the coins were taken first");
        assertAmount("500", menus.balance(menus.coins(), alex));
        assertAmount("10", menus.balance(menus.gold(), alex));
    }

    @Test
    void aVariablePriceIsReadAtEveryQuote() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        AtomicReference<BigDecimal> market = new AtomicReference<>(new BigDecimal("100"));
        Variable<BigDecimal> diamondPrice = new Variable<BigDecimal>() {
            @Override
            public String getId() {
                return "fakeshop:diamond-price";
            }

            @Override
            public Class<BigDecimal> getType() {
                return BigDecimal.class;
            }

            @Override
            public BigDecimal read(ExecutionContext ctx) {
                return market.get();
            }
        };
        Offer offer = Offer.buy(Menus.counted("diamond", 0), Price.of(menus.coins(), diamondPrice)).build();

        assertAmount("200", Transactions.quote(menus.context(alex), offer, 2).getQuote().getTotal(menus.coins().getId()));
        market.set(new BigDecimal("150"));
        assertAmount("300", Transactions.quote(menus.context(alex), offer, 2).getQuote().getTotal(menus.coins().getId()));
    }

    @Test
    void aSellOfferWithAnItemLineIsRefusedWithWhatToDoInstead() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> Offer.sell(Menus.counted("ore", 1),
                Price.of(menus.coins(), 5).andItem(ItemMatcher.of("minecraft:iron_ingot"), 10)).build());

        assertTrue(refused.getMessage().contains("only a currency can be paid"), refused.getMessage());
    }

    @Test
    void aListenerCannotSetANegativeTotal() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        Quote quote = Price.of(menus.coins(), 100).quote(menus.context(alex), 1);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> quote.setTotal(menus.coins().getId(), new BigDecimal("-1")));
        assertTrue(refused.getMessage().contains("zero or more"), refused.getMessage());
    }
}
