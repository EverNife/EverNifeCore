package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.GuiLayout;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.IconData;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutDiff;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutScanner;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Layouts;
import br.com.finalcraft.evernifecore.minecraft.gui.state.MutableState;
import br.com.finalcraft.evernifecore.minecraft.gui.state.State;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
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
 * the container itself - and the one that mattered was neither of the first two. Here they are asserted
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

    private GuiTestWorld world;
    private ECPluginData plugin;
    private PlayerDouble player;

    private final MutableState<Mode> mode = State.of(Mode.SELLING);
    private final MutableState<Boolean> ownItem = State.of(false);

    private enum Mode {SELLING, BIDDING}

    private enum Stock {DEFAULT, EMPTY}

    /** Seven icons over three slots: the shape of a real 2.x layout, in the vocabulary it ports to. */
    @GuiLayout(title = "TheMarket", rows = 6)
    public static class MarketLayout extends LayoutBase {

        @IconData(slot = {52}, group = "mode")
        public Icon SELECTED_SELLING = Icon.of(new ItemStack(Material.SLIME_BALL));

        @IconData(slot = {52}, group = "mode")
        public Icon SELECTED_BIDDING = Icon.of(new ItemStack(Material.MAGMA_CREAM));

        @IconData(slot = {53}, group = "helpSlot")
        public Icon HELP_BOOK_SELLING = Icon.of(new ItemStack(Material.BOOK));

        @IconData(slot = {53}, group = "helpSlot")
        public Icon HELP_BOOK_BIDDING = Icon.of(new ItemStack(Material.PAPER));

        @IconData(slot = {53}, group = "helpSlot")
        public Icon CANT_BUY_SELF_ITEM = Icon.of(new ItemStack(Material.BARRIER));

        @IconData(slot = {49})
        public Icon CATEGORIES = Icon.of(new ItemStack(Material.CHEST))
                .addState(Stock.EMPTY, new ItemStack(Material.HOPPER));

        @IconData(slot = {})
        public Icon ITEM_DISPLAY = Icon.of(new ItemStack(Material.STONE));
    }

    /** The same group, plus one icon that lands on its slot by accident and has to be told. */
    @GuiLayout(title = "Intruder", rows = 6)
    public static class IntruderLayout extends LayoutBase {

        @IconData(slot = {52}, group = "mode")
        public Icon SELECTED_SELLING = Icon.of(new ItemStack(Material.SLIME_BALL));

        @IconData(slot = {52}, group = "mode")
        public Icon SELECTED_BIDDING = Icon.of(new ItemStack(Material.MAGMA_CREAM));

        @IconData(slot = {52})
        public Icon ZOMBIE_NOTES = Icon.of(new ItemStack(Material.PAPER));
    }

    /** Declaration order says BETA and ZULU; the declared order says ALPHA and ZULU. */
    @GuiLayout(title = "Disagree", rows = 3)
    public static class DisagreeLayout extends LayoutBase {

        @IconData(slot = {13}, order = 5)
        public Icon BETA = Icon.of(new ItemStack(Material.PAPER));

        @IconData(slot = {13}, order = 1)
        public Icon ALPHA = Icon.of(new ItemStack(Material.BARRIER));

        @IconData(slot = {14}, order = 1)
        public Icon ZULU = Icon.of(new ItemStack(Material.GOLD_INGOT));

        @IconData(slot = {14}, order = 5)
        public Icon AARDVARK = Icon.of(new ItemStack(Material.ANVIL));
    }

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        plugin = ECPluginManager.getOrCreateECorePluginData(new Object());
        player = world.newPlayer("Steve");
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
    private Gui<MarketLayout> market() {
        Gui<MarketLayout> gui = Gui.of(MarketLayout.class).debounce(0);

        gui.icon(l -> l.SELECTED_SELLING)
                .visibleWhen(() -> mode.get() == Mode.SELLING)
                .onClick(context -> {
                    mode.set(Mode.BIDDING);
                    context.refresh();
                });
        gui.icon(l -> l.SELECTED_BIDDING)
                .visibleWhen(() -> mode.get() == Mode.BIDDING)
                .onClick(context -> {
                    mode.set(Mode.SELLING);
                    context.refresh();
                });

        gui.icon(l -> l.HELP_BOOK_SELLING).visibleWhen(() -> mode.get() == Mode.SELLING && !ownItem.get());
        gui.icon(l -> l.HELP_BOOK_BIDDING).visibleWhen(() -> mode.get() == Mode.BIDDING && !ownItem.get());
        gui.icon(l -> l.CANT_BUY_SELF_ITEM).visibleWhen(ownItem::get);

        gui.icon(l -> l.CATEGORIES).states(Stock.class,
                () -> mode.get() == Mode.SELLING ? Stock.DEFAULT : Stock.EMPTY);
        return gui;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What the player sees, and what the player clicks
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aSharedSlotShowsOneIconAndHandsTheClickToThatSameIcon() {
        GuiView view = world.openDetachedAndRegistered(market(), player);
        SurfaceDouble surface = world.getSurface();

        assertEquals(Material.SLIME_BALL, surface.getItem(52).getType(), "the button of the mode it is in");
        assertEquals(Material.BOOK, surface.getItem(53).getType(), "one help out of the three declared there");
        assertEquals(Material.CHEST, surface.getItem(49).getType(), "and the state slot on its default face");
        assertEquals(Material.SLIME_BALL, view.getIconAt(52).getItemStack().getType(),
                "appearance and click are read out of the same array - this is what proves they agree");

        world.getClicks().leftClick(player, 52);

        assertEquals(Mode.BIDDING, mode.get(), "the handler that ran belongs to the icon on screen: the "
                + "hidden one would have set SELLING and nothing would have moved");
    }

    @Test
    void changingTheModeMovesBothSharedSlotsAndTheStateOneWithThem() {
        GuiView view = world.openDetachedAndRegistered(market(), player);
        SurfaceDouble surface = world.getSurface();

        world.getClicks().leftClick(player, 52);
        world.advanceTicks(2); //the pass the click asked for, and the poll that would have caught it anyway

        assertEquals(Material.MAGMA_CREAM, surface.getItem(52).getType(),
                "hiding an icon takes it off the slot; the mode that ended is not left behind");
        assertEquals(Material.PAPER, surface.getItem(53).getType(), "the second shared slot turned too");
        assertEquals(Material.HOPPER, surface.getItem(49).getType(),
                "and so did the slot that changes face instead of changing icon");
        assertEquals(Material.MAGMA_CREAM, view.getIconAt(52).getItemStack().getType(),
                "the click follows what is painted, or the handler of the mode that ended is still armed");

        world.getClicks().leftClick(player, 52);
        world.advanceTicks(2);

        assertEquals(Mode.SELLING, mode.get(), "and the icon now on screen switches back");
        assertEquals(Material.SLIME_BALL, surface.getItem(52).getType());
    }

    @Test
    void theMemberOfAGroupThatIsAliveTakesTheSlotFromTheOtherTwo() {
        GuiView view = world.openDetachedAndRegistered(market(), player);
        SurfaceDouble surface = world.getSurface();

        ownItem.set(true);
        view.refresh();
        world.advanceTicks(1);

        assertEquals(Material.BARRIER, surface.getItem(53).getType(),
                "three icons on one slot, and the one the logic says is alive is the one showing");
        assertEquals(Material.BARRIER, view.getIconAt(53).getItemStack().getType());
        assertEquals(Material.SLIME_BALL, surface.getItem(52).getType(), "the other group did not move");
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

        GuiView view = world.openDetached(Gui.of(DisagreeLayout.class), player);
        SurfaceDouble surface = world.getSurface();

        assertEquals(Material.BARRIER, surface.getItem(13).getType(),
                "declaration order would have painted BETA last and left it showing");
        assertEquals(Material.BARRIER, view.getIconAt(13).getItemStack().getType());
        assertEquals(Material.GOLD_INGOT, surface.getItem(14).getType(),
                "and a lower order wins even against the alphabet");
        assertEquals(Material.GOLD_INGOT, view.getIconAt(14).getItemStack().getType());
    }

    @Test
    void theArithmeticRanksAnIconThatHasNoLayoutKeyLastWithoutBreakingDown() {
        Icon named = Icon.of(new ItemStack(Material.PAPER));
        named.setName("ALPHA");
        Icon loose = Icon.of(new ItemStack(Material.PAPER)); //bound straight to a slot: there is no key

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
