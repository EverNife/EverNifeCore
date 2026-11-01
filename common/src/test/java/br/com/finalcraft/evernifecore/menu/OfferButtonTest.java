package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ConfirmScreen;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Layouts;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.Slots;
import br.com.finalcraft.evernifecore.screen.prompt.PromptRegistry;
import br.com.finalcraft.evernifecore.testing.CountedGoods;
import br.com.finalcraft.evernifecore.testing.Menus;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The button of an offer on a screen: which gesture trades what, the prompt, the confirmation, the texts. */
class OfferButtonTest extends MenuTestWorlds {

    private static final Icon SWORD_ICON = Icon.of(ItemSpec.of("minecraft:iron_sword", 1)).displayName("Sword").lore("Buy: {price}", "Sell: {sell-price}");

    private TestFPlayerSender alex;
    private CountedGoods swords;

    @BeforeEach
    void setup() {
        Layouts.clear();
        alex = menus.player("Alex", 1000);
        swords = Menus.counted("sword", 5).stackSize(16);
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }

    private ScreenView open(Menu menu) {
        ScreenView view = screens.open(menu.getScreen(), alex);
        screens.advanceTicks(1);
        return view;
    }

    private void close(ScreenView view) {
        view.close();
        screens.advanceTicks(2);
    }

    private Menu shop(Offer... offers) {
        return Menu.of(shop.getOwner(), "shop").offer("sword", Slots.of(13), SWORD_ICON, offers).build();
    }

    private Menu market() {
        return shop(menus.buy(swords, 10).build(), menus.sell(swords, 4).build());
    }

    @Test
    void leftBuysOneAndTheBalanceDrops() {
        ScreenView view = open(market());

        menus.clickOffer(view, "sword", ClickGesture.PRIMARY);

        assertEquals(6, swords.getHeld());
        assertAmount("990", menus.balance(menus.coins(), alex));
        close(view);
    }

    @Test
    void shiftLeftBuysAStack() {
        ScreenView view = open(market());

        menus.clickOffer(view, "sword", ClickGesture.SHIFT_PRIMARY);

        assertEquals(5 + 16, swords.getHeld());
        assertAmount("840", menus.balance(menus.coins(), alex));
        close(view);
    }

    @Test
    void rightSellsOne() {
        ScreenView view = open(market());

        menus.clickOffer(view, "sword", ClickGesture.SECONDARY);

        assertEquals(4, swords.getHeld());
        assertAmount("1004", menus.balance(menus.coins(), alex));
        close(view);
    }

    @Test
    void middleAsksHowManyAndThirtyTwoBuysThirtyTwo() {
        ScreenView view = open(market());

        menus.clickOffer(view, "sword", ClickGesture.MIDDLE);
        assertEquals(1, PromptRegistry.get().getPendingCount(), "the quantity is asked through the prompt registry");
        menus.answerPrompt("32");

        assertEquals(5 + 32, swords.getHeld());
        assertAmount("680", menus.balance(menus.coins(), alex));
        close(view);
    }

    @Test
    void aCancelledPromptIsCancelledAndChargesNothing() {
        ScreenView view = open(market());
        menus.clickOffer(view, "sword", ClickGesture.MIDDLE);

        menus.answerPrompt("cancel");

        assertEquals(5, swords.getHeld());
        assertAmount("1000", menus.balance(menus.coins(), alex));
        assertTrue(alex.anyMessageContains("No quantity was given"), alex.getMessages().toString());
        close(view);
    }

    @Test
    void aConfirmedOfferAsksFirstAndYesTrades() {
        ScreenView view = open(shop(menus.buy(swords, 10).confirm().build()));

        menus.clickOffer(view, "sword", ClickGesture.PRIMARY);
        assertTrue(Screens.getOpenView(alex).getScreen() instanceof ConfirmScreen, "the confirmation is on top");
        assertEquals(5, swords.getHeld(), "nothing traded before the answer");
        menus.confirm(alex, true);

        assertEquals(6, swords.getHeld());
        assertAmount("990", menus.balance(menus.coins(), alex));
        close(Screens.getOpenView(alex));
    }

    @Test
    void noOnTheConfirmationTradesNothingAndChargesNothing() {
        ScreenView view = open(shop(menus.buy(swords, 10).confirm().build()));
        menus.clickOffer(view, "sword", ClickGesture.PRIMARY);

        menus.confirm(alex, false);

        assertEquals(5, swords.getHeld());
        assertAmount("1000", menus.balance(menus.coins(), alex));
        assertEquals(view, Screens.getOpenView(alex), "the shop is back");
        close(view);
    }

    @Test
    void aDenialResolvesMissingAndChargesNothing() {
        Menu menu = Menu.of(shop.getOwner(), "shop").denyText("§cYou still need: {missing}")
                .offer("sword", Slots.of(13), SWORD_ICON, menus.buy(swords, 5000).build()).build();
        ScreenView view = open(menu);

        menus.clickOffer(view, "sword", ClickGesture.PRIMARY);

        assertTrue(alex.anyMessageContains("You still need: Not enough"), alex.getMessages().toString());
        assertEquals(5, swords.getHeld());
        assertAmount("1000", menus.balance(menus.coins(), alex));
        close(view);
    }

    @Test
    void aReadOnlyOfferTradesOnNoClick() {
        ScreenView view = open(shop(menus.buy(swords, 10).readOnly().build(), menus.sell(swords, 4).readOnly().build()));

        for (ClickGesture gesture : new ClickGesture[]{ClickGesture.PRIMARY, ClickGesture.SHIFT_PRIMARY, ClickGesture.SECONDARY,
                ClickGesture.SHIFT_SECONDARY, ClickGesture.MIDDLE}) {
            menus.clickOffer(view, "sword", gesture);
        }

        assertEquals(5, swords.getHeld());
        assertAmount("1000", menus.balance(menus.coins(), alex));
        close(view);
    }

    @Test
    void theBuyRuleDoesNotAnswerARightClick() {
        ScreenView view = open(shop(menus.buy(swords, 10).build()));

        menus.clickOffer(view, "sword", ClickGesture.SECONDARY);

        assertEquals(5, swords.getHeld(), "a button with only a BUY offer has no rule for the sell gesture");
        assertAmount("1000", menus.balance(menus.coins(), alex));
        close(view);
    }

    @Test
    void theLoreReadsPriceAndSellPriceInTheCurrencyFormat() {
        ScreenView view = open(market());
        screens.advanceTicks(2);

        List<String> lore = screens.getRenderer().surfaceOf(view).getSlot(13).getItem().getLore();

        assertEquals("Buy: " + menus.coins().format(new BigDecimal("10")).toLegacyString(), lore.get(0));
        assertEquals("Sell: " + menus.coins().format(new BigDecimal("4")).toLegacyString(), lore.get(1));
        close(view);
    }
}
