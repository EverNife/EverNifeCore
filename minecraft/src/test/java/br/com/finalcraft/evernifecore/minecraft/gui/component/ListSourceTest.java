package br.com.finalcraft.evernifecore.minecraft.gui.component;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.cfg.ConfigSetting;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.GuiLayout;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.IconData;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Layouts;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a gui list's entries come from and what the screen owes them: the sequence the source answered
 * in, one write for one changed entry, and a source the admin wrote in the yml.
 *
 * <p>The rendered item of an entry is a stack of {@code PAPER} whose AMOUNT is the entry, so what a slot
 * holds names the entry that wrote it - the item runtime here answers no metadata, and amount is what a
 * stack can still be told apart by.</p>
 */
class ListSourceTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;

    /** What the admin writes one button of the menu as. */
    public static class UpgradeEntry {

        public Integer level = 0;
        public String label = "";
        /** A field a later version of the plugin added, which no file written before it can carry. */
        public String sound = "click";

        public UpgradeEntry() {
        }

        public UpgradeEntry(Integer level, String label) {
            this.level = level;
            this.label = label;
        }
    }

    /** A menu whose buttons are the entries of a list the file owns. */
    @GuiLayout(title = "Upgrades", rows = 3)
    public static class ConfiguredUpgradesLayout extends LayoutBase {

        @ConfigSetting(key = "Settings.upgrades",
                comment = @FCLocale(text = "One button per entry. Add or remove at will."))
        public List<UpgradeEntry> upgrades = Arrays.asList(
                new UpgradeEntry(1, "one"), new UpgradeEntry(2, "two"), new UpgradeEntry(3, "three"));

        @IconData(slot = {0, 1, 2})
        public Icon UPGRADE = Icon.of(new ItemStack(Material.PAPER));
    }

    /** The same menu again, under its own key: the scanner reports a broken entry once per site, so a
     *  second scenario reading the same key would be reading the first one's warning. */
    @GuiLayout(title = "Upgrades", rows = 3)
    public static class IntactUpgradesLayout extends LayoutBase {

        @ConfigSetting(key = "Settings.intactUpgrades")
        public List<UpgradeEntry> upgrades = Arrays.asList(
                new UpgradeEntry(1, "one"), new UpgradeEntry(2, "two"), new UpgradeEntry(3, "three"));

        @IconData(slot = {0, 1, 2})
        public Icon UPGRADE = Icon.of(new ItemStack(Material.PAPER));
    }

    /** A third key, for the file that was written before the plugin grew a field. */
    @GuiLayout(title = "Upgrades", rows = 3)
    public static class GrownUpgradesLayout extends LayoutBase {

        @ConfigSetting(key = "Settings.grownUpgrades")
        public List<UpgradeEntry> upgrades = Arrays.asList(new UpgradeEntry(1, "one"));

        @IconData(slot = {0, 1, 2})
        public Icon UPGRADE = Icon.of(new ItemStack(Material.PAPER));
    }

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
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
    //  The order on screen is the order the source answered in
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A source whose entries come out of a {@link HashMap} keeps the sequence it handed over, and hands
     * over the same one on the next render.
     *
     * <p>The map is the point: its iteration order is neither the insertion order nor a sorted one, so a
     * screen that re-sorted its entries - or that poured them through a set of its own - would show a
     * different sequence, and the first assertion is what would catch it.</p>
     */
    @Test
    void aSourceWithNoOrderOfItsOwnIsPouredInTheSequenceItAnswered() {
        Map<String, Integer> byName = new HashMap<>();
        byName.put("iron", 1);
        byName.put("gold", 2);
        byName.put("diamond", 3);
        byName.put("netherite", 4);
        byName.put("copper", 5);
        List<Integer> sequence = new ArrayList<>(byName.values());
        assertNotEquals(Arrays.asList(1, 2, 3, 4, 5), sequence,
                "a source that happens to answer in order proves nothing about keeping one");

        AtomicInteger reads = new AtomicInteger();
        Gui<?> gui = Gui.of(3);
        gui.list(() -> {
                    reads.incrementAndGet();
                    return new ArrayList<>(byName.values());
                })
                .into(Slots.of(0, 1, 2, 3, 4))
                .render((entry, icon) -> icon.from(new ItemStack(Material.PAPER, entry)));

        GuiView view = world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        world.advanceTicks(1);

        assertEquals(sequence, amountsOf(surface, 5),
                "slot by slot, the screen reads the source out in the source's own sequence");

        int readsBefore = reads.get();
        view.refresh();
        world.advanceTicks(1);

        assertTrue(reads.get() > readsBefore, "the second render has to have asked the source again");
        assertEquals(sequence, amountsOf(surface, 5),
                "and it laid the same entries down in the same slots");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  One entry changed is one slot written
    // -----------------------------------------------------------------------------------------------------------------

    /** A domain object the menu shows and the plugin mutates in place. */
    private static final class Product {

        private int stock;

        private Product(int stock) {
            this.stock = stock;
        }
    }

    /**
     * A screen whose entries are mutable objects: changing one of them and asking for a render writes
     * that entry's slot and nothing else on the screen, background included.
     */
    @Test
    void changingOneEntryAndRenderingAgainWritesThatEntrysSlotAlone() {
        List<Product> catalogue = Arrays.asList(new Product(1), new Product(2), new Product(3));
        Gui<?> gui = Gui.of(3)
                .icon(Slots.all(), Icon.of(new ItemStack(Material.GRAY_STAINED_GLASS_PANE)).background());
        gui.list(catalogue)
                .into(Slots.of(0, 1, 2))
                .render((product, icon) -> icon.from(new ItemStack(Material.PAPER, product.stock)));

        GuiView view = world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        world.advanceTicks(1);
        surface.forgetWrites();

        catalogue.get(1).stock = 9;
        view.refresh();
        world.advanceTicks(1);

        assertEquals(Collections.singleton(1), surface.getWrittenSlots(),
                "one entry changed, so one slot is worth a write: " + surface.getWrites());
        assertEquals(1, surface.getWriteCount(), surface.getWrites().toString());
        assertEquals(9, surface.getItem(1).getAmount());
        assertEquals(1, surface.getItem(0).getAmount(), "the neighbours were not redrawn, and still read right");
        assertEquals(3, surface.getItem(2).getAmount());

        surface.forgetWrites();
        catalogue.get(2).stock = 7;
        view.refresh();
        world.advanceTicks(1);

        assertEquals(Collections.singleton(2), surface.getWrittenSlots(),
                "and the slot that answers for the changed entry is the changed entry's own: "
                        + surface.getWrites());
        assertEquals(7, surface.getItem(2).getAmount());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  A source the admin wrote
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * An entry the file got wrong costs that entry: the ones around it are still buttons on the screen,
     * and the log names the index of the one that was left out - a menu quietly missing a button is a menu
     * nobody knows is broken.
     */
    @Test
    void anEntryTheFileGotWrongCostsThatEntryAndTheIndexIsNamed() throws IOException {
        writeSource("ConfiguredUpgradesLayout", "upgrades", Arrays.asList(
                "  - level: 1",
                "    label: 'one'",
                "  - level: 'not-a-number'",
                "    label: 'two'",
                "  - level: 3",
                "    label: 'three'"
        ));

        ConfiguredUpgradesLayout layout = Layouts.of(ConfiguredUpgradesLayout.class);

        assertEquals(2, layout.upgrades.size(), "the entries that do read are the entries the menu has");
        assertEquals(Arrays.asList(1, 3), levelsOf(layout.upgrades));
        assertTrue(logged("'Settings.upgrades'"),
                "the message has to name the key the admin will look for: " + logs());
        assertTrue(logged("Settings.upgrades[1]"),
                "a count alone leaves the operator hunting for which entry: " + logs());

        Gui<ConfiguredUpgradesLayout> gui = Gui.of(layout);
        gui.list(layout.upgrades)
                .into(l -> l.UPGRADE)
                .render((entry, icon) -> icon.from(new ItemStack(Material.PAPER, entry.level)));
        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();

        assertEquals(1, surface.getItem(0).getAmount());
        assertEquals(3, surface.getItem(1).getAmount(), "the entries around the broken one are still drawn");
        assertNull(surface.getItem(2), "and the button that was lost leaves its slot alone");
    }

    /** The same file with nothing wrong in it: three entries, three buttons, and nothing to report. */
    @Test
    void aFileWithNothingWrongInItBuildsOneButtonPerEntry() throws IOException {
        writeSource("IntactUpgradesLayout", "intactUpgrades", Arrays.asList(
                "  - level: 1",
                "    label: 'one'",
                "  - level: 2",
                "    label: 'two'",
                "  - level: 3",
                "    label: 'three'"
        ));

        IntactUpgradesLayout layout = Layouts.of(IntactUpgradesLayout.class);

        assertEquals(Arrays.asList(1, 2, 3), levelsOf(layout.upgrades));
        assertFalse(logged("Settings.intactUpgrades"), "nothing was lost, so nothing is reported: " + logs());

        Gui<IntactUpgradesLayout> gui = Gui.of(layout);
        gui.list(layout.upgrades)
                .into(l -> l.UPGRADE)
                .render((entry, icon) -> icon.from(new ItemStack(Material.PAPER, entry.level)));
        world.openDetached(gui, world.newPlayer("Steve"));

        assertEquals(Arrays.asList(1, 2, 3), amountsOf(world.getSurface(), 3));
    }

    /**
     * A file written before the plugin grew a field: the entry that says nothing about it gets the
     * plugin's default, and the entry that does say something is still the one that decides. Both halves
     * matter - a field that only ever answered its default would pass the first assertion while ignoring
     * the file entirely.
     */
    @Test
    void aFieldTheFileNeverHeardOfIsFilledInPerEntry() throws IOException {
        writeSource("GrownUpgradesLayout", "grownUpgrades", Arrays.asList(
                "  - level: 4",
                "    label: 'four'",
                "  - level: 5",
                "    label: 'five'",
                "    sound: 'boom'"
        ));

        GrownUpgradesLayout layout = Layouts.of(GrownUpgradesLayout.class);

        assertEquals(Arrays.asList(4, 5), levelsOf(layout.upgrades),
                "the entries the admin wrote are the entries in use");
        assertEquals(Arrays.asList("four", "five"), labelsOf(layout.upgrades));
        assertEquals(Arrays.asList("click", "boom"), soundsOf(layout.upgrades),
                "a field the entry never mentions is filled in from the plugin's own default, entry by "
                        + "entry, and one the entry does mention is read from the file like any other");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------------------------------------------------

    /** Writes the list section of a layout file the way an admin would, before anything has read it. */
    private void writeSource(String layoutName, String key, List<String> entries) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("Settings:");
        lines.add("  " + key + ":");
        lines.addAll(entries);
        Path file = tempDir.resolve("guis/" + layoutName + ".yml");
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static List<Integer> amountsOf(SurfaceDouble surface, int slots) {
        List<Integer> amounts = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            ItemStack drawn = surface.getItem(slot);
            amounts.add(drawn == null ? null : drawn.getAmount());
        }
        return amounts;
    }

    private static List<Integer> levelsOf(List<UpgradeEntry> entries) {
        List<Integer> levels = new ArrayList<>(entries.size());
        for (UpgradeEntry entry : entries) {
            levels.add(entry.level);
        }
        return levels;
    }

    private static List<String> labelsOf(List<UpgradeEntry> entries) {
        List<String> labels = new ArrayList<>(entries.size());
        for (UpgradeEntry entry : entries) {
            labels.add(entry.label);
        }
        return labels;
    }

    private static List<String> soundsOf(List<UpgradeEntry> entries) {
        List<String> sounds = new ArrayList<>(entries.size());
        for (UpgradeEntry entry : entries) {
            sounds.add(entry.sound);
        }
        return sounds;
    }

    private boolean logged(String fragment) {
        for (String line : world.getPlatform().getLoggedMessages()) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private String logs() {
        return world.getPlatform().getLoggedMessages().toString();
    }

}
