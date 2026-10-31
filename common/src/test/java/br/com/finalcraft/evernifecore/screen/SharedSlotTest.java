package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Several icons over one slot, which is what every 2.x catalogue layout was made of: the file says
 * where they are, and the running code says which one of them is on screen right now.
 *
 * <p>Three answers used to disagree about a contested slot - the log at load, the operator's diff and
 * the window itself - and the one that mattered was neither of the first two. Here they are asserted
 * together, on a layout where declaration order and the declared order deliberately point at different
 * icons, because a report that names the other winner is worse than no report.</p>
 *
 * <p>The click is asserted next to the appearance on purpose: both come out of the same per-layer array,
 * so a screen that paints the right icon and hands the click to the one underneath is a screen that
 * looks correct in a screenshot.</p>
 */
class SharedSlotTest {

    /** What the scanner appends to a dispute, once, so the fix is in the message and not in the source. */
    private static final String ADVICE = " Move one of them, or give both the same group = \"...\" when they"
            + " are meant to share the slot and the menu picks which one is alive.";

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private ECPluginData plugin;
    private TestFPlayerSender player;

    private final MutableState<Mode> mode = State.of(Mode.SELLING);
    private final MutableState<Boolean> ownItem = State.of(false);

    private enum Mode {SELLING, BIDDING}

    private enum Stock {DEFAULT, EMPTY}

    private static ItemSpec item(String name) {
        return ItemSpec.of("minecraft:" + name, 1);
    }

    /** Seven icons over three slots: the shape of a real 2.x layout, in the vocabulary it ports to. */
    @ScreenLayout(title = "TheMarket", rows = 6)
    public static class MarketLayout extends Layout {

        @IconData(slot = {52}, group = "mode")
        public Icon SELECTED_SELLING = Icon.of(item("slime_ball"));

        @IconData(slot = {52}, group = "mode")
        public Icon SELECTED_BIDDING = Icon.of(item("magma_cream"));

        @IconData(slot = {53}, group = "helpSlot")
        public Icon HELP_BOOK_SELLING = Icon.of(item("book"));

        @IconData(slot = {53}, group = "helpSlot")
        public Icon HELP_BOOK_BIDDING = Icon.of(item("paper"));

        @IconData(slot = {53}, group = "helpSlot")
        public Icon CANT_BUY_SELF_ITEM = Icon.of(item("barrier"));

        @IconData(slot = {49})
        public Icon CATEGORIES = Icon.of(item("chest"))
                .addState(Stock.EMPTY, item("hopper"));

        @IconData(slot = {})
        public Icon ITEM_DISPLAY = Icon.of(item("stone"));
    }

    /** The same group, plus one icon that lands on its slot by accident and has to be told. */
    @ScreenLayout(title = "Intruder", rows = 6)
    public static class IntruderLayout extends Layout {

        @IconData(slot = {52}, group = "mode")
        public Icon SELECTED_SELLING = Icon.of(item("slime_ball"));

        @IconData(slot = {52}, group = "mode")
        public Icon SELECTED_BIDDING = Icon.of(item("magma_cream"));

        @IconData(slot = {52})
        public Icon ZOMBIE_NOTES = Icon.of(item("paper"));
    }

    /** Declaration order says BETA and ZULU; the declared order says ALPHA and ZULU. */
    @ScreenLayout(title = "Disagree", rows = 3)
    public static class DisagreeLayout extends Layout {

        @IconData(slot = {13}, order = 5)
        public Icon BETA = Icon.of(item("paper"));

        @IconData(slot = {13}, order = 1)
        public Icon ALPHA = Icon.of(item("barrier"));

        @IconData(slot = {14}, order = 1)
        public Icon ZULU = Icon.of(item("gold_ingot"));

