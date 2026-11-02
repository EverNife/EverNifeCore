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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void pagedOffersDrawTheFrameworkArrowsOnTheSlotsGiven() {
        List<Offer> thirty = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            thirty.add(menus.buy(Goods.ofItem(ItemSpec.of("minecraft:iron_sword", 1)), 10 + i).build());
        }
        Menu menu = Menu.of(shop.getOwner(), "paged").offers(Slots.row(1), Slots.of(18), Slots.of(26), () -> thirty).build();

        ScreenView view = open(menu);

        assertNotNull(menu.getOfferList(), "the paged list is handed back to place its arrows");
        assertEquals("offers#next", screens.getRenderer().surfaceOf(view).getLastPatch(26).getElementId());
        assertEquals("offers#previous", screens.getRenderer().surfaceOf(view).getLastPatch(18).getElementId());
        close(view);
    }

    @Test
    void theOfferListArrowsTakeTheIconsGiven() {
        List<Offer> thirty = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            thirty.add(menus.buy(Goods.ofItem(ItemSpec.of("minecraft:iron_sword", 1)), 10).build());
        }
        Menu menu = Menu.of(shop.getOwner(), "paged").offers(Slots.row(1), Slots.of(18), Slots.of(26), () -> thirty).build();
        menu.getOfferList().arrows(null, Icon.of(ItemSpec.of("minecraft:arrow", 1)));

        ScreenView view = open(menu);

        assertEquals("minecraft:arrow", screens.getRenderer().surfaceOf(view).getSlot(26).getItem().getMaterialId());
        close(view);
    }

    @Test
    void aSecondPagedOfferListIsRefused() {
        Menu.Builder builder = Menu.of(shop.getOwner(), "paged").offers(Slots.row(1), ArrayList::new);

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> builder.offers(Slots.row(2), ArrayList::new));
        assertTrue(refused.getMessage().contains("a menu has one"), refused.getMessage());
    }

    @Test
    void aMenuWithoutPagedOffersHasNoOfferList() {
        assertNull(market().getOfferList());
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

    private String question() {
        ScreenView top = Screens.getOpenView(alex);
        assertTrue(top.getScreen() instanceof ConfirmScreen, "the confirmation is on top, not " + top.getScreen());
        return ((ConfirmScreen) top.getScreen()).getRequest().getQuestion();
    }

    @Test
    void aConfirmedSellOfEverythingHeldConfirmsTheWholeLot() {
        CountedGoods diamonds = Menus.counted("diamond", 30);
        ScreenView view = open(shop(menus.sell(diamonds, 10).confirm().build()));

        menus.clickOffer(view, "sword", ClickGesture.SHIFT_SECONDARY);
        String question = question();
        assertTrue(question.contains("30x diamond") && question.contains("300"), question);
        menus.confirm(alex, true);

        assertEquals(0, diamonds.getHeld());
        assertAmount("1300", menus.balance(menus.coins(), alex));
        close(Screens.getOpenView(alex));
    }

    @Test
    void aConfirmedAskAsksTheNumberFirstThenConfirmsIt() {
        ScreenView view = open(shop(menus.buy(swords, 10).confirm().build()));

        menus.clickOffer(view, "sword", ClickGesture.MIDDLE);
        assertEquals(1, PromptRegistry.get().getPendingCount(), "the number is asked before anything is confirmed");
        menus.answerPrompt("5");
        String question = question();
        assertTrue(question.contains("5x sword") && question.contains("50"), question);
        assertEquals(5, swords.getHeld(), "nothing traded before the answer");
        menus.confirm(alex, true);

        assertEquals(10, swords.getHeld());
        assertAmount("950", menus.balance(menus.coins(), alex));
        close(Screens.getOpenView(alex));
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
