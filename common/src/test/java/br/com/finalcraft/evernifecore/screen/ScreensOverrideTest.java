package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A screen key resolves through four layers: the caller's replacement, the content owner, the server, the core's default. */
class ScreensOverrideTest {

    private static final ScreenKey SHOP_CONFIRM = ScreenKey.of("shop", "buy-confirm");

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender alex;
    private ECPluginData shop;
    private ECPluginData menus;
    private ECPluginData admin;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
        shop = Plugins.fakePluginData("Shop", tempDir.resolve("shop").toFile());
        menus = Plugins.fakePluginData("FinalMenus", tempDir.resolve("menus").toFile());
        admin = Plugins.fakePluginData("AdminPack", tempDir.resolve("admin").toFile());
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Screens.closeOwnedBy(shop);
        Screens.closeOwnedBy(menus);
        Screens.closeOwnedBy(admin);
        Plugins.forget("Shop");
        Plugins.forget("FinalMenus");
        Plugins.forget("AdminPack");
        Layouts.clear();
        world.close();
    }

    private static Screen<?> titled(String title) {
        return Screen.of(1).title(title);
    }

    private static ScreenRequest confirmOf(ECPluginData owner) {
        return ScreenRequest.of(owner).payload(ConfirmRequest.of("Buy it?").owner(owner));
    }

    /** Opens {@code key} for Alex, hands back the title it drew, and closes it again. */
    private String opened(ScreenKey key, ScreenRequest request) {
        ScreenView view = Screens.open(alex, key, request).join();
        String title = view.getCurrentTitle();
        view.close();
        world.advanceTicks(2);
        return title;
    }

    @Test
    void theCoreDefaultAnswersAKeyNobodyOverrides() {
        ScreenView view = Screens.open(alex, ScreenKey.CONFIRM, confirmOf(shop)).join();

        assertInstanceOf(ConfirmScreen.class, view.getScreen());
        assertEquals("Buy it?", view.getCurrentTitle());
        assertEquals(ScreenKey.CONFIRM, view.getScreen().getKey());
        view.close();
        world.advanceTicks(2);
    }

    @Test
    void theLayersResolveCallerThenContentOwnerThenServerThenDefault() {
        Screens.override(admin, ScreenKey.CONFIRM, ScreenScope.server(), ctx -> titled("server"));
        Screens.override(shop, ScreenKey.CONFIRM, ScreenScope.ownedBy(shop), ctx -> titled("shop's own"));
        Screens.override(shop, SHOP_CONFIRM, ScreenScope.server(), ctx -> titled("the caller's pick"));

        assertEquals("the caller's pick", opened(ScreenKey.CONFIRM, confirmOf(shop).withScreen(SHOP_CONFIRM)));
        assertEquals("shop's own", opened(ScreenKey.CONFIRM, confirmOf(shop)));
        assertEquals("server", opened(ScreenKey.CONFIRM, confirmOf(menus)), "another owner's content falls to the server layer");
    }

    @Test
    void aServerOverrideBeatsTheCoreDefault() {
        Screens.override(menus, ScreenKey.CONFIRM, ScreenScope.server(), ctx -> titled("menus asks: " + ctx.getRequest(ConfirmRequest.class).getQuestion()));

        assertEquals("menus asks: Buy it?", opened(ScreenKey.CONFIRM, confirmOf(shop)));
    }

    @Test
    void aSecondServerOverrideThrowsNamingBothPlugins() {
        Screens.override(menus, ScreenKey.CONFIRM, ScreenScope.server(), ctx -> titled("menus"));

        IllegalStateException clash = assertThrows(IllegalStateException.class,
                () -> Screens.override(admin, ScreenKey.CONFIRM, ScreenScope.server(), ctx -> titled("admin")));

        assertTrue(clash.getMessage().contains("[FinalMenus]") && clash.getMessage().contains("[AdminPack]"), clash.getMessage());
        assertTrue(clash.getMessage().contains("ScreenScope.ownedBy"), "it teaches the way out: " + clash.getMessage());
        assertEquals("menus", opened(ScreenKey.CONFIRM, confirmOf(shop)), "the refused one changed nothing");
    }

    @Test
    void cancellingAnOverrideHandsTheKeyBackToTheLayerBelow() {
        Screens.override(admin, ScreenKey.CONFIRM, ScreenScope.server(), ctx -> titled("server"));
        Cancellable own = Screens.override(shop, ScreenKey.CONFIRM, ScreenScope.ownedBy(shop), ctx -> titled("shop's own"));
        assertEquals("shop's own", opened(ScreenKey.CONFIRM, confirmOf(shop)));

        own.cancel();
        own.cancel();

        assertEquals("server", opened(ScreenKey.CONFIRM, confirmOf(shop)));
    }

    @Test
    void thePluginsShutdownTakesItsOverridesBack() {
        Screens.override(menus, ScreenKey.CONFIRM, ScreenScope.server(), ctx -> titled("menus"));

        Screens.closeOwnedBy(menus);

        assertEquals("Buy it?", opened(ScreenKey.CONFIRM, confirmOf(shop)), "the core's default answers again");
        Screens.override(admin, ScreenKey.CONFIRM, ScreenScope.server(), ctx -> titled("admin"));
    }

    @Test
    void aKeyNobodyAnswersRefusesSayingWhatToRegister() {
        CompletionException refused = assertThrows(CompletionException.class,
                () -> Screens.open(alex, SHOP_CONFIRM, ScreenRequest.of(shop)).join());

        String message = refused.getCause().getMessage();
        assertTrue(message.contains("shop:buy-confirm") && message.contains("Screens.override"), message);
        assertEquals(0, Screens.getOpenCount());
    }

    @Test
    void whichNamesTheLayerThatWouldAnswer() {
        assertEquals("evernifecore:confirm: the core's default", Screens.which(ScreenKey.CONFIRM, ScreenRequest.of(shop)));
        Screens.override(shop, ScreenKey.CONFIRM, ScreenScope.ownedBy(shop), ctx -> titled("shop's own"));

        assertEquals("evernifecore:confirm: the content owner's override by [Shop]", Screens.which(ScreenKey.CONFIRM, ScreenRequest.of(shop)));
        assertEquals("evernifecore:confirm: the core's default", Screens.which(ScreenKey.CONFIRM, ScreenRequest.of(menus)));
        assertNull(Screens.which(SHOP_CONFIRM, ScreenRequest.of(shop)));
    }

    @Test
    void aFallbackByKeyOpensWhatAnswersThatKey() {
        Screens.override(shop, SHOP_CONFIRM, ScreenScope.server(), ctx -> titled("no storage here"));
        Screen<?> backpack = Screen.of(3).owner(shop).onMissingCapability(ScreenFallback.open(SHOP_CONFIRM));
        backpack.storage(Slots.of(0));

        ScreenView view = world.open(backpack, alex);

        assertEquals("no storage here", view.getCurrentTitle());
        view.close();
        world.advanceTicks(2);
    }

}
