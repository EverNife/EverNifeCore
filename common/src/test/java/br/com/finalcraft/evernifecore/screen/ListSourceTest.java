package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.config.ConfigSetting;
import br.com.finalcraft.evernifecore.config.SettingsScanner;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a screen list's entries come from and what the screen owes them: the sequence the source answered
 * in, one write for one changed entry, and a source the admin wrote in the yml.
 *
 * <p>The rendered item of an entry is a stack of {@code paper} whose AMOUNT is the entry, so what a slot
 * holds names the entry that wrote it.</p>
 */
class ListSourceTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender steve;

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
    @ScreenLayout(title = "Upgrades", rows = 3)
    public static class ConfiguredUpgradesLayout extends Layout {

        @ConfigSetting(key = "Settings.upgrades",
                comment = @FCLocale(text = "One button per entry. Add or remove at will."))
        public List<UpgradeEntry> upgrades = Arrays.asList(
                new UpgradeEntry(1, "one"), new UpgradeEntry(2, "two"), new UpgradeEntry(3, "three"));

        @IconData(slot = {0, 1, 2})
        public Icon UPGRADE = Icon.of(ItemSpec.of("minecraft:paper", 1));
    }

    /** The same menu again, under its own key: the scanner reports a broken entry once per site, so a
     *  second scenario reading the same key would be reading the first one's warning. */
    @ScreenLayout(title = "Upgrades", rows = 3)
    public static class IntactUpgradesLayout extends Layout {

        @ConfigSetting(key = "Settings.intactUpgrades")
        public List<UpgradeEntry> upgrades = Arrays.asList(
                new UpgradeEntry(1, "one"), new UpgradeEntry(2, "two"), new UpgradeEntry(3, "three"));

        @IconData(slot = {0, 1, 2})
        public Icon UPGRADE = Icon.of(ItemSpec.of("minecraft:paper", 1));
    }

    /** A third key, for the file that was written before the plugin grew a field. */
    @ScreenLayout(title = "Upgrades", rows = 3)
    public static class GrownUpgradesLayout extends Layout {

        @ConfigSetting(key = "Settings.grownUpgrades")
        public List<UpgradeEntry> upgrades = Arrays.asList(new UpgradeEntry(1, "one"));

        @IconData(slot = {0, 1, 2})
        public Icon UPGRADE = Icon.of(ItemSpec.of("minecraft:paper", 1));
    }

    @BeforeEach
    void setup() {
        //a settings warning is emitted once per process, so a suite that reads one starts from clean
        SettingsScanner.forgetWarnings();
        world = ScreenWorld.open(tempDir);
        steve = world.viewer("Steve");
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        if (world != null) {
            world.close();
        }
    }

    private static ItemSpec paper(int amount) {
        return ItemSpec.of("minecraft:paper", amount);
    }

    private void close(ScreenView view) {
        view.close();
        world.advanceTicks(2);
    }

    /** A countdown read inside the render: only a clock makes the list notice that time went by. */
    @Test
    void aListOnAClockDrawsAgainOnEveryPeriod() {
        AtomicInteger seconds = new AtomicInteger(1);
        Screen<Layout> screen = Screen.of(3);
        screen.list(Collections.singletonList(0))
                .into(Slots.of(0))
                .render((entry, icon) -> icon.from(paper(seconds.get())))
                .every(20);

        ScreenView view = world.open(screen, steve);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        world.advanceTicks(1);
        assertEquals(Collections.singletonList(1), amountsOf(surface, 1));

        seconds.set(2);
        world.advanceTicks(21);

        assertEquals(Collections.singletonList(2), amountsOf(surface, 1),
                "the period redrew the entry with what the clock reads now");
        close(view);
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
        Screen<Layout> screen = Screen.of(3);
        screen.list(() -> {
                    reads.incrementAndGet();
                    return new ArrayList<>(byName.values());
                })
                .into(Slots.of(0, 1, 2, 3, 4))
                .render((entry, icon) -> icon.from(paper(entry)));

        ScreenView view = world.open(screen, steve);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        world.advanceTicks(1);

        assertEquals(sequence, amountsOf(surface, 5),
                "slot by slot, the screen reads the source out in the source's own sequence");

        int readsBefore = reads.get();
        view.refresh();
        world.advanceTicks(1);

        assertTrue(reads.get() > readsBefore, "the second render has to have asked the source again");
        assertEquals(sequence, amountsOf(surface, 5),
                "and it laid the same entries down in the same slots");
        close(view);
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
        Screen<Layout> screen = Screen.of(3);
        screen.icon(Slots.all(), Icon.of(ItemSpec.of("minecraft:gray_stained_glass_pane", 1)).background());
        screen.list(catalogue)
                .into(Slots.of(0, 1, 2))
                .render((product, icon) -> icon.from(paper(product.stock)));

        ScreenView view = world.open(screen, steve);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);
        world.advanceTicks(1);
        surface.forgetApplies();

        catalogue.get(1).stock = 9;
        view.refresh();
        world.advanceTicks(1);

        assertEquals(Collections.singleton(1), writtenSlots(surface),
                "one entry changed, so one slot is worth a write: " + surface.getApplies());
        assertEquals(1, writeCount(surface), surface.getApplies().toString());
        assertEquals(Arrays.asList(1, 9, 3), amountsOf(surface, 3),
                "the neighbours were not redrawn, and still read right");

        surface.forgetApplies();
        catalogue.get(2).stock = 7;
        view.refresh();
        world.advanceTicks(1);

        assertEquals(Collections.singleton(2), writtenSlots(surface),
                "and the slot that answers for the changed entry is the changed entry's own: "
                        + surface.getApplies());
        assertEquals(7, surface.getSlot(2).getItem().getAmount());
        close(view);
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

        Screen<ConfiguredUpgradesLayout> screen = Screen.of(layout);
        screen.list(layout.upgrades)
                .into(l -> l.UPGRADE)
                .render((entry, icon) -> icon.from(paper(entry.level)));
        ScreenView view = world.open(screen, steve);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);

        assertEquals(1, surface.getSlot(0).getItem().getAmount());
        assertEquals(3, surface.getSlot(1).getItem().getAmount(), "the entries around the broken one are still drawn");
        assertNull(surface.getSlot(2), "and the button that was lost leaves its slot alone");
        close(view);
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

        Screen<IntactUpgradesLayout> screen = Screen.of(layout);
        screen.list(layout.upgrades)
                .into(l -> l.UPGRADE)
                .render((entry, icon) -> icon.from(paper(entry.level)));
        ScreenView view = world.open(screen, steve);

        assertEquals(Arrays.asList(1, 2, 3), amountsOf(world.getRenderer().surfaceOf(view), 3));
        close(view);
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

    private static List<Integer> amountsOf(RecordingSurface surface, int slots) {
        List<Integer> amounts = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            RenderedIcon drawn = surface.getSlot(slot);
            amounts.add(drawn == null || drawn.isEmpty() ? null : drawn.getItem().getAmount());
        }
        return amounts;
    }

    /** Every slot an apply since the last {@code forgetApplies} wrote. */
    private static Set<Integer> writtenSlots(RecordingSurface surface) {
        Set<Integer> slots = new LinkedHashSet<>();
        for (List<ElementPatch> apply : surface.getApplies()) {
            for (ElementPatch patch : apply) {
                slots.add(patch.getSlot());
            }
        }
        return slots;
    }

    private static int writeCount(RecordingSurface surface) {
        int writes = 0;
        for (List<ElementPatch> apply : surface.getApplies()) {
            writes += apply.size();
        }
        return writes;
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
