package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.cfg.ConfigSetting;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McConfigTypes;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static boolean typesRegistered = false;

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

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        plugin = ECPluginManager.getOrCreateECorePluginData(new Object());
        if (!typesRegistered) {
            //the registry is process-wide; a second registration would be the bootstrap running twice
            McConfigTypes.register();
            typesRegistered = true;
        }
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
