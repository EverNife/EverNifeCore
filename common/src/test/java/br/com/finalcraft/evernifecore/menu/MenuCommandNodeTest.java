package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mountable menu verbs: open with arguments, list only the owner's menus, and the mount's permission. */
class MenuCommandNodeTest extends MenuTestWorlds {

    private FinalCmdTestHarness harness;
    private FinalCMDPluginCommand command;
    private ECPluginData other;

    @FinalCMD(aliases = "fakeshop")
    public static class ShopCommand {
        @FinalCMD.Node(subcmd = "menu", permission = "fakeshop.menu")
        public final MenuCommandNode menus;

        ShopCommand(MenuCommandNode menus) {
            this.menus = menus;
        }
    }

    @BeforeEach
    void mount() {
        Menu vip = Menu.of(shop.getOwner(), "vip").title("VIP of {0}")
                .offer("sword", 13, menus.buy(Goods.ofItem(ItemSpec.of("minecraft:iron_sword", 1)), 10).build()).build();
        MenuRegistry.get().register(shop.getOwner(), vip);
        other = Plugins.fakePluginData("OtherShop", tempDir.resolve("other").toFile());
        MenuRegistry.get().register(other, Menu.of(other, "kits")
                .offer("kit", 0, Offer.buy(Goods.ofItem(ItemSpec.of("minecraft:chest", 1)), Price.free()).build()).build());
        harness = new FinalCmdTestHarness("MenuCmd", tempDir.resolve("cmd"));
        command = harness.register(new ShopCommand(new MenuCommandNode(shop.getOwner(), "fakeshop.menu")));
    }

    @AfterEach
    void unmount() {
        harness.close();
        MenuRegistry.get().unregisterAll(shop.getOwner());
        MenuRegistry.get().unregisterAll(other);
        Plugins.forget("OtherShop");
    }

    @Test
    void openOpensTheMenuWithItsArguments() {
        TestFPlayerSender alex = menus.player("Alex", 0).grant("fakeshop.menu");

        harness.dispatch(command, alex, "menu open vip Bia Carl");
        screens.advanceTicks(2);

        ScreenView view = Screens.getOpenView(alex);
        assertNotNull(view, alex.getMessages().toString());
        assertEquals(Arrays.<Object>asList("Bia", "Carl"), view.getArguments());
        assertEquals("VIP of Bia", view.getCurrentTitle());
        view.close();
        screens.advanceTicks(2);
    }

    @Test
    void listListsOnlyTheMenusOfTheNodesOwner() {
        TestFPlayerSender alex = menus.player("Alex", 0).grant("fakeshop.menu");

        harness.dispatch(command, alex, "menu list");

        assertTrue(alex.anyMessageContains("fakeshop:vip"), alex.getMessages().toString());
        assertFalse(alex.anyMessageContains("othershop:kits"), "another plugin's menu is not this node's");
    }

    @Test
    void aSenderWithoutTheMountPermissionIsDenied() {
        TestFPlayerSender alex = menus.player("Alex", 0);

        harness.dispatch(command, alex, "menu open vip");
        screens.advanceTicks(2);

        assertNull(Screens.getOpenView(alex));
    }

    @Test
    void anotherPluginsMenuIsNotOpenedThroughThisNode() {
        TestFPlayerSender alex = menus.player("Alex", 0).grant("fakeshop.menu");

        harness.dispatch(command, alex, "menu open othershop:kits");
        screens.advanceTicks(2);

        assertNull(Screens.getOpenView(alex));
        assertTrue(alex.anyMessageContains("No menu 'othershop:kits' of"), alex.getMessages().toString());
    }

    @Test
    void openForNeedsTheOthersPermission() {
        TestFPlayerSender alex = menus.player("Alex", 0).grant("fakeshop.menu");
        TestFPlayerSender bia = menus.player("Bia", 0);

        harness.dispatch(command, alex, "menu openfor Bia vip");
        screens.advanceTicks(2);

        assertNull(Screens.getOpenView(bia));
        assertTrue(alex.anyMessageContains("fakeshop.menu.others"), alex.getMessages().toString());
    }
}
