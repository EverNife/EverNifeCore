package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Text a plugin writes in code reaches the viewer the way text in a file does: {@code &} codes coloured, a value put in
 * later left as it is, and an explicit name on a ready-made icon drawn instead of the icon's language block.
 */
class CodeTextTest {

    /** A value that carries an {@code &} code of its own, as a player-chosen name would. */
    private static final RegexReplacer<String> SELLER = new RegexReplacer<String>()
            .addParser("seller", seller -> seller);

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender alex;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
        FCLocaleManager.loadLocale(EverNifeCore.getEcPluginData(), DefaultIcons.class);
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        ScreenView open = Screens.getOpenView(alex);
        if (open != null) {
            open.close();
            world.advanceTicks(1);
        }
        Layouts.clear();
        world.close();
    }

    private RecordingSurface open(Screen<?> screen) {
        return world.getRenderer().surfaceOf(world.open(screen, alex));
    }

    @Test
    void ampersandCodesInCodeTextAreColoured() {
        Screen<?> screen = Screen.of(3).title("&8Market");
        screen.icon(13, Icon.of("minecraft:emerald").displayName("&aBuy").lore("&7Cheap", "&#FF8800Hot"));

        RecordingSurface surface = open(screen);

        assertEquals("§8Market", surface.getView().getCurrentTitle());
        assertEquals("§aBuy", surface.getSlot(13).getItem().getDisplayName());
        assertEquals(Arrays.asList("§7Cheap", "§x§F§F§8§8§0§0Hot"), surface.getSlot(13).getItem().getLore());
    }

    @Test
    void aTitleReadOnEveryPassIsColouredToo() {
        Screen<?> screen = Screen.of(3).title(() -> "&#FF8800Page 1");

        assertEquals("§x§F§F§8§8§0§0Page 1", open(screen).getView().getCurrentTitle());
    }

    @Test
    void aHexCodeInAnIconNameIsColoured() {
        Screen<?> screen = Screen.of(3);
        screen.icon(13, Icon.of("minecraft:emerald").displayName("&#FF8800Gold"));

        assertEquals("§x§F§F§8§8§0§0Gold", open(screen).getSlot(13).getItem().getDisplayName());
    }

    @Test
    void aConfirmationQuestionIsColoured() {
        Screens.confirm(alex, ConfirmRequest.of("&cSell it?"));
        ScreenView view = Screens.getOpenView(alex);

        assertNotNull(view);
        assertEquals("§cSell it?", view.getCurrentTitle());
    }

    @Test
    void aValuePutInByAReplacerIsNotColoured() {
        Screen<?> screen = Screen.of(3).title("&8Sold by %seller%").addReplacer(SELLER, "&cBob");
        screen.icon(13, Icon.of("minecraft:emerald").displayName("&a%seller%").lore("&7%seller%"));

        RecordingSurface surface = open(screen);

        assertEquals("§8Sold by &cBob", surface.getView().getCurrentTitle());
        assertEquals("§a&cBob", surface.getSlot(13).getItem().getDisplayName());
        assertEquals(Collections.singletonList("§7&cBob"), surface.getSlot(13).getItem().getLore());
    }

    @Test
    void anExplicitNameOnAReadyMadeIconIsWhatTheViewerReads() {
        Icon info = DefaultIcons.info();
        assertNotNull(info.getLocale(), "the ready-made icon carries its language block");

        Screen<?> screen = Screen.of(3);
        screen.icon(13, info.displayName("§bAbout the shop").lore("§7Open all day"));
        RecordingSurface surface = open(screen);

        assertEquals("§bAbout the shop", surface.getSlot(13).getItem().getDisplayName());
        assertEquals(Collections.singletonList("§7Open all day"), surface.getSlot(13).getItem().getLore());
    }

    @Test
    void aReadyMadeIconWithAnExplicitNameLoadsInALayoutFieldWithoutALocale() {
        NamedConfirmLayout layout = Layouts.of(NamedConfirmLayout.class);
        Icon confirm = layout.getIcon("CONFIRM");
        assertNotNull(confirm, "a single text, given in one place, is not refused");
        assertNull(confirm.getLocale());

        Screen<NamedConfirmLayout> screen = Screen.of(layout);
        RecordingSurface surface = open(screen);

        assertEquals("§aSell the sword", surface.getSlot(11).getItem().getDisplayName());
    }

    @ScreenLayout(title = "Sell", rows = 3)
    public static class NamedConfirmLayout extends Layout {

        @IconData(slot = {11})
        public Icon CONFIRM = DefaultIcons.confirm().displayName("&aSell the sword");
    }

}
