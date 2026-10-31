package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.config.SettingsScanner;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private ECPluginData plugin;

    private static Icon icon(String materialId) {
        return Icon.of(ItemSpec.of(materialId, 1));
    }

    @ScreenLayout(title = "Vault", rows = 6)
    public static class VaultLayout extends Layout {

        @IconData(slot = {10})
        public Icon KEY = icon("minecraft:chest");

        @IconData(slot = {12})
        public Icon GOLD = icon("minecraft:gold_ingot");

        @IconData(slot = {0, 1}, background = true)
        public Icon FRAME = icon("minecraft:black_stained_glass_pane");
    }

    /** Two icons over one slot, named so that neither declaration order nor alphabet is a coincidence. */
    @ScreenLayout(title = "Clash", rows = 3)
    public static class ClashLayout extends Layout {

        @IconData(slot = {13})
        public Icon ZEBRA = icon("minecraft:paper");

        @IconData(slot = {13})
        public Icon ALPHA = icon("minecraft:barrier");
    }

    /** The states this screen's own enum names, so a key the admin mistypes has something to miss. */
    public enum Stock {
        DEFAULT, LOW, SOLD_OUT
    }

    /** An icon whose states are typed, plus a static field nothing can ever draw. */
    @ScreenLayout(title = "Stall", rows = 3)
    public static class StallLayout extends Layout {

        @IconData(slot = {0})
        public static Icon NEVER_DRAWN = icon("minecraft:bedrock");

        @IconData(slot = {4})
        public Icon PRODUCT = icon("minecraft:chest")
                .addState(Stock.LOW, ItemSpec.of("minecraft:paper", 1))
                .addState(Stock.SOLD_OUT, ItemSpec.of("minecraft:barrier", 1))
                .states(Stock.class, () -> Stock.DEFAULT);
    }

    /** A backdrop with a button on top of it - the arrangement almost every real screen has. */
    @ScreenLayout(title = "Stacked", rows = 3)
    public static class StackedLayout extends Layout {

        @IconData(slot = {0, 1, 2}, background = true)
        public Icon PANE = icon("minecraft:black_stained_glass_pane");

        @IconData(slot = {1})
        public Icon BUTTON = icon("minecraft:barrier");
    }

    @BeforeEach
    void setup() {
        //a settings warning is emitted once per process, so a suite that counts one starts from clean
        SettingsScanner.forgetWarnings();
        world = ScreenWorld.open(tempDir);
        plugin = world.getOwner();
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
    void anUnknownMaterialIdIsKeptAndTheIconStillLoads() throws IOException {
        LayoutScanner.load(plugin, VaultLayout.class, null);
        rewrite("material:minecraft:chest", "material:minecraft:chestt");

        VaultLayout layout = LayoutScanner.load(plugin, VaultLayout.class, null);

        //only a renderer knows its platform's registry, so the load neither refuses nor corrects an id
        assertNotNull(layout.getIcon("KEY"), "an id the layout cannot judge is not a broken key");
        assertEquals("minecraft:chestt", layout.getIcon("KEY").getItem().getMaterialId(),
                "the id is carried as written, for the renderer to resolve or fall back from");
        assertNotNull(layout.getIcon("GOLD"));
        assertFalse(logged("VaultLayout.KEY"), logs());
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
        assertTrue(logged("guis/VaultLayout.yml, key Layout.KEY"),
                "the log names the file and the key an admin has to open: " + logs());
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
        assertEquals("minecraft:gold_ingot", layout.getIcon("GOLD").getItem().getMaterialId());
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
        assertTrue(logged("A screen has 1 to 6 rows of nine, not [9]."), logs());
    }

    @Test
    void aStateKeyNoConstantAnswersToIsReportedOnceTheFileHasBeenRead() throws IOException {
        LayoutScanner.load(plugin, StallLayout.class, null);
        Path file = tempDir.resolve("guis/StallLayout.yml");
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(content.contains("soldOut:"), "the state was seeded under its camelCase key:\n" + content);
        Files.write(file, content.replace("soldOut:", "soldout:").getBytes(StandardCharsets.UTF_8));

        //the state check reports through the core's own log, not the plugin's, so it is captured at the source
        List<String> logged = Logs.capture(() -> LayoutScanner.load(plugin, StallLayout.class, null));

        assertTrue(anyContains(logged, "The state 'soldout' of StallLayout.PRODUCT"),
                "the typo is the admin's, and only a load that has read the file can see it: " + logged);
        assertTrue(anyContains(logged, "matches no constant of Stock"), logged.toString());
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
                "    - material:minecraft:gray_stained_glass_pane"
        ));

        VaultLayout layout = LayoutScanner.load(plugin, VaultLayout.class, null);

        Icon extra = layout.getIcon("EXTRA_PANE");
        assertNotNull(extra, "decoration the file added on its own is a feature, not leftovers");
        assertTrue(extra.isBackground());
        assertEquals("minecraft:gray_stained_glass_pane", extra.getItem().getMaterialId());
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

    private static boolean anyContains(List<String> lines, String fragment) {
        for (String line : lines) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
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
        String content = seededFile();
        assertTrue(content.contains(from), "the file has to hold what the test edits:\n" + content);
        Files.write(seededPath(), content.replace(from, to).getBytes(StandardCharsets.UTF_8));
    }

    /** Replaces the seeded file with a partial one - what an admin who edits by hand ends up with. */
    private void write(List<String> lines) throws IOException {
        Files.write(seededPath(), lines, StandardCharsets.UTF_8);
    }

}
