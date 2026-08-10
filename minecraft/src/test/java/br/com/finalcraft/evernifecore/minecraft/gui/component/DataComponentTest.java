package br.com.finalcraft.evernifecore.minecraft.gui.component;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.GuiLayout;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.IconData;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.IconStates;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Layouts;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.state.MutableState;
import br.com.finalcraft.evernifecore.minecraft.gui.state.State;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The primitives that replace the copied {@code for}, the {@code if} stealing a slot and the page
 * arithmetic every menu used to write again: a list poured into a region, the page that region implies,
 * and an icon that draws whichever state the plugin says it is in.
 */
class DataComponentTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;

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

    private enum Availability {DEFAULT, LOCKED, MAXED}

    @GuiLayout(title = "Upgrades", rows = 3)
    public static class UpgradesLayout extends LayoutBase {

        @IconData(slot = {0, 1, 2})
        public Icon OPTION = Icon.of(new ItemStack(Material.PAPER));

        @IconData(slot = {18})
        public Icon PREVIOUS = Icon.of(new ItemStack(Material.ARROW));

        @IconData(slot = {26})
        public Icon NEXT = Icon.of(new ItemStack(Material.ARROW));

        @IconData(slot = {4})
        public Icon TOGGLE = Icon.of(new ItemStack(Material.ANVIL))
                .addState(Availability.LOCKED, new ItemStack(Material.BARRIER));

        @IconData(slot = {8}, background = true)
        public Icon DECORATION = Icon.of(new ItemStack(Material.GLASS));
    }

    private static List<Integer> numbers(int count) {
        List<Integer> entries = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            entries.add(index);
        }
        return entries;
    }

    /** One paper per entry, its amount being the entry, so what a slot holds names the entry that wrote it. */
    private static Gui<?> listOf(List<Integer> entries, Pager pager) {
        Gui<?> gui = Gui.of(3);
        gui.list(entries)
                .pager(pager)
                .into(Slots.of(0, 1, 2))
                .pagedBy(Slots.of(18), Slots.of(26))
                .render((entry, icon) -> icon.from(new ItemStack(Material.PAPER, entry)));
        return gui;
    }

    private static boolean isEmpty(SurfaceDouble surface, int slot) {
        ItemStack item = surface.getItem(slot);
        return item == null || item.getType() == Material.AIR;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The region is the page
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theRegionDecidesHowManyEntriesAPageHolds() {
        Pager pager = new Pager();
        world.openDetached(listOf(numbers(7), pager), world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();

        assertEquals(3, pager.getPageSize(), "three slots of region means three entries a page");
        assertEquals(3, pager.getTotalPages(), "seven entries over three slots is three pages");
        assertEquals(1, surface.getItem(0).getAmount());
        assertEquals(2, surface.getItem(1).getAmount());
        assertEquals(3, surface.getItem(2).getAmount(), "the order on screen is the order of the source");
    }

    @Test
    void turningThePageShowsTheEntriesThatComeNext() {
        Pager pager = new Pager();
        world.openDetached(listOf(numbers(7), pager), world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        world.advanceTicks(1);
        surface.forgetWrites();

        pager.next();
        world.advanceTicks(1);

        assertEquals(2, pager.getPage());
        assertEquals(4, surface.getItem(0).getAmount());
        assertEquals(6, surface.getItem(2).getAmount());
    }

    @Test
    void aShortPageLeavesTheSlotsItDoesNotReachEmpty() {
        Pager pager = new Pager();
        world.openDetached(listOf(numbers(7), pager), world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();

        pager.last();
        world.advanceTicks(1);

        assertEquals(3, pager.getPage());
        assertEquals(7, surface.getItem(0).getAmount());
        assertTrue(isEmpty(surface, 1),
                "the last page holds one entry, so the other two slots show nothing: " + surface.getItem(1));
    }

    @Test
    void aListThatFitsOnOnePageDrawsNoPageButtons() {
        Pager pager = new Pager();
        world.openDetached(listOf(numbers(2), pager), world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();

        assertEquals(1, pager.getTotalPages());
        assertTrue(isEmpty(surface, 18),
                "an arrow with nowhere to go is a slot taken for nothing: " + surface.getItem(18));
        assertTrue(isEmpty(surface, 26));
    }

    @Test
    void clickingTheArrowTurnsThePage() {
        Pager pager = new Pager();
        PlayerDouble player = world.newPlayer("Steve");
        world.openDetachedAndRegistered(listOf(numbers(7), pager), player);
        SurfaceDouble surface = world.getSurface();
        assertNotNull(surface.getItem(26), "with three pages the forward arrow is on screen");

        world.getClicks().leftClick(player, 26);
        world.advanceTicks(1);

        assertEquals(2, pager.getPage());
        assertEquals(4, surface.getItem(0).getAmount());
    }

    @Test
    void aSourceThatAnsweredTheSameEntriesCostsNoWrite() {
        MutableState<String> filter = State.of("a");
        Gui<?> gui = Gui.of(3);
        gui.list(() -> numbers(3))
                .dependsOn(filter)
                .into(Slots.of(0, 1, 2))
                .render((entry, icon) -> icon.from(new ItemStack(Material.PAPER, entry)));

        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        world.advanceTicks(1);
        surface.forgetWrites();

        filter.set("b");
        world.advanceTicks(1);

        assertEquals(0, surface.getWriteCount(),
                "the list rendered again and drew the same picture: " + surface.getWrites());
    }

    @Test
    void anEntryWhoseRenderThrowsCostsThatEntryAlone() {
        Gui<?> gui = Gui.of(3);
        gui.list(numbers(3))
                .into(Slots.of(0, 1, 2))
                .render((entry, icon) -> {
                    if (entry == 2) {
                        throw new IllegalStateException("this entry is broken");
                    }
                    icon.from(new ItemStack(Material.PAPER, entry));
                });

        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();

        assertEquals(1, surface.getItem(0).getAmount());
        assertEquals(3, surface.getItem(2).getAmount(), "the entries around the broken one still draw");
        assertTrue(isEmpty(surface, 1));
    }

    @Test
    void aPageSourceIsAskedForOnePageAndCountedApart() {
        AtomicReference<int[]> asked = new AtomicReference<>();
        Pager pager = new Pager();
        Gui<?> gui = Gui.of(3);
        gui.list((page, size) -> {
                    asked.set(new int[]{page, size});
                    return Arrays.asList(page * 10, page * 10 + 1);
                })
                .total(() -> 40)
                .pager(pager)
                .into(Slots.of(0, 1, 2))
                .render((entry, icon) -> icon.from(new ItemStack(Material.PAPER, entry)));

        world.openDetached(gui, world.newPlayer("Steve"));

        assertEquals(1, asked.get()[0], "pages count from 1");
        assertEquals(3, asked.get()[1], "what is asked for is one region's worth");
        assertEquals(14, pager.getTotalPages(), "forty entries over three slots");
        assertEquals(10, world.getSurface().getItem(0).getAmount());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Named states, chosen by the menu
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anEnumConstantNamesItsOwnKeyInTheYml() {
        assertEquals("", IconStates.keyOf(Availability.DEFAULT), "DEFAULT is the icon's own appearance");
        assertEquals("locked", IconStates.keyOf(Availability.LOCKED));
        assertEquals("outOfStock", IconStates.keyOf("OUT_OF_STOCK"));
        assertEquals("priceAsc", IconStates.keyOf("PRICE_ASC"));
        assertEquals(Availability.LOCKED, IconStates.constantOf(Availability.class, "locked"));
    }

    @Test
    void anIconDrawsTheStateTheSupplierNames() {
        MutableState<Availability> availability = State.of(Availability.DEFAULT);
        Icon icon = Icon.of(new ItemStack(Material.ANVIL))
                .addState(Availability.LOCKED, new ItemStack(Material.BARRIER))
                .addState(Availability.MAXED, new ItemStack(Material.DIAMOND))
                .states(Availability.class, availability::get);

        world.openDetached(Gui.of(3).addComponent(component -> {
            component.remember(availability);
            component.render(writer -> writer.icon(13, icon));
        }), world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        assertEquals(Material.ANVIL, surface.getItem(13).getType());

        availability.set(Availability.LOCKED);
        world.advanceTicks(1);

        assertEquals(Material.BARRIER, surface.getItem(13).getType());
    }

    @Test
    void aConstantNoAppearanceWasDeclaredForFallsBackToTheIconItself() {
        Icon icon = Icon.of(new ItemStack(Material.ANVIL))
                .addState(Availability.LOCKED, new ItemStack(Material.BARRIER))
                .states(Availability.class, () -> Availability.MAXED);

        world.openDetached(Gui.of(3).icon(13, icon), world.newPlayer("Steve"));

        assertEquals(Material.ANVIL, world.getSurface().getItem(13).getType(),
                "a domain enum is allowed to carry more constants than the screen has looks");
    }

    @Test
    void aCycleWalksTheConstantsInDeclarationOrderAndWrapsAround() {
        AtomicReference<Availability> stored = new AtomicReference<>(Availability.DEFAULT);
        MutableState<Availability> bound = State.bound(stored::get, stored::set);
        List<Availability> seen = new ArrayList<>();

        Gui<?> gui = Gui.of(3).debounce(0);
        IconBinder binder = new IconBinder(gui, Slots.of(13), Icon.of(new ItemStack(Material.ANVIL))
                .addState(Availability.LOCKED, new ItemStack(Material.BARRIER))
                .addState(Availability.MAXED, new ItemStack(Material.DIAMOND)));
        binder.cycle(Availability.class, bound).onCycle(seen::add);
        gui.addComponent(binder::bind);

        PlayerDouble player = world.newPlayer("Steve");
        world.openDetachedAndRegistered(gui, player);
        SurfaceDouble surface = world.getSurface();

        world.getClicks().leftClick(player, 13);
        world.advanceTicks(1);
        assertEquals(Availability.LOCKED, stored.get(), "the cycle writes through the state it was given");
        assertEquals(Material.BARRIER, surface.getItem(13).getType());

        world.getClicks().leftClick(player, 13);
        world.getClicks().leftClick(player, 13);
        world.advanceTicks(1);

        assertEquals(Availability.DEFAULT, stored.get(), "past the last constant the walk starts over");
        assertEquals(Arrays.asList(Availability.LOCKED, Availability.MAXED, Availability.DEFAULT), seen);
        assertEquals(Material.ANVIL, surface.getItem(13).getType());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  State that outlives the screen
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aBoundStateReadsAndWritesThroughTheFunctionsItWasGiven() {
        AtomicReference<Availability> stored = new AtomicReference<>(Availability.LOCKED);
        MutableState<Availability> bound = State.bound(stored::get, stored::set);

        assertEquals(Availability.LOCKED, bound.get(), "the value is read from where it lives");

        Icon icon = Icon.of(new ItemStack(Material.ANVIL))
                .addState(Availability.LOCKED, new ItemStack(Material.BARRIER))
                .states(Availability.class, bound::get);
        world.openDetached(Gui.of(3).addComponent(component -> {
            component.remember(bound);
            component.render(writer -> writer.icon(13, icon));
        }), world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        assertEquals(Material.BARRIER, surface.getItem(13).getType());

        bound.set(Availability.DEFAULT);
        world.advanceTicks(1);

        assertEquals(Availability.DEFAULT, stored.get(), "the setter is what persists it");
        assertEquals(Material.ANVIL, surface.getItem(13).getType());
    }

    @Test
    void anUpdateOfABoundStateReadsThroughTheGetter() {
        AtomicReference<Integer> stored = new AtomicReference<>(4);
        MutableState<Integer> bound = State.bound(stored::get, stored::set);

        bound.update(value -> value + 1);

        assertEquals(5, stored.get(), "a bound state owns no value of its own to update from");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Addressing icons by the field the layout declared them under
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aScreenBuiltFromALayoutIsSizedAndDecoratedByIt() {
        UpgradesLayout layout = Layouts.of(UpgradesLayout.class);
        Gui<UpgradesLayout> gui = Gui.of(layout);

        assertEquals(3, gui.getRows());
        assertEquals("Upgrades", gui.getTitle());

        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();

        assertEquals(Material.GLASS, surface.getItem(8).getType(),
                "an icon nothing bound is still painted - that is what a layout is for");
        assertEquals(Material.PAPER, surface.getItem(0).getType());
    }

    @Test
    void aTemplateIconStopsBeingPaintedOnceTheListPoursIntoIt() {
        UpgradesLayout layout = Layouts.of(UpgradesLayout.class);
        Gui<UpgradesLayout> gui = Gui.of(layout);
        gui.list(numbers(2))
                .into(l -> l.OPTION)
                .pagedBy(l -> l.PREVIOUS, l -> l.NEXT)
                .render((entry, icon) -> icon.from(new ItemStack(Material.PAPER, entry)));

        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();

        assertEquals(1, surface.getItem(0).getAmount());
        assertEquals(2, surface.getItem(1).getAmount());
        assertTrue(isEmpty(surface, 2),
                "the third slot has no entry, and the template must not stand in for one: " + surface.getItem(2));
        assertTrue(isEmpty(surface, 18), "one page needs no arrows, layout-declared or not");
    }

    @Test
    void anIconSelectedByFieldDrawsTheStateTheMenuChose() {
        MutableState<Availability> availability = State.of(Availability.LOCKED);
        Gui<UpgradesLayout> gui = Gui.of(UpgradesLayout.class);
        gui.icon(l -> l.TOGGLE).states(Availability.class, availability::get);

        world.openDetached(gui, world.newPlayer("Steve"));
        SurfaceDouble surface = world.getSurface();
        assertEquals(Material.BARRIER, surface.getItem(4).getType());

        availability.set(Availability.DEFAULT);
        world.advanceTicks(2); //one tick polls the state, the next runs the pass it dirtied

        assertEquals(Material.ANVIL, surface.getItem(4).getType(),
                "the poll is what carries a change nobody told the screen about");
    }

}
