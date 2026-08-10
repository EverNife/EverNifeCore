package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocalePDSection;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.GuiLayout;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.IconData;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Layouts;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * How a screen's text is decided, and who each decision speaks about.
 *
 * <p>Two people are involved and they are not the same person: the VIEWER has the window open, and the
 * SUBJECT is whose data is on screen. Permission and language follow the viewer; {@code %playerdata_*%}
 * follows the subject.</p>
 *
 * <p>Five steps run over that text, in this order: the viewer's language block, the icon's own scopes,
 * the screen's replacers, the subject's placeholders, and finally PlaceholderAPI. Every assertion here
 * reads what the BUFFER wrote for one viewer, because that is the only place a viewer exists.</p>
 */
class TextResolutionOrderTest {

    /** What the icon answers on its own. */
    private static final RegexReplacer<String> ICON_SCOPE = new RegexReplacer<String>()
            .addParser("owner", answer -> answer);

    /** What the screen around it answers - on purpose for keys the steps after it also answer. */
    private static final RegexReplacer<String> MENU = new RegexReplacer<String>()
            .addParser("owner", answer -> answer)
            .addParser("playerdata_name", answer -> answer);

    @TempDirNobodyCleans
    Path tempDir;

    private GuiTestWorld world;
    private ECPluginData plugin;
    private boolean perPlayerLocaleBefore;

    /** A staff screen: a button only staff may see, next to a line about the player being inspected. */
    @GuiLayout(title = "Profile", rows = 3)
    public static class ProfileLayout extends LayoutBase {

        @IconData(slot = {0}, permission = "profile.inspect")
        public Icon INSPECT = Icon.of(new ItemStack(Material.ANVIL)).displayName("Inspect %playerdata_name%");

        @IconData(slot = {1})
        public Icon SUMMARY = Icon.of(new ItemStack(Material.PAPER))
                .displayName("%playerdata_name%")
                .lore("%playerdata_uuid%");

        //what a viewer who may not see the button gets instead, so an unpainted slot is not a blank one
        @IconData(slot = {0}, background = true)
        public Icon FILLER = Icon.of(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
    }

