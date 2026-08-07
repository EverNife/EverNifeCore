package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.cfg.ConfigSetting;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a layout class turns into on disk, and what the admin can do to it afterwards.
 *
 * <p>The file is the deliverable: it has to explain its own vocabulary, list the placeholders the
 * screen resolves, hold one block per language, keep an orphan key instead of deleting it, and let a
 * language overlay replace exactly the keys it names.</p>
 *
 * <p>Everything asserted here is read from the yml or from {@link LayoutDiff}. Building an
 * {@code ItemStack} through {@code FCItemFactory} needs the NBT api, which needs a running server, so
 * anything downstream of that is proved on a real server and not here.</p>
 */
class LayoutSeedTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;
    private ECPluginData plugin;

    @GuiLayout(title = "&9Shop: &0%category%", rows = 6, integrateToPAPI = true,
            locale = {
                    @FCLocale(lang = LocaleType.EN_US, text = "&9Shop: &0%category%"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "&9Loja: &0%category%")
            })
    public static class ShopLayout extends LayoutBase {

        @ConfigSetting(key = "Settings.taxRate", comment = @FCLocale(text = "Cut kept from every sale"))
        @Min(0)
        @Max(1)
        public Double taxRate = 0.05D;

        @IconData(slot = {10, 11, 12}, locale = {
                @FCLocale(lang = LocaleType.EN_US, text = "&a%product_name%",
                        hover = "&7Price: &6%product_price%"),
                @FCLocale(lang = LocaleType.PT_BR, text = "&a%product_name%",
                        hover = "&7Preco: &6%product_price%")
        })
        public Icon PRODUCT = Icon.of(new ItemStack(Material.CHEST));

        @IconData(slot = {22})
        public Icon UPGRADE = Icon.of(new ItemStack(Material.ANVIL))
                .addState("locked", new ItemStack(Material.BARRIER))
                .addState("maxed", new ItemStack(Material.NETHER_STAR));

        @IconData(slot = {45}, permission = "shop.admin")
        public Icon EDIT = Icon.of(new ItemStack(Material.ANVIL));

        @IconData(slot = {0, 1, 2, 3, 5, 6, 7, 8}, background = true)
        public Icon BACKDROP = Icon.of(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
    }

    /** Two icons per section, so a file that already holds one of each can be asked for the other. */
    @GuiLayout(title = "Bank", rows = 6)
    public static class BankLayout extends LayoutBase {

        @IconData(slot = {10})
        public Icon DEPOSIT = Icon.of(new ItemStack(Material.CHEST));

        @IconData(slot = {12})
        public Icon WITHDRAW = Icon.of(new ItemStack(Material.HOPPER));

        @IconData(slot = {0, 1}, background = true)
        public Icon TOP = Icon.of(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));

        @IconData(slot = {45, 46}, background = true)
        public Icon BOTTOM = Icon.of(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
    }

    @GuiLayout(title = "Base", rows = 3)
    public static class BaseScreen extends LayoutBase {

        @IconData(slot = {0})
        public Icon CLOSE = Icon.of(new ItemStack(Material.BARRIER));
    }

    @GuiLayout(title = "Child", rows = 3)
    public static class ChildScreen extends BaseScreen {

        @IconData(slot = {8})
        public Icon HELP = Icon.of(new ItemStack(Material.PAPER));
    }

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        plugin = ECPluginManager.getOrCreateECorePluginData(new Object());
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        if (world != null) {
            world.close();
        }
    }

    @Test
    void aFreshFileTeachesItsOwnVocabularyAndListsTheScreensPlaceholders() throws IOException {
        LayoutScanner.load(plugin, ShopLayout.class, null);

        String yaml = seededFile();
        assertTrue(yaml.contains("What you write here beats the plugin's default"),
                "the header has to say who wins:\n" + yaml);
        for (String key : Arrays.asList("Slot", "Permission", "DisplayItem", "States", "Locale")) {
            assertTrue(yaml.contains("#   " + key), "the header has to explain '" + key + "':\n" + yaml);
        }
        assertTrue(yaml.contains("%category%") && yaml.contains("%product_name%")
                && yaml.contains("%product_price%"), "every placeholder of the screen is listed:\n" + yaml);
    }

    @Test
    void everyIconIsSeededWithItsSlotPermissionStatesAndLanguages() throws IOException {
        LayoutScanner.load(plugin, ShopLayout.class, null);

        String yaml = seededFile();
        assertTrue(yaml.contains("Slot: \"[10,11,12]\""), "the slot list is written in one form:\n" + yaml);
        assertTrue(yaml.contains("Permission: shop.admin"), "a declared permission is seeded:\n" + yaml);
        assertTrue(yaml.contains("locked:") && yaml.contains("maxed:"), "each state gets a block:\n" + yaml);
        assertTrue(yaml.contains("EN_US:") && yaml.contains("PT_BR:"), "each language gets a block:\n" + yaml);
        assertTrue(yaml.contains("Background:"), "a background icon has its own section:\n" + yaml);
        assertTrue(yaml.contains("taxRate: 0.05"), "the settings of the class share the file:\n" + yaml);
        assertTrue(yaml.contains("At least 0") || yaml.contains("At most 1"),
                "a rule documents itself above the key it judges:\n" + yaml);
    }

    @Test
    void textDeclaredPerLanguageStaysOutOfTheDisplayItem() throws IOException {
        LayoutScanner.load(plugin, ShopLayout.class, null);

        String yaml = seededFile();
        int localeBlock = yaml.indexOf("Locale:\n      EN_US:");
        assertTrue(localeBlock > 0, "the language blocks were written:\n" + yaml);
        assertTrue(yaml.substring(0, localeBlock).contains("type:CHEST"),
                "the display item keeps what does not depend on language:\n" + yaml);
        assertTrue(yaml.contains("name:&a%product_name%"), "the name lives in the language block:\n" + yaml);
    }

    @Test
    void aKeyTheFileKeepsAndTheClassDroppedIsQuarantinedInsteadOfDeleted() throws IOException {
        LayoutScanner.load(plugin, ShopLayout.class, null);
        rewrite(seededPath(), "  UPGRADE:", "  OLD_UPGRADE:");

        LayoutScanner.load(plugin, ShopLayout.class, null);

        String yaml = seededFile();
        assertTrue(yaml.contains("_Quarentena:"), "the orphan key was moved, not dropped:\n" + yaml);
        assertTrue(yaml.contains("OLD_UPGRADE:"), "the admin's own work is still there to copy:\n" + yaml);
    }

    @Test
    void aLanguageOverlayAnswersForTheKeysItDeclaresAndForNothingElse() throws IOException {
        LayoutScanner.load(plugin, ShopLayout.class, null);

        Path overlay = tempDir.resolve("guis/locale/EN_US/ShopLayout.yml");
        Files.createDirectories(overlay.getParent());
        Files.write(overlay, Arrays.asList(
                "Layout:",
                "  UPGRADE:",
                "    Slot: '[40]'"
        ), StandardCharsets.UTF_8);

        LayoutDiff localized = LayoutDiff.of(plugin, ShopLayout.class, LocaleType.EN_US);
        LayoutDiff base = LayoutDiff.of(plugin, ShopLayout.class, null);

        assertTrue(localized.hasOverlay(), "the overlay next to the base file was found");
        assertEquals(Arrays.asList("UPGRADE"), new ArrayList<>(localized.getOverriddenByOverlay()),
                "the overlay answers for exactly the key it declares");
        assertEquals("[40]", slotsOf(localized, "UPGRADE"), "the overlay's slot wins for that language");
        assertEquals("[22]", slotsOf(base, "UPGRADE"), "the base file is untouched by the overlay");
        assertEquals("[10,11,12]", slotsOf(localized, "PRODUCT"),
                "a key the overlay does not mention still comes from the base file");
    }

    @Test
    void anIconTheFileSwitchedOffIsReportedAsSilencedRatherThanMissing() throws IOException {
        LayoutScanner.load(plugin, ShopLayout.class, null);
        rewrite(seededPath(), "Slot: \"[22]\"", "Slot: \"[]\"");

        LayoutDiff diff = LayoutDiff.of(plugin, ShopLayout.class, null);

        assertEquals(Arrays.asList("UPGRADE"), namesOf(diff, LayoutDiff.Verdict.SILENCED),
                "an empty slot list is deliberate, and the operator has to be able to see it");
    }

    @Test
    void aVersionThatAddsOneIconAddsOneKeyToAFileThatAlreadyExists() throws IOException {
        Path file = tempDir.resolve("guis/BankLayout.yml");
        Files.createDirectories(file.getParent());
        Files.write(file, Arrays.asList(
                "Layout:",
                "  DEPOSIT:",
                "    Slot: \"[10]\"",
                "    DisplayItem:",
                "    - type:CHEST",
                "Background:",
                "  TOP:",
                "    Slot: \"[0,1]\"",
                "    DisplayItem:",
                "    - type:BLACK_STAINED_GLASS_PANE"
        ), StandardCharsets.UTF_8);

        LayoutScanner.load(plugin, BankLayout.class, null);

        String yaml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("WITHDRAW:"), "the icon the file never heard of is written:\n" + yaml);
        assertTrue(yaml.contains("BOTTOM:"), "a Background that already holds a sibling is not taken for "
                + "complete: seeding is per icon, never per section:\n" + yaml);
        assertTrue(yaml.contains("Slot: \"[45,46]\""), yaml);
    }

    @Test
    void anIconInheritedFromABaseLayoutIsSeededIntoTheChildsOwnFile() throws IOException {
        ChildScreen child = LayoutScanner.load(plugin, ChildScreen.class, null);

        String yaml = new String(Files.readAllBytes(tempDir.resolve("guis/ChildScreen.yml")),
                StandardCharsets.UTF_8);
        assertTrue(yaml.contains("CLOSE:") && yaml.contains("HELP:"),
                "the child's file carries what it inherited too:\n" + yaml);
        assertTrue(yaml.indexOf("CLOSE:") < yaml.indexOf("HELP:"),
                "and reads base class first, like the settings of the same file:\n" + yaml);
        assertNotNull(child.getIcon("CLOSE"));
        assertNotNull(child.getIcon("HELP"));
        assertFalse(Files.exists(tempDir.resolve("guis/BaseScreen.yml")),
                "a base nobody opened is not a screen, so it gets no file of its own");
    }

    @Test
    void aQuarantinedKeyIsNeitherMovedAgainNorDuplicatedByALaterLoad() throws IOException {
        LayoutScanner.load(plugin, ShopLayout.class, null);
        rewrite(seededPath(), "  UPGRADE:", "  OLD_UPGRADE:");
        LayoutScanner.load(plugin, ShopLayout.class, null);
        String afterTheMove = seededFile();

        LayoutScanner.load(plugin, ShopLayout.class, null);
        LayoutScanner.load(plugin, ShopLayout.class, null);

        String yaml = seededFile();
        assertEquals(1, occurrencesOf(yaml, "OLD_UPGRADE:"), "one copy of the admin's work, not three:\n" + yaml);
        assertEquals(afterTheMove, yaml, "a load with nothing left to move leaves the file untouched");
    }

    @Test
    void theDiffTellsTheFourFatesOfAKeyApartAndLeavesStackingAlone() throws IOException {
        LayoutScanner.load(plugin, ShopLayout.class, null);
        rewrite(seededPath(), "  UPGRADE:", "  OLD_UPGRADE:");
        rewrite(seededPath(), "Slot: \"[45]\"", "Slot: \"[]\"");
        rewrite(seededPath(), "Slot: \"[10,11,12]\"", "Slot: \"[0]\"");

        //the very entry point the /ecoregui command reads, so what an operator is shown is what is asserted
        LayoutDiff diff = LayoutDiff.of(plugin, ShopLayout.class, null);

        assertEquals(Arrays.asList("PRODUCT", "BACKDROP"), namesOf(diff, LayoutDiff.Verdict.MATCHED));
        assertEquals(Arrays.asList("UPGRADE"), namesOf(diff, LayoutDiff.Verdict.NEW),
                "the key the file no longer holds is waiting to be seeded, not missing");
        assertEquals(Arrays.asList("OLD_UPGRADE"), namesOf(diff, LayoutDiff.Verdict.ORPHAN));
        assertEquals(Arrays.asList("EDIT"), namesOf(diff, LayoutDiff.Verdict.SILENCED));
        assertEquals(Collections.emptyList(), diff.getWarnings(),
                "PRODUCT landed on a slot the BACKDROP pane covers, which is what a background is for");
    }

    @Test
    void twoIconsOfTheSameLayerContestASlotAndTheDiffNamesBoth() throws IOException {
        LayoutScanner.load(plugin, ShopLayout.class, null);
        rewrite(seededPath(), "Slot: \"[22]\"", "Slot: \"[10]\"");

        LayoutDiff diff = LayoutDiff.of(plugin, ShopLayout.class, null);

        assertEquals(Arrays.asList("PRODUCT and UPGRADE both claim slot 10. PRODUCT wins."),
                diff.getWarnings(), "a contested slot is invisible on screen and only the report says why");
    }

    private static int occurrencesOf(String text, String fragment) {
        int found = 0;
        for (int at = text.indexOf(fragment); at >= 0; at = text.indexOf(fragment, at + 1)) {
            found++;
        }
        return found;
    }

    private static String slotsOf(LayoutDiff diff, String key) {
        for (LayoutDiff.Entry entry : diff.getEntries()) {
            if (entry.getKey().equals(key) && entry.getSlots() != null) {
                return entry.getSlots().serialize();
            }
        }
        return "absent";
    }

    private static ArrayList<String> namesOf(LayoutDiff diff, LayoutDiff.Verdict verdict) {
        ArrayList<String> names = new ArrayList<>();
        for (LayoutDiff.Entry entry : diff.getEntries(verdict)) {
            names.add(entry.getKey());
        }
        return names;
    }

    private Path seededPath() {
        return tempDir.resolve("guis/ShopLayout.yml");
    }

    private String seededFile() throws IOException {
        return new String(Files.readAllBytes(seededPath()), StandardCharsets.UTF_8);
    }

    private static void rewrite(Path path, String from, String to) throws IOException {
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        Files.write(path, content.replace(from, to).getBytes(StandardCharsets.UTF_8));
    }

}
