package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.testing.Menus;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Selling from code: the held stack, the same item everywhere, the whole inventory - each found through a catalog. */
class SellHandTest extends MenuTestWorlds {

    private static final ItemSpec DIAMOND = ItemSpec.of("minecraft:diamond", 1);
    private static final ItemMatcher PLAIN_DIAMOND = ItemMatcher.of("minecraft:diamond").plainOnly();

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }

    private OfferCatalog diamondsAtFive(ItemMatcher matcher) {
        return Menus.catalog(menus.sell(Goods.ofItem(DIAMOND, matcher), 5).build());
    }

    private int diamonds(TestFPlayerSender player) {
        return menus.inventory().count(player, ItemMatcher.of("minecraft:diamond"));
    }

    @Test
    void theHeldStackSellsOnlyItsOwnUnits() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        menus.inventory().addSeparately(alex, DIAMOND.withAmount(10));
        menus.inventory().addSeparately(alex, DIAMOND.withAmount(20));

        TransactionResult sold = Transactions.sellHand(menus.context(alex), diamondsAtFive(PLAIN_DIAMOND), HandScope.HELD_STACK);

        assertTrue(sold.isSuccess(), sold.toString());
        assertEquals(10, sold.getQuantity());
        assertEquals(20, diamonds(alex));
        assertAmount("50", menus.balance(menus.coins(), alex));
    }

    @Test
    void sameItemEverywhereSweepsTheInventoryByTheSameOffer() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        menus.inventory().addSeparately(alex, DIAMOND.withAmount(10));
        menus.inventory().addSeparately(alex, DIAMOND.withAmount(20));

        TransactionResult sold = Transactions.sellHand(menus.context(alex), diamondsAtFive(PLAIN_DIAMOND), HandScope.SAME_ITEM_EVERYWHERE);

        assertTrue(sold.isSuccess(), sold.toString());
        assertEquals(30, sold.getQuantity());
        assertEquals(0, diamonds(alex));
        assertAmount("150", menus.balance(menus.coins(), alex));
    }

    @Test
    void aNamedItemDoesNotMatchAPlainOnlyMatcher() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        menus.inventory().addSeparately(alex, DIAMOND.withText("§bShiny", Collections.<String>emptyList()).withAmount(4));
        menus.recorded(TransactionEvent.class);

        TransactionResult refused = Transactions.sellHand(menus.context(alex), diamondsAtFive(PLAIN_DIAMOND), HandScope.HELD_STACK);

        assertEquals(TransactionResult.Failure.NOT_SELLABLE, refused.getFailure());
        assertEquals(4, diamonds(alex));
        assertTrue(menus.recorded(TransactionEvent.class).isEmpty());
    }

    @Test
    void aNamedItemMatchesWhenTheMatcherDoesNotAskForPlain() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        menus.inventory().addSeparately(alex, DIAMOND.withText("§bShiny", Collections.<String>emptyList()).withAmount(4));

        TransactionResult sold = Transactions.sellHand(menus.context(alex), diamondsAtFive(ItemMatcher.of("minecraft:diamond")), HandScope.HELD_STACK);

        assertTrue(sold.isSuccess(), sold.toString());
        assertEquals(0, diamonds(alex));
        assertAmount("20", menus.balance(menus.coins(), alex));
    }

    @Test
    void anItemTheCatalogDoesNotBuyIsNotSellableAndPostsNothing() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        menus.inventory().add(alex, ItemSpec.of("minecraft:stone", 32));
        menus.recorded(TransactionEvent.class);

        TransactionResult refused = Transactions.sellHand(menus.context(alex), diamondsAtFive(PLAIN_DIAMOND), HandScope.HELD_STACK);

        assertEquals(TransactionResult.Failure.NOT_SELLABLE, refused.getFailure());
        assertNull(refused.getOffer());
        assertTrue(menus.recorded(TransactionEvent.class).isEmpty());
        assertAmount("0", menus.balance(menus.coins(), alex));
    }

    @Test
    void sellAllTotalsPerCurrencyAndPostsOneEventPerOffer() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        menus.inventory().add(alex, DIAMOND.withAmount(10));
        menus.inventory().add(alex, ItemSpec.of("minecraft:iron_ingot", 20));
        menus.inventory().add(alex, ItemSpec.of("minecraft:stone", 5));
        OfferCatalog catalog = Menus.catalog(
                menus.sell(Goods.ofItem(DIAMOND, PLAIN_DIAMOND), 5).build(),
                Offer.sell(Goods.ofItem(ItemSpec.of("minecraft:iron_ingot", 1), ItemMatcher.of("minecraft:iron_ingot")), Price.of(menus.gold(), 1)).build());
        menus.recorded(TransactionEvent.class);

        SellAllResult sold = Transactions.sellAll(menus.context(alex), catalog);

        assertEquals(30, sold.getUnits());
        assertAmount("50", sold.getTotals().get(menus.coins().getId()));
        assertAmount("20", sold.getTotals().get(menus.gold().getId()));
        assertEquals(2, menus.recorded(TransactionEvent.class).size(), "one TransactionEvent per offer matched");
        assertEquals(5, menus.inventory().count(alex, ItemMatcher.of("minecraft:stone")), "what the catalog does not buy stays");
    }
}