        @IconData(slot = {14}, order = 5)
        public Icon AARDVARK = Icon.of(item("anvil"));
    }

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        plugin = world.getOwner();
        player = world.viewer("Steve");
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        if (world != null) {
            world.close();
        }
    }

    /**
     * The market screen: one slot resolved by a group of icons that each say when they are alive, one
     * resolved the other way - a single icon wearing the face of the mode it is in.
     */
    private Screen<MarketLayout> market() {
        Screen<MarketLayout> screen = Screen.of(MarketLayout.class);
        screen.debounce(0);

        screen.icon(l -> l.SELECTED_SELLING)
                .visibleWhen(() -> mode.get() == Mode.SELLING)
                .onClick(context -> {
                    mode.set(Mode.BIDDING);
                    context.refresh();
                });
        screen.icon(l -> l.SELECTED_BIDDING)
                .visibleWhen(() -> mode.get() == Mode.BIDDING)
                .onClick(context -> {
                    mode.set(Mode.SELLING);
                    context.refresh();
                });

        screen.icon(l -> l.HELP_BOOK_SELLING).visibleWhen(() -> mode.get() == Mode.SELLING && !ownItem.get());
        screen.icon(l -> l.HELP_BOOK_BIDDING).visibleWhen(() -> mode.get() == Mode.BIDDING && !ownItem.get());
        screen.icon(l -> l.CANT_BUY_SELF_ITEM).visibleWhen(ownItem::get);

        screen.icon(l -> l.CATEGORIES).states(Stock.class,
                () -> mode.get() == Mode.SELLING ? Stock.DEFAULT : Stock.EMPTY);
        return screen;
    }

    private static String materialAt(RecordingSurface surface, int slot) {
        RenderedIcon drawn = surface.getSlot(slot);
        return drawn == null ? null : drawn.getItem().getMaterialId();
    }

    /** What the view would hand a click on {@code slot} to - the top paint, read out of the same array the window was. */
    private static String clickableAt(ScreenView view, int slot) {
        Icon icon = view.getIconAt(slot);
        return icon == null ? null : icon.getItem().getMaterialId();
    }

    private void click(ScreenView view, int slot) {
        world.click(view, slot, ClickGesture.PRIMARY, ClickKind.NOTHING);
    }

    private void close(ScreenView view) {
        view.close();
        world.advanceTicks(2);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What the player sees, and what the player clicks
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aSharedSlotShowsOneIconAndHandsTheClickToThatSameIcon() {
        ScreenView view = world.open(market(), player);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);

        assertEquals("minecraft:slime_ball", materialAt(surface, 52), "the button of the mode it is in");
        assertEquals("minecraft:book", materialAt(surface, 53), "one help out of the three declared there");
        assertEquals("minecraft:chest", materialAt(surface, 49), "and the state slot on its default face");
        assertEquals("minecraft:slime_ball", clickableAt(view, 52),
                "appearance and click are read out of the same array - this is what proves they agree");

        click(view, 52);

        assertEquals(Mode.BIDDING, mode.get(), "the handler that ran belongs to the icon on screen: the "
                + "hidden one would have set SELLING and nothing would have moved");
        close(view);
    }

    @Test
    void changingTheModeMovesBothSharedSlotsAndTheStateOneWithThem() {
        ScreenView view = world.open(market(), player);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);

        click(view, 52);
        world.advanceTicks(2); //the pass the click asked for, and the poll that would have caught it anyway

        assertEquals("minecraft:magma_cream", materialAt(surface, 52),
                "hiding an icon takes it off the slot; the mode that ended is not left behind");
        assertEquals("minecraft:paper", materialAt(surface, 53), "the second shared slot turned too");
        assertEquals("minecraft:hopper", materialAt(surface, 49),
                "and so did the slot that changes face instead of changing icon");
        assertEquals("minecraft:magma_cream", clickableAt(view, 52),
                "the click follows what is painted, or the handler of the mode that ended is still armed");

        click(view, 52);
        world.advanceTicks(2);

        assertEquals(Mode.SELLING, mode.get(), "and the icon now on screen switches back");
        assertEquals("minecraft:slime_ball", materialAt(surface, 52));
        close(view);
    }

    @Test
    void theMemberOfAGroupThatIsAliveTakesTheSlotFromTheOtherTwo() {
        ScreenView view = world.open(market(), player);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);

        ownItem.set(true);
        view.refresh();
        world.advanceTicks(1);

        assertEquals("minecraft:barrier", materialAt(surface, 53),
                "three icons on one slot, and the one the logic says is alive is the one showing");
        assertEquals("minecraft:barrier", clickableAt(view, 53));
        assertEquals("minecraft:slime_ball", materialAt(surface, 52), "the other group did not move");
        close(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What the reports say about it - the negative alone would pass with the report switched off
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aGroupIsNotADisputeAndAnIconOutsideItStillIs() {
        LayoutScanner.load(plugin, MarketLayout.class, null);

        assertEquals(Collections.emptyList(), disputes(),
                "five icons over two slots, every one of them there on purpose: " + logs());
        assertEquals(Collections.emptyList(), LayoutDiff.of(plugin, MarketLayout.class, null).getWarnings());

        LayoutScanner.load(plugin, IntruderLayout.class, null);

        assertEquals(Arrays.asList("IntruderLayout: group [mode] and ZOMBIE_NOTES both claim slot 52. "
                        + "group [mode] wins." + ADVICE), disputes(),
                "the group is named as a whole - which member arrived first is not the operator's problem");
        assertEquals(Arrays.asList("group [mode] and ZOMBIE_NOTES both claim slot 52. group [mode] wins."),
                LayoutDiff.of(plugin, IntruderLayout.class, null).getWarnings(),
                "and the operator reads the same sentence the log said");
    }

    @Test
    void theDiffShowsTheGroupSoThreeIconsOnOneSlotDoNotReadAsADefect() {
        LayoutScanner.load(plugin, MarketLayout.class, null);

        LayoutDiff diff = LayoutDiff.of(plugin, MarketLayout.class, null);

        assertEquals("helpSlot", groupOf(diff, "CANT_BUY_SELF_ITEM"));
        assertEquals("mode", groupOf(diff, "SELECTED_BIDDING"));
        assertEquals("", groupOf(diff, "CATEGORIES"), "an icon nobody shares a slot with declares no group");
    }

    @Test
    void theLogTheDiffAndTheScreenNameTheSameWinner() {
        LayoutScanner.load(plugin, DisagreeLayout.class, null);

        assertEquals(Arrays.asList(
                        "DisagreeLayout: ZULU and AARDVARK both claim slot 14. ZULU wins." + ADVICE,
                        "DisagreeLayout: ALPHA and BETA both claim slot 13. ALPHA wins." + ADVICE),
                disputes(), "the log confronts the claims in the order the screen resolves them");
        assertEquals(Arrays.asList("ZULU and AARDVARK both claim slot 14. ZULU wins.",
                        "ALPHA and BETA both claim slot 13. ALPHA wins."),
                LayoutDiff.of(plugin, DisagreeLayout.class, null).getWarnings());

        ScreenView view = world.open(Screen.of(DisagreeLayout.class), player);
        RecordingSurface surface = world.getRenderer().surfaceOf(view);

        assertEquals("minecraft:barrier", materialAt(surface, 13),
                "declaration order would have painted BETA last and left it showing");
        assertEquals("minecraft:barrier", clickableAt(view, 13));
        assertEquals("minecraft:gold_ingot", materialAt(surface, 14),
                "and a lower order wins even against the alphabet");
        assertEquals("minecraft:gold_ingot", clickableAt(view, 14));
        close(view);
    }

    @Test
    void theArithmeticRanksAnIconThatHasNoLayoutKeyLastWithoutBreakingDown() {
        Icon named = Icon.of(item("paper"));
        named.setName("ALPHA");
        Icon loose = Icon.of(item("paper")); //bound straight to a slot: there is no key

        assertTrue(Icon.BY_SLOT_PRIORITY.compare(named, loose) < 0, "a claim with no name to compare loses");
        assertTrue(Icon.BY_SLOT_PRIORITY.compare(loose, named) > 0,
                "and it loses from either side - an asymmetric comparator takes the sort down with it");
        assertEquals(0, Icon.BY_SLOT_PRIORITY.compare(loose, loose.copy()), "two nameless claims tie");

        loose.setOrder(-1);

        assertTrue(Icon.BY_SLOT_PRIORITY.compare(loose, named) < 0, "the order is read before the name is");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------------------------------------------------

    private static String groupOf(LayoutDiff diff, String key) {
        for (LayoutDiff.Entry entry : diff.getEntries()) {
            if (entry.getKey().equals(key)) {
                return entry.getGroup();
            }
        }
        throw new AssertionError("The diff says nothing about " + key + ": " + diff.getEntries());
    }

    private List<String> disputes() {
        List<String> found = new ArrayList<>();
        for (String line : world.getPlatform().getLoggedMessages()) {
            if (line.contains("both claim slot")) {
                found.add(line);
            }
        }
        return found;
    }

    private String logs() {
        return world.getPlatform().getLoggedMessages().toString();
    }

}
