package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import br.com.finalcraft.evernifecore.testing.Menus;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a quantity resolves to: all that fits, all held, a stack - and effects bought N times. */
class QuantityTest extends MenuTestWorlds {

    @Test
    void allThatFitsOnABuyIsTheSmallestOfRoomBalanceAndLimit() {
        TestFPlayerSender alex = menus.player("Alex", 55);

        Offer roomBound = menus.buy(Menus.counted("sword", 0).room(4), 10).build();
        assertEquals(4, Quantity.ALL_THAT_FITS.resolve(menus.context(alex), roomBound), "room for 4, coins for 5");

        Offer balanceBound = menus.buy(Menus.counted("sword", 0).room(7), 10).build();
        assertEquals(5, Quantity.ALL_THAT_FITS.resolve(menus.context(alex), balanceBound), "room for 7, coins for 5");

        Offer limitBound = menus.buy(Menus.counted("sword", 0).room(7), 10)
                .limit("sword-fit", RepeatPolicy.limited(3), LimitScope.PLAYER).build();
        assertEquals(3, Quantity.ALL_THAT_FITS.resolve(menus.context(alex), limitBound), "room for 7, coins for 5, 3 uses left");
    }

    @Test
    void allHeldOnASaleIsTheUnitsHeld() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        Offer offer = menus.sell(Menus.counted("ore", 12), 1).build();

        assertEquals(12, Quantity.ALL_HELD.resolve(menus.context(alex), offer));
    }

    @Test
    void aStackIsTheStackSizeOfTheGoods() {
        TestFPlayerSender alex = menus.player("Alex", 0);

        assertEquals(16, Quantity.STACK.resolve(menus.context(alex), menus.buy(Menus.counted("pearl", 0).stackSize(16), 1).build()));
        assertEquals(64, Quantity.STACK.resolve(menus.context(alex), menus.buy(Goods.ofItem(ItemSpec.of("minecraft:diamond", 1)), 1).build()));
        assertEquals(1, Quantity.STACK.resolve(menus.context(alex), menus.buy(Goods.ofEffects(Collections.<Effect>singletonList(ctx -> { })), 1).build()));
    }

    @Test
    void effectGoodsRunTheirEffectsOncePerUnitAndCannotBeSold() {
        TestFPlayerSender alex = menus.player("Alex", 100);
        AtomicInteger runs = new AtomicInteger();
        Goods rank = Goods.ofEffects("a rank day", Collections.<Effect>singletonList(ctx -> runs.incrementAndGet()));

        assertTrue(Transactions.buy(menus.context(alex), menus.buy(rank, 10).build(), 3).isSuccess());
        assertEquals(3, runs.get());

        TransactionResult sold = Transactions.sell(menus.context(alex), menus.sell(rank, 10).build(), 1);
        assertEquals(TransactionResult.Failure.NOT_SELLABLE, sold.getFailure());
        assertEquals(3, runs.get());
    }
}