    /**
     * One text with a token for every step: {@code %owner%} the icon and the screen both answer,
     * {@code %playerdata_name%} the screen and the subject both answer, {@code %playerdata_uuid%} the
     * subject and PlaceholderAPI both answer, and {@code %papi_rank%} only PlaceholderAPI answers.
     */
    @GuiLayout(title = "Order", rows = 3, integrateToPAPI = true)
    public static class OrderLayout extends LayoutBase {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.EN_US,
                        text = "EN %owner% %playerdata_name% %papi_rank% %playerdata_uuid%"),
                @FCLocale(lang = LocaleType.PT_BR,
                        text = "PT %owner% %playerdata_name% %papi_rank% %playerdata_uuid%")
        })
        public Icon TICKET = Icon.of(new ItemStack(Material.PAPER));
    }

    /** The same text on a screen that never asked for PlaceholderAPI. */
    @GuiLayout(title = "Quiet", rows = 3)
    public static class QuietOrderLayout extends LayoutBase {

        @IconData(slot = {0}, locale = {
                @FCLocale(lang = LocaleType.EN_US,
                        text = "EN %owner% %playerdata_name% %papi_rank% %playerdata_uuid%")
        })
        public Icon TICKET = Icon.of(new ItemStack(Material.PAPER));
    }

    @BeforeEach
    void setup() {
        world = GuiTestWorld.installWithItemMetadata(tempDir);
        plugin = world.getPluginData();
        Plugins.setLanguage(plugin, LocaleType.EN_US);

        perPlayerLocaleBefore = ECSettings.PER_PLAYER_LOCALE;
        ECSettings.PER_PLAYER_LOCALE = true;
        PlayerController.initialize(Storages.memory().writeTo(tempDir));

        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        PlayerDataWorld.tearDown();
        ECSettings.PER_PLAYER_LOCALE = perPlayerLocaleBefore;
        if (world != null) {
            world.close();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Two people, one screen
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A staff member reading somebody else's profile: the button's permission is asked of the staff, and
     * the text on it speaks of the player being read.
     *
     * <p>Both halves are checked because either one alone passes on a screen where viewer and subject are
     * the same person, which is precisely the case this class does not cover.</p>
     */
    @Test
    void aPermissionAsksTheViewerWhileThePlaceholdersAnswerForTheSubject() {
        PlayerData inspected = registered("Inspected");
        LayoutGui<PlayerData, ProfileLayout> gui =
                new LayoutGui<>(Layouts.of(ProfileLayout.class), inspected);

        PlayerDouble staff = world.newPlayer("Staff").withPermission("profile.inspect");
        world.openDetached(gui, staff);
        SurfaceDouble byStaff = world.getSurface();

        PlayerDouble bystander = world.newPlayer("Bystander");
        world.openDetached(gui, bystander);
        SurfaceDouble byBystander = world.getSurface();

        assertEquals(Material.ANVIL, byStaff.getItem(0).getType(),
                "the permission is checked against whoever has the window open");
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, byBystander.getItem(0).getType(),
                "and the same screen about the same player hides the button from a viewer without it");
        assertEquals("Inspect Inspected", nameAt(byStaff, 0),
                "the button the viewer earned still talks about the subject");

        assertEquals("Inspected", nameAt(byStaff, 1));
        assertEquals("Inspected", nameAt(byBystander, 1),
                "the subject's placeholders do not follow the viewer, so both read the same name");
        assertNotEquals("Staff", nameAt(byStaff, 1),
                "a screen that answered with the viewer's own name would be a screen about the wrong person");
        assertEquals(Arrays.asList(inspected.getUniqueId().toString()), loreAt(byStaff, 1));
        assertNotEquals(staff.getUniqueId().toString(), loreAt(byStaff, 1).get(0));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The order the five steps run in
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * One text, five steps, and a swap of any two neighbours changes what it reads.
     *
     * <p>The text lives in a language block, so it does not exist before the first step runs: were the
     * substitutions to happen first they would find an icon with no text at all, and the name would arrive
     * still carrying its raw tokens. From there each remaining pair contests one token - two steps answer
     * it and only the earlier one may win, because whoever resolves a token first leaves nothing behind
     * for the next step to see:</p>
     *
     * <ul>
     *   <li>{@code %owner%} - the icon's scope against the screen's replacer;</li>
     *   <li>{@code %playerdata_name%} - the screen's replacer against the subject;</li>
     *   <li>{@code %playerdata_uuid%} - the subject against PlaceholderAPI;</li>
     *   <li>{@code %papi_rank%} - nobody but PlaceholderAPI, which is what makes its absence visible.</li>
     * </ul>
     */
    @Test
    void everyStepResolvesWhatTheStepsAfterItWouldHaveAnsweredDifferently() {
        PlayerData subject = registered("Subject");
        OrderLayout layout = Layouts.of(OrderLayout.class);
        layout.getIcon("TICKET").addScope(ICON_SCOPE, "scope-owner");
        LayoutGui<PlayerData, OrderLayout> gui = new LayoutGui<>(layout, subject);
        gui.addReplacer(MENU, "menu-answer");

        try (ECoreTestWorld papi = Platforms.lenient().parsingWith(lastResort()).install()) {
            world.openDetached(gui, viewerSpeaking("Reader", LocaleType.EN_US));
            SurfaceDouble english = world.getSurface();
            world.openDetached(gui, viewerSpeaking("Leitor", LocaleType.PT_BR));
            SurfaceDouble brazilian = world.getSurface();

            assertFalse(nameAt(english, 0).contains("%"),
                    "a token still standing is a substitution that ran before the text it was written in "
                            + "existed: " + nameAt(english, 0));

            String expected = "scope-owner menu-answer diamond " + subject.getUniqueId();
            assertEquals("EN " + expected, nameAt(english, 0));
            assertEquals("PT " + expected, nameAt(brazilian, 0),
                    "the language picks the sentence; the four steps after it fill the same blanks");
        }
    }

    /**
     * The last step, switched off: without {@code integrateToPAPI} the token nobody else answers is
     * simply left standing, which is what makes its resolution above a statement about PlaceholderAPI
     * and not about the steps before it.
     */
    @Test
    void aScreenThatNeverAskedForPlaceholderApiLeavesItsTokensAlone() {
        PlayerData subject = registered("Subject");
        QuietOrderLayout layout = Layouts.of(QuietOrderLayout.class);
        layout.getIcon("TICKET").addScope(ICON_SCOPE, "scope-owner");
        LayoutGui<PlayerData, QuietOrderLayout> gui = new LayoutGui<>(layout, subject);
        gui.addReplacer(MENU, "menu-answer");

        try (ECoreTestWorld papi = Platforms.lenient().parsingWith(lastResort()).install()) {
            world.openDetached(gui, viewerSpeaking("Reader", LocaleType.EN_US));

            assertEquals("EN scope-owner menu-answer %papi_rank% " + subject.getUniqueId(),
                    nameAt(world.getSurface(), 0),
                    "the four steps that are not PlaceholderAPI answered, and nothing else did");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A PlaceholderAPI that answers every token still standing when it is reached. It is deliberately
     * able to answer the earlier steps' keys too, so a step that failed to run leaves its own name behind
     * instead of a hole that could pass for anything.
     */
    private static BiFunction<FPlayer, String, String> lastResort() {
        return (player, text) -> text
                .replace("%papi_rank%", "diamond")
                .replace("%owner%", "papi-owner")
                .replace("%playerdata_name%", "papi-name")
                .replace("%playerdata_uuid%", "papi-uuid");
    }

    /** A player the storage knows, to be the subject of a screen somebody else is looking at. */
    private PlayerData registered(String name) {
        return PlayerController.handleLogin(UUID.randomUUID(), name).join();
    }

    /** A viewer who has chosen a language of their own. */
    private PlayerDouble viewerSpeaking(String name, String language) {
        PlayerDouble player = world.newPlayer(name);
        PlayerController.handleLogin(player.getUniqueId(), name).join()
                .getPDSection(LocalePDSection.class).join().setLang(language);
        return player;
    }

    private static String nameAt(SurfaceDouble surface, int slot) {
        ItemStack drawn = surface.getItem(slot);
        return drawn == null || drawn.getItemMeta() == null ? null : drawn.getItemMeta().getDisplayName();
    }

    private static List<String> loreAt(SurfaceDouble surface, int slot) {
        ItemStack drawn = surface.getItem(slot);
        return drawn == null || drawn.getItemMeta() == null ? null : drawn.getItemMeta().getLore();
    }

}
