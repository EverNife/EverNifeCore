package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import br.com.finalcraft.evernifecore.testing.Menus;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.time.Window;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An offer's limit: counted per owner and per window, by the quantity traded, never by a quote. */
class LimitTest extends MenuTestWorlds {

    private static final RepeatPolicy THREE_A_DAY = RepeatPolicy.limited(3).window(Window.daily(LocalTime.of(4, 0), ZoneOffset.UTC));

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }

    private Offer threeADay(String counter) {
        return menus.buy(Menus.counted("sword", 0), 10).limit(counter, THREE_A_DAY, LimitScope.PLAYER).build();
    }

    @Test
    void threeADayAcceptsThreeAndRefusesTheFourthWithoutCharging() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        Offer offer = threeADay("sword-a");
        for (int i = 0; i < 3; i++) {
            assertTrue(Transactions.buy(menus.context(alex), offer, 1).isSuccess());
        }

        TransactionResult fourth = Transactions.buy(menus.context(alex), offer, 1);

        assertEquals(TransactionResult.Failure.LIMIT_REACHED, fourth.getFailure());
        assertAmount("470", menus.balance(menus.coins(), alex));
    }

    @Test
    void theNextWindowStartsTheCountOver() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        Offer offer = threeADay("sword-b");
        assertTrue(Transactions.buy(menus.context(alex), offer, 3).isSuccess());
        assertEquals(TransactionResult.Failure.LIMIT_REACHED, Transactions.buy(menus.context(alex), offer, 1).getFailure());

        shop.advance(Duration.ofDays(1));

        assertTrue(Transactions.buy(menus.context(alex), offer, 1).isSuccess(), "the day turned: the count is zero again");
    }

    @Test
    void buyingTwoUsesTwo() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        Offer offer = threeADay("sword-c");

        assertTrue(Transactions.buy(menus.context(alex), offer, 2).isSuccess());
        assertEquals(1, offer.getCounter(menus.context(alex)).getUsesLeft());
        assertEquals(TransactionResult.Failure.LIMIT_REACHED, Transactions.buy(menus.context(alex), offer, 2).getFailure());
        assertTrue(Transactions.buy(menus.context(alex), offer, 1).isSuccess());
    }

    @Test
    void aGlobalLimitIsOneCounterForEveryPlayer() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        TestFPlayerSender bia = menus.player("Bia", 500);
        Offer offer = menus.buy(Menus.counted("crown", 0), 10)
                .limit("crown-limit-global", RepeatPolicy.limited(2).persistent(false), LimitScope.GLOBAL).build();

        assertTrue(Transactions.buy(menus.context(alex), offer, 1).isSuccess());
        assertTrue(Transactions.buy(menus.context(bia), offer, 1).isSuccess());

        assertEquals(TransactionResult.Failure.LIMIT_REACHED, Transactions.buy(menus.context(alex), offer, 1).getFailure(),
                "Bia's purchase counted against the same server-wide limit");
    }

    @Test
    void aQuoteNeverUsesTheLimit() {
        TestFPlayerSender alex = menus.player("Alex", 500);
        Offer offer = threeADay("sword-d");
        for (int i = 0; i < 10; i++) {
            assertTrue(Transactions.quote(menus.context(alex), offer, 1).isSuccess());
        }

        for (int i = 0; i < 3; i++) {
            assertTrue(Transactions.buy(menus.context(alex), offer, 1).isSuccess(), "purchase " + (i + 1) + " after ten quotes");
        }
    }
}
