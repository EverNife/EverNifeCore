package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a file the admin got wrong costs, and what it does not cost.
 *
 * <p>The rule the whole scanner is built around: a broken key costs the ONE icon it describes. The screen
 * still opens, every other icon is still there, and the log names the field, the file and the key - because
 * an icon that simply failed to appear is otherwise unexplainable from the outside.</p>
 */
class LayoutScannerTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;
    private ECPluginData plugin;

    @GuiLayout(title = "Vault", rows = 6)
    public static class VaultLayout extends LayoutBase {

        @IconData(slot = {10})
        public Icon KEY = Icon.of(new ItemStack(Material.CHEST));

        @IconData(slot = {12})
        public Icon GOLD = Icon.of(new ItemStack(Material.GOLD_INGOT));

        @IconData(slot = {0, 1}, background = true)
        public Icon FRAME = Icon.of(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
    }

    /** Two icons over one slot, named so that neither declaration order nor alphabet is a coincidence. */
    @GuiLayout(title = "Clash", rows = 3)
    public static class ClashLayout extends LayoutBase {

        @IconData(slot = {13})
        public Icon ZEBRA = Icon.of(new ItemStack(Material.PAPER));

        @IconData(slot = {13})
        public Icon ALPHA = Icon.of(new ItemStack(Material.BARRIER));
    }

    /** The states this screen's own enum names, so a key the admin mistypes has something to miss. */
    public enum Stock {
        DEFAULT, LOW, SOLD_OUT
    }

    /** An icon whose states are typed, plus a static field nothing can ever draw. */
    @GuiLayout(title = "Stall", rows = 3)
    public static class StallLayout extends LayoutBase {

        @IconData(slot = {0})
        public static Icon NEVER_DRAWN = Icon.of(new ItemStack(Material.BEDROCK));

        @IconData(slot = {4})
        public Icon PRODUCT = Icon.of(new ItemStack(Material.CHEST))
                .addState(Stock.LOW, new ItemStack(Material.PAPER))
                .addState(Stock.SOLD_OUT, new ItemStack(Material.BARRIER))
                .states(Stock.class, () -> Stock.DEFAULT);
    }

    /** A backdrop with a button on top of it - the arrangement almost every real screen has. */
    @GuiLayout(title = "Stacked", rows = 3)
    public static class StackedLayout extends LayoutBase {

        @IconData(slot = {0, 1, 2}, background = true)
        public Icon PANE = Icon.of(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));

        @IconData(slot = {1})
        public Icon BUTTON = Icon.of(new ItemStack(Material.BARRIER));
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

    // -----------------------------------------------------------------------------------------------------------------
    //  One broken key, one lost icon
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aMaterialThisServerDoesNotHaveCostsItsOwnIconAndSuggestsTheNameItAlmostIs() throws IOException {
        LayoutScanner.load(plugin, VaultLayout.class, null);
        rewrite("type:CHEST", "type:CHESTT");

        VaultLayout layout = LayoutScanner.load(plugin, VaultLayout.class, null);

        assertNull(layout.getIcon("KEY"), "the icon the file broke is the icon that is missing");
        assertNotNull(layout.getIcon("GOLD"), "and nothing else on the screen paid for it");
        assertNotNull(layout.getIcon("FRAME"));
        assertTrue(logged("VaultLayout.KEY"), logs());
        assertTrue(logged("Did you mean 'CHEST'?"), logs());
        assertTrue(logged("guis/VaultLayout.yml, key Layout.KEY"),
                "the log names the file and the key an admin has to open: " + logs());
    }

    @Test
    void aSlotOutsideTheScreenCostsItsOwnIconAndSaysHowBigTheScreenIs() throws IOException {
        LayoutScanner.load(plugin, VaultLayout.class, null);
        rewrite("Slot: \"[10]\"", "Slot: \"[60]\"");

        VaultLayout layout = LayoutScanner.load(plugin, VaultLayout.class, null);

        assertNull(layout.getIcon("KEY"));
        assertNotNull(layout.getIcon("GOLD"));
        assertTrue(logged("Slot 60 of '[60]' is outside a screen of 54 slots (0-53)."), logs());
        assertTrue(logged("VaultLayout.KEY"), logs());
    }

    @Test
    void anEmptySlotListUnderBackgroundSwitchesTheIconOffWithoutComplaining() throws IOException {
        LayoutScanner.load(plugin, VaultLayout.class, null);
        rewrite("Slot: \"[0,1]\"", "Slot: \"[]\"");

        VaultLayout layout = LayoutScanner.load(plugin, VaultLayout.class, null);

        assertNotNull(layout.getIcon("FRAME"), "the icon is still declared, it is simply nowhere");
        assertFalse(layout.getIcons().get("FRAME").isVisible());
        assertFalse(logged("VaultLayout.FRAME"),
                "switching an icon off is what the file's own header offers, not a mistake: " + logs());
        assertEquals(Arrays.asList("FRAME"),
                namesOf(LayoutDiff.of(plugin, VaultLayout.class, null), LayoutDiff.Verdict.SILENCED),
                "it is deliberate, and the operator has to be able to see it anyway");
    }

    @Test
    void anEmptyDisplayItemFallsBackToWhatThePluginDeclaredForADeclaredIcon() throws IOException {
        LayoutScanner.load(plugin, VaultLayout.class, null);
        write(Arrays.asList(
                "Layout:",
                "  GOLD:",
                "    Slot: \"[12]\"",
                "    DisplayItem: []"
        ));

        VaultLayout layout = LayoutScanner.load(plugin, VaultLayout.class, null);

        assertNotNull(layout.getIcon("GOLD"), "a field with no item in the file still has one in Java");
        assertEquals(Material.GOLD_INGOT, layout.getIcon("GOLD").getItemStack().getType());
        assertFalse(logged("VaultLayout.GOLD"), logs());
    }

    @Test
    void aFileOnlyIconWithNoItemAtAllIsRefusedNamingTheKey() throws IOException {
        LayoutScanner.load(plugin, VaultLayout.class, null);
        write(Arrays.asList(
                "Background:",
                "  PAINTED_BY_HAND:",
                "    Slot: \"[8]\"",
                "    DisplayItem: []"
        ));

        VaultLayout layout = LayoutScanner.load(plugin, VaultLayout.class, null);

        assertNull(layout.getIcon("PAINTED_BY_HAND"),
                "there is no Java default to fall back on: the key describes nothing at all");
        assertTrue(logged("VaultLayout.PAINTED_BY_HAND"), logs());
        assertTrue(logged("key Background.PAINTED_BY_HAND"), logs());
    }

    @Test
    void anUnreadableSlotListCostsItsOwnIconAndIsNeverReadAsAnEmptyOne() throws IOException {
        LayoutScanner.load(plugin, VaultLayout.class, null);
        rewrite("Slot: \"[10]\"", "Slot: \"[10,ten]\"");

        VaultLayout layout = LayoutScanner.load(plugin, VaultLayout.class, null);

        assertNull(layout.getIcon("KEY"), "text nothing can read is not a slot list, so there is no icon");
        assertNotNull(layout.getIcon("GOLD"));
        assertTrue(logged("VaultLayout.KEY"), logs());
        assertTrue(logged("key Layout.KEY"), "the log names the file and the key: " + logs());
    }

    @Test
    void anUnreadableSlotListIsReportedByTheDiffAsBrokenRatherThanAsSwitchedOff() throws IOException {
        LayoutScanner.load(plugin, VaultLayout.class, null);
        rewrite("Slot: \"[10]\"", "Slot: \"[10,ten]\"");

        LayoutDiff diff = LayoutDiff.of(plugin, VaultLayout.class, null);

        assertEquals(Arrays.asList(), namesOf(diff, LayoutDiff.Verdict.SILENCED),
                "an empty list is deliberate; a broken one is not, and reading them the same way hides it");
        assertEquals(1, diff.getWarnings().size(), diff.getWarnings().toString());
        assertTrue(diff.getWarnings().get(0).contains("KEY has an unreadable slot list"),
                diff.getWarnings().toString());
        assertTrue(diff.getWarnings().get(0).contains("guis/VaultLayout.yml"),
                "and it names the file to open: " + diff.getWarnings());
    }

    @Test
    void aRowCountTheWindowCannotHaveCostsOneWarningAndTheScreenStillOpens() throws IOException {
        LayoutScanner.load(plugin, VaultLayout.class, null);
        rewrite("rows: 6", "rows: 9");

        VaultLayout layout = LayoutScanner.load(plugin, VaultLayout.class, null);

        assertEquals(6, layout.getRows(), "the row count the plugin declared is what the screen opens with");
        assertNotNull(layout.getIcon("KEY"), "and no icon is blamed for a setting none of them wrote");
        assertEquals(1, occurrencesOf("Settings.rows"), "one line about it, not one per icon: " + logs());
        assertTrue(logged("A CHEST gui has 1 to 6 rows, got [9]"), logs());
    }

    @Test
    void aStateKeyNoConstantAnswersToIsReportedOnceTheFileHasBeenRead() throws IOException {
        LayoutScanner.load(plugin, StallLayout.class, null);
        Files.write(tempDir.resolve("guis/StallLayout.yml"),
                new String(Files.readAllBytes(tempDir.resolve("guis/StallLayout.yml")), StandardCharsets.UTF_8)
                        .replace("soldOut:", "soldout:").getBytes(StandardCharsets.UTF_8));

        LayoutScanner.load(plugin, StallLayout.class, null);

        assertTrue(logged("The state 'soldout' of StallLayout.PRODUCT"),
                "the typo is the admin's, and only a load that has read the file can see it: " + logs());
        assertTrue(logged("matches no constant of Stock"), logs());
    }

    @Test
    void aStaticIconFieldIsReportedInsteadOfBeingDroppedInSilence() {
        LayoutScanner.load(plugin, StallLayout.class, null);

        assertNull(LayoutScanner.load(plugin, StallLayout.class, null).getIcon("NEVER_DRAWN"),
                "the field is not part of any instance, so no file was ever written for it");
        assertTrue(logged("StallLayout.NEVER_DRAWN"), logs());
        assertTrue(logged("Make it an instance field"), "the message names the way out: " + logs());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  A slot two icons want
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aContestedSlotPicksTheSameWinnerOnEveryLoadAndTheLogNamesBothFields() {
        for (int load = 0; load < 10; load++) {
            LayoutScanner.load(plugin, ClashLayout.class, null);
        }

        Set<String> distinct = new LinkedHashSet<>();
        int reported = 0;
        for (String line : world.getPlatform().getLoggedMessages()) {
            if (line.contains("both claim slot 13")) {
                distinct.add(line);
                reported++;
            }
        }

        assertEquals(10, reported, "every load says it, because every load is a chance to fix it");
        assertEquals(1, distinct.size(),
                "a screen that swapped two icons after a restart would be unexplainable: " + distinct);
        assertEquals("ClashLayout: ALPHA and ZEBRA both claim slot 13. ALPHA wins. Move one of them, or give"
                        + " both the same group = \"...\" when they are meant to share the slot and the menu"
                        + " picks which one is alive.",
                distinct.iterator().next(), "the loser is invisible, and only the log says who it is");
    }

    @Test
    void aButtonOverABackdropStacksAndOnlyTheSameLayerIsReported() {
        LayoutScanner.load(plugin, StackedLayout.class, null);
        LayoutScanner.load(plugin, ClashLayout.class, null);

        List<String> disputes = new ArrayList<>();
        for (String line : world.getPlatform().getLoggedMessages()) {
            if (line.contains("both claim slot")) {
                disputes.add(line);
            }
        }

        assertEquals(Arrays.asList(
                        "ClashLayout: ALPHA and ZEBRA both claim slot 13. ALPHA wins. Move one of them, or give"
                                + " both the same group = \"...\" when they are meant to share the slot and the"
                                + " menu picks which one is alive."),
                disputes, "the pane under BUTTON is a floor, not a rival - only ClashLayout contests");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What the file may add on its own
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aBackgroundKeyWithNoJavaFieldBehindItRenders() throws IOException {
        LayoutScanner.load(plugin, VaultLayout.class, null);
        write(Arrays.asList(
                "Background:",
                "  EXTRA_PANE:",
                "    Slot: \"[7,8]\"",
                "    DisplayItem:",
                "    - type:GRAY_STAINED_GLASS_PANE"
        ));

        VaultLayout layout = LayoutScanner.load(plugin, VaultLayout.class, null);

        Icon extra = layout.getIcon("EXTRA_PANE");
        assertNotNull(extra, "decoration the file added on its own is a feature, not leftovers");
        assertTrue(extra.isBackground());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, extra.getItemStack().getType());
        assertEquals("[7,8]", layout.getIcons().get("EXTRA_PANE").getSlots().serialize());
        assertFalse(seededFile().contains(LayoutScanner.QUARANTINE),
                "a key with no field is only leftovers under Layout; under Background it is the point");
    }

    @Test
    void aPermissionTheAdminAddedIsAppliedToAnIconThatDeclaredNone() throws IOException {
        LayoutScanner.load(plugin, VaultLayout.class, null);
        assertTrue(LayoutScanner.load(plugin, VaultLayout.class, null).getIcon("GOLD").getPermission().isEmpty(),
                "the plugin declared no permission on this icon");

        rewrite("  GOLD:\n", "  GOLD:\n    Permission: vault.rich\n");

        VaultLayout layout = LayoutScanner.load(plugin, VaultLayout.class, null);

        assertEquals("vault.rich", layout.getIcon("GOLD").getPermission(),
                "who sees an icon is the admin's call, whether or not the plugin had an opinion");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------------------------------------------------

    private static List<String> namesOf(LayoutDiff diff, LayoutDiff.Verdict verdict) {
        List<String> names = new ArrayList<>();
        for (LayoutDiff.Entry entry : diff.getEntries(verdict)) {
            names.add(entry.getKey());
        }
        return names;
    }

    private boolean logged(String fragment) {
        return occurrencesOf(fragment) > 0;
    }

    private int occurrencesOf(String fragment) {
        int found = 0;
        for (String line : world.getPlatform().getLoggedMessages()) {
            if (line.contains(fragment)) {
                found++;
            }
        }
        return found;
    }

    private String logs() {
        return world.getPlatform().getLoggedMessages().toString();
    }

    private Path seededPath() {
        return tempDir.resolve("guis/VaultLayout.yml");
    }

    private String seededFile() throws IOException {
        return new String(Files.readAllBytes(seededPath()), StandardCharsets.UTF_8);
    }

    private void rewrite(String from, String to) throws IOException {
        Files.write(seededPath(), seededFile().replace(from, to).getBytes(StandardCharsets.UTF_8));
    }

    /** Replaces the seeded file with a partial one - what an admin who edits by hand ends up with. */
    private void write(List<String> lines) throws IOException {
        Files.write(seededPath(), lines, StandardCharsets.UTF_8);
    }

}
