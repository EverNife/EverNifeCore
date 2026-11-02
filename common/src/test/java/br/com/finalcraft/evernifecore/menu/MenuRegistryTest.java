package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.IECPluginBootstrap;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ClickRule;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenNavigation;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Menus by name: open with arguments, the open-menu effect, the teaching refusals, and the owner's shutdown. */
class MenuRegistryTest extends MenuTestWorlds {

    @AfterEach
    void forgetMenus() {
        MenuRegistry.get().unregisterAll(shop.getOwner());
    }

    private Menu register(String name, String title) {
        Menu menu = Menu.of(shop.getOwner(), name).title(title)
                .offer("sword", 13, menus.buy(Goods.ofItem(ItemSpec.of("minecraft:iron_sword", 1)), 10).build()).build();
        MenuRegistry.get().register(shop.getOwner(), menu);
        return menu;
    }

    private void close(ScreenView view) {
        view.close();
        screens.advanceTicks(2);
    }

    @Test
    void openingByIdHandsTheViewerAndArgumentsToTheScreenAndResolvesTheTitle() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        register("vip", "Shop of {0}");

        ScreenView view = MenuRegistry.get().open(alex, "FakeShop:VIP", Collections.singletonList("Bia")).join();
        screens.advanceTicks(1);

        assertEquals(alex, view.getViewer());
        assertEquals(Collections.<Object>singletonList("Bia"), view.getArguments());
        assertEquals("Shop of Bia", view.getCurrentTitle());
        close(view);
    }

    @Test
    void aMenuTitleColoursItsOwnCodesAndLeavesAnArgumentAsItCame() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        register("vip", "&6Shop of {0}");

        ScreenView view = MenuRegistry.get().open(alex, "FakeShop:VIP", Collections.singletonList("&cBia")).join();
        screens.advanceTicks(1);

        assertEquals("§6Shop of &cBia", view.getCurrentTitle());
        close(view);
    }

    @Test
    void theOpenMenuEffectOpensOnTopWithItsArgumentsAndBackReturns() throws Exception {
        TestFPlayerSender alex = menus.player("Alex", 0);
        register("vip", "VIP of {0}");
        Effect openVip = Effects.parse(shop.getOwner(), new ObjectMapper().readTree(
                "{\"type\": \"open-menu\", \"menu\": \"fakeshop:vip\", \"args\": [\"{player}\"]}"), ZoneOffset.UTC);
        Screen<?> hub = Screen.of(3).title("Hub");
        hub.icon(0, Icon.of(ItemSpec.of("minecraft:chest", 1))).id("to-vip").rule(ClickRule.on(ClickGesture.PRIMARY).onSuccess(openVip));
        ScreenView hubView = screens.open(hub, alex);

        screens.click(hubView, "to-vip", ClickGesture.PRIMARY);
        screens.advanceTicks(2);
        ScreenView vip = Screens.getOpenView(alex);
        assertEquals("VIP of Alex", vip.getCurrentTitle());

        ScreenNavigation.back(vip, null);
        screens.advanceTicks(2);
        assertSame(hubView, Screens.getOpenView(alex), "back returns to the screen the menu was opened from");
        close(hubView);
    }

    @Test
    void anUnknownIdIsRefusedNamingTheIdTheRegisteredMenusAndHowToRegister() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        register("vip", "VIP");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> MenuRegistry.get().open(alex, "fakeshop:missing", Collections.<String>emptyList()));

        assertTrue(refused.getMessage().contains("'fakeshop:missing'"), refused.getMessage());
        assertTrue(refused.getMessage().contains("[fakeshop:vip]"), refused.getMessage());
        assertTrue(refused.getMessage().contains("MenuRegistry.get().register(owner, Menu.of(owner"), refused.getMessage());
    }

    @Test
    void aPluginShutdownTakesOutItsMenusAndLeavesTheOthers() {
        ECPluginData other = Plugins.fakePluginData("OtherShop", tempDir.resolve("other").toFile());
        try {
            register("vip", "VIP");
            Menu theirs = Menu.of(other, "kits").offer("kit", 0, Offer.buy(Goods.ofItem(ItemSpec.of("minecraft:chest", 1)), Price.free()).build()).build();
            MenuRegistry.get().register(other, theirs);

            new IECPluginBootstrap() {
                @Override
                public ECPluginData getPluginData() {
                    return shop.getOwner();
                }

                @Override
                public void onECPluginEnable() {
                }

                @Override
                public void onECPluginShutdown() {
                }

                @Override
                public void onECPluginReload() {
                }
            }.onECPluginShutdownPre();

            assertNull(MenuRegistry.get().find("fakeshop:vip"), "the shop's menu left with its shutdown");
            assertNotNull(MenuRegistry.get().find("othershop:kits"), "another plugin's menu stays");
        } finally {
            MenuRegistry.get().unregisterAll(other);
            Plugins.forget("OtherShop");
        }
    }

    @Test
    void unregisterTakesOutOneMenuOfTheOwnerAndAnswersWhetherItWasThere() {
        register("vip", "VIP");
        register("kits", "Kits");

        assertTrue(MenuRegistry.get().unregister(shop.getOwner(), "vip"));

        assertNull(MenuRegistry.get().find("fakeshop:vip"));
        assertNotNull(MenuRegistry.get().find("fakeshop:kits"), "only the menu named left");
        assertFalse(MenuRegistry.get().unregister(shop.getOwner(), "fakeshop:vip"), "nothing is registered under it any more");
    }

    @Test
    void unregisteringAnotherPluginsMenuIsRefusedNamingBothOwners() {
        ECPluginData other = Plugins.fakePluginData("OtherShop", tempDir.resolve("other").toFile());
        try {
            MenuRegistry.get().register(other, Menu.of(other, "x")
                    .offer("kit", 0, Offer.buy(Goods.ofItem(ItemSpec.of("minecraft:chest", 1)), Price.free()).build()).build());

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> MenuRegistry.get().unregister(shop.getOwner(), "othershop:x"));

            String owner = shop.getOwner().getMetaInfo().getName();
            assertEquals("[" + owner + "] cannot unregister the menu othershop:x: it was registered by [OtherShop]. Only the owner takes"
                    + " a menu out.", refused.getMessage());
            assertNotNull(MenuRegistry.get().find("othershop:x"), "the refusal took nothing out");
        } finally {
            MenuRegistry.get().unregisterAll(other);
            Plugins.forget("OtherShop");
        }
    }

    @Test
    void aSecondRegistrationOfAnIdNamesBothOwners() {
        register("vip", "VIP");
        Menu again = Menu.of(shop.getOwner(), "vip").offer("x", 0, Offer.buy(Goods.ofItem(ItemSpec.of("minecraft:stone", 1)), Price.free()).build()).build();

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> MenuRegistry.get().register(shop.getOwner(), again));

        String owner = shop.getOwner().getMetaInfo().getName();
        assertTrue(refused.getMessage().contains("[" + owner + "] already registered the menu fakeshop:vip"), refused.getMessage());
        assertTrue(refused.getMessage().contains("and [" + owner + "] tried to register it again"), refused.getMessage());
    }

    @Test
    void aDeniedOpenGateOpensNothingAndTellsWhy() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        Menu vip = Menu.of(shop.getOwner(), "vip").openGate(Gate.builder().require(ctx -> ConditionResult.fail(FancyText.of("VIPs only."))).build())
                .offer("sword", 13, menus.buy(Goods.ofItem(ItemSpec.of("minecraft:iron_sword", 1)), 10).build()).build();
        MenuRegistry.get().register(shop.getOwner(), vip);

        CompletableFuture<ScreenView> opened = MenuRegistry.get().open(alex, "fakeshop:vip", Arrays.<String>asList());

        assertTrue(opened.isCompletedExceptionally());
        assertNull(Screens.getOpenView(alex));
        assertTrue(alex.anyMessageContains("VIPs only."), alex.getMessages().toString());
    }

    private Menu registerCharging(String name) {
        Menu menu = Menu.of(shop.getOwner(), name).openGate(Gate.builder().cost(Price.of(menus.coins(), 10).getLines().get(0).asCost(BigDecimal.TEN)).build())
                .offer("sword", 13, menus.buy(Goods.ofItem(ItemSpec.of("minecraft:iron_sword", 1)), 10).build()).build();
        MenuRegistry.get().register(shop.getOwner(), menu);
        return menu;
    }

    @Test
    void aRefusedOpenChargesNothingOfTheOpenGate() {
        TestFPlayerSender alex = menus.player("Alex", 1000);
        registerCharging("vip");
        screens.getRenderer().refuseOpens(true);

        CompletableFuture<ScreenView> opened = MenuRegistry.get().open(alex, "fakeshop:vip", Collections.<String>emptyList());
        screens.advanceTicks(2);

        assertTrue(opened.isCompletedExceptionally());
        assertNull(Screens.getOpenView(alex));
        assertEquals(0, new BigDecimal(1000).compareTo(menus.balance(menus.coins(), alex)), "nothing was charged for a window that never opened");
    }

    @Test
    void anOpenedMenuPaysItsOpenGateOnce() {
        TestFPlayerSender alex = menus.player("Alex", 1000);
        registerCharging("vip");

        ScreenView view = MenuRegistry.get().open(alex, "fakeshop:vip", Collections.<String>emptyList()).join();
        screens.advanceTicks(1);

        assertEquals(0, new BigDecimal(990).compareTo(menus.balance(menus.coins(), alex)));
        close(view);
    }

    @Test
    void aMenuRefusedOnTopChargesNothing() throws Exception {
        TestFPlayerSender alex = menus.player("Alex", 1000);
        registerCharging("vip");
        Effect openVip = Effects.parse(shop.getOwner(), new ObjectMapper().readTree(
                "{\"type\": \"open-menu\", \"menu\": \"fakeshop:vip\"}"), ZoneOffset.UTC);
        Screen<?> hub = Screen.of(3).title("Hub");
        hub.icon(0, Icon.of(ItemSpec.of("minecraft:chest", 1))).id("to-vip").rule(ClickRule.on(ClickGesture.PRIMARY).onSuccess(openVip));
        ScreenView hubView = screens.open(hub, alex);
        screens.getRenderer().refuseNextOpen();

        screens.click(hubView, "to-vip", ClickGesture.PRIMARY);
        screens.advanceTicks(2);

        assertEquals(0, new BigDecimal(1000).compareTo(menus.balance(menus.coins(), alex)));
        ScreenView back = Screens.getOpenView(alex);
        assertNotNull(back, "the way back to the hub was not refused, so the hub is open again");
        assertSame(hub, back.getScreen());
        close(back);
    }

    @Test
    void aMenuOpenedOnTopPaysItsOpenGate() throws Exception {
        TestFPlayerSender alex = menus.player("Alex", 1000);
        registerCharging("vip");
        Effect openVip = Effects.parse(shop.getOwner(), new ObjectMapper().readTree(
                "{\"type\": \"open-menu\", \"menu\": \"fakeshop:vip\"}"), ZoneOffset.UTC);
        Screen<?> hub = Screen.of(3).title("Hub");
        hub.icon(0, Icon.of(ItemSpec.of("minecraft:chest", 1))).id("to-vip").rule(ClickRule.on(ClickGesture.PRIMARY).onSuccess(openVip));
        ScreenView hubView = screens.open(hub, alex);

        screens.click(hubView, "to-vip", ClickGesture.PRIMARY);
        screens.advanceTicks(2);

        assertEquals("fakeshop:vip", String.valueOf(MenuRegistry.get().find("fakeshop:vip").getId()));
        assertTrue(Screens.getOpenView(alex) != hubView, "the menu is up on top");
        assertEquals(0, new BigDecimal(990).compareTo(menus.balance(menus.coins(), alex)));
        ScreenNavigation.back(Screens.getOpenView(alex), null);
        screens.advanceTicks(2);
        close(hubView);
    }

    @Test
    void theCatalogOfAMenuIsItsSellOffers() {
        TestFPlayerSender alex = menus.player("Alex", 0);
        Offer sell = menus.sell(Goods.ofItem(ItemSpec.of("minecraft:diamond", 1)), 5).build();
        Menu menu = Menu.of(shop.getOwner(), "buyback").offer("diamond", 0, sell).build();

        assertSame(sell, menu.asCatalog().sellOfferFor(menus.context(alex), ItemSpec.of("minecraft:diamond", 3)));
        assertNull(menu.asCatalog().sellOfferFor(menus.context(alex), ItemSpec.of("minecraft:stone", 3)));
    }
}
