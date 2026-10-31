package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.testing.Logs;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The primitives that replace the copied {@code for}, the {@code if} stealing a slot and the page
 * arithmetic every menu used to write again: a list poured into a region, the page that region implies,
 * and an icon that draws whichever state the plugin says it is in.
 *
 * <p>An entry is drawn as a stack of paper whose AMOUNT is the entry, so what a slot holds names the entry
 * that wrote it.</p>
 */
class DataComponentTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender steve;

    @BeforeEach
    void setup() {
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

    private enum Availability {DEFAULT, LOCKED, MAXED}

    @ScreenLayout(title = "Upgrades", rows = 3)
    public static class UpgradesLayout extends Layout {

        @IconData(slot = {0, 1, 2})
        public Icon OPTION = Icon.of(item("paper"));

        @IconData(slot = {18})
        public Icon PREVIOUS = Icon.of(item("arrow"));

        @IconData(slot = {26})
        public Icon NEXT = Icon.of(item("arrow"));

        @IconData(slot = {4})
        public Icon TOGGLE = Icon.of(item("anvil"))
                .addState(Availability.LOCKED, item("barrier"));

        @IconData(slot = {8}, background = true)
        public Icon DECORATION = Icon.of(item("glass"));
    }

    private static ItemSpec item(String name) {
        return ItemSpec.of("minecraft:" + name, 1);
    }

    private static ItemSpec paper(int amount) {
        return ItemSpec.of("minecraft:paper", amount);
    }

    private static List<Integer> numbers(int count) {
        List<Integer> entries = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            entries.add(index);
        }
        return entries;
    }

    /** One paper per entry, its amount being the entry. */
    private static Screen<?> listOf(List<Integer> entries, Pager pager) {
        Screen<Layout> screen = Screen.of(3);
        screen.list(entries)
                .pager(pager)
                .into(Slots.of(0, 1, 2))
                .pagedBy(Slots.of(18), Slots.of(26))
                .render((entry, icon) -> icon.from(paper(entry)));
        return screen;
    }

    private RecordingSurface surfaceOf(ScreenView view) {
        return world.getRenderer().surfaceOf(view);
    }

    private static boolean isEmpty(RecordingSurface surface, int slot) {
        RenderedIcon drawn = surface.getSlot(slot);
        return drawn == null || drawn.isEmpty();
    }

    private static int amountAt(RecordingSurface surface, int slot) {
        RenderedIcon drawn = surface.getSlot(slot);
        assertFalse(drawn == null || drawn.isEmpty(), "slot " + slot + " shows nothing");
        return drawn.getItem().getAmount();
    }

    private static String materialAt(RecordingSurface surface, int slot) {
        RenderedIcon drawn = surface.getSlot(slot);
        return drawn == null ? null : drawn.getItem().getMaterialId();
    }

    private void close(ScreenView view) {
        view.close();
        world.advanceTicks(2);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The region is the page
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theRegionDecidesHowManyEntriesAPageHolds() {
        Pager pager = new Pager();
        ScreenView view = world.open(listOf(numbers(7), pager), steve);
        RecordingSurface surface = surfaceOf(view);

        assertEquals(3, pager.getPageSize(), "three slots of region means three entries a page");
        assertEquals(3, pager.getTotalPages(), "seven entries over three slots is three pages");
        assertEquals(1, amountAt(surface, 0));
        assertEquals(2, amountAt(surface, 1));
        assertEquals(3, amountAt(surface, 2), "the order on screen is the order of the source");
        close(view);
    }

    @Test
    void turningThePageShowsTheEntriesThatComeNext() {
        Pager pager = new Pager();
        ScreenView view = world.open(listOf(numbers(7), pager), steve);
        RecordingSurface surface = surfaceOf(view);
        world.advanceTicks(1);
        surface.forgetApplies();

        pager.next();
        world.advanceTicks(1);

        assertEquals(2, pager.getPage());
        assertEquals(4, amountAt(surface, 0));
        assertEquals(6, amountAt(surface, 2));
        close(view);
    }

    @Test
    void aShortPageLeavesTheSlotsItDoesNotReachEmpty() {
        Pager pager = new Pager();
        ScreenView view = world.open(listOf(numbers(7), pager), steve);
        RecordingSurface surface = surfaceOf(view);

        pager.last();
        world.advanceTicks(1);

        assertEquals(3, pager.getPage());
        assertEquals(7, amountAt(surface, 0));
        assertTrue(isEmpty(surface, 1),
                "the last page holds one entry, so the other two slots show nothing: " + surface.getSlot(1));
        close(view);
    }

    @Test
    void aListThatFitsOnOnePageDrawsNoPageButtons() {
        Pager pager = new Pager();
        ScreenView view = world.open(listOf(numbers(2), pager), steve);
        RecordingSurface surface = surfaceOf(view);

        assertEquals(1, pager.getTotalPages());
        assertTrue(isEmpty(surface, 18),
                "an arrow with nowhere to go is a slot taken for nothing: " + surface.getSlot(18));
        assertTrue(isEmpty(surface, 26));
        close(view);
    }

    /** A shop window: 24 slots of goods and the two arrows under them, which hold goods when nothing pages. */
    private static Screen<?> shopOf(int goods, Pager pager, boolean pagedSource) {
        Screen<Layout> screen = Screen.of(6);
        PagedList<Integer, Layout> list = pagedSource
                ? screen.<Integer>list((page, size) -> {
                    List<Integer> all = numbers(goods);
                    int from = (page - 1) * size;
                    return all.subList(Math.min(from, goods), Math.min(from + size, goods));
                }).total(() -> goods)
                : screen.list(numbers(goods));
        list.pager(pager)
                .into(Slots.of(IntStream.range(0, 24).toArray()))
                .pagedBy(Slots.of(37), Slots.of(43))
                .spillIntoPageArrows(true)
                .render((entry, icon) -> icon.from(paper(entry)));
        return screen;
    }

    @Test
    void theArrowSlotsHoldEntriesOnlyWhileEverythingFitsOnOnePage() {
        TestFPlayerSender alex = world.viewer("Alex");
        for (boolean pagedSource : new boolean[]{false, true}) {
            Pager pager = new Pager();
            ScreenView fits = world.open(shopOf(26, pager, pagedSource), steve);
            RecordingSurface surface = surfaceOf(fits);

            assertEquals(1, pager.getTotalPages(), "26 goods fit 24 slots and the two arrows");
            assertEquals(25, amountAt(surface, 37), "the previous arrow slot comes first");
            assertEquals(26, amountAt(surface, 43));

            pager = new Pager();
            ScreenView pages = world.open(shopOf(27, pager, pagedSource), alex);
            surface = surfaceOf(pages);

            assertEquals(2, pager.getTotalPages(), "27 do not, so the list pages over the region alone");
            assertEquals(24, pager.getPageSize(), "and every page is the region's size");
            assertFalse(isEmpty(surface, 43), "the arrows are drawn in their slots");
            assertNotEquals("minecraft:paper", materialAt(surface, 43), "and what is drawn there is an arrow");

            close(fits);
            close(pages);
        }
    }

    @Test
    void clickingTheArrowTurnsThePage() {
        Pager pager = new Pager();
        ScreenView view = world.open(listOf(numbers(7), pager), steve);
        RecordingSurface surface = surfaceOf(view);
        assertFalse(isEmpty(surface, 26), "with three pages the forward arrow is on screen");

        world.click(view, 26, ClickGesture.PRIMARY, ClickKind.NOTHING);
        world.advanceTicks(1);

        assertEquals(2, pager.getPage());
        assertEquals(4, amountAt(surface, 0));
        close(view);
    }

    @Test
    void aSourceThatAnsweredTheSameEntriesCostsNoWrite() {
        MutableState<String> filter = State.of("a");
        Screen<Layout> screen = Screen.of(3);
        screen.list(() -> numbers(3))
                .dependsOn(filter)
                .into(Slots.of(0, 1, 2))
                .render((entry, icon) -> icon.from(paper(entry)));

        ScreenView view = world.open(screen, steve);
        RecordingSurface surface = surfaceOf(view);
        world.advanceTicks(1);
        surface.forgetApplies();

        filter.set("b");
        world.advanceTicks(1);

        assertEquals(0, surface.getApplies().size(),
                "the list rendered again and drew the same picture: " + surface.getApplies());
        close(view);
    }

    @Test
    void anEntryWhoseRenderThrowsCostsThatEntryAlone() {
        Screen<Layout> screen = Screen.of(3);
        screen.list(numbers(3))
                .into(Slots.of(0, 1, 2))
                .render((entry, icon) -> {
                    if (entry == 2) {
                        throw new IllegalStateException("this entry is broken");
                    }
                    icon.from(paper(entry));
                });

        ScreenView view = world.open(screen, steve);
        RecordingSurface surface = surfaceOf(view);

        assertEquals(1, amountAt(surface, 0));
        assertEquals(3, amountAt(surface, 2), "the entries around the broken one still draw");
        assertTrue(isEmpty(surface, 1));
        close(view);
    }

    @Test
    void aSourceThatThrowsCostsThePageAndNotTheScreen() {
        Screen<Layout> screen = Screen.of(3);
        screen.<Integer>list(() -> {
                    throw new IllegalStateException("the database went away");
                })
                .into(Slots.of(0, 1, 2))
                .render((entry, icon) -> icon.from(paper(1)));
        screen.icon(8, Icon.of(item("anvil")));

        //the core logs through its fallback logger here, which only Logs reads alongside the platform's sink
        AtomicReference<ScreenView> opened = new AtomicReference<>();
        List<String> logged = Logs.capture(() -> opened.set(world.open(screen, steve)));
        ScreenView view = opened.get();
        RecordingSurface surface = surfaceOf(view);

        assertTrue(isEmpty(surface, 0), "a page nothing could be read for is drawn empty");
        assertEquals("minecraft:anvil", materialAt(surface, 8), "and the screen around it still opens");
        assertTrue(logged.toString().contains("the database went away"),
                "an empty list and a broken one look the same, so only the log tells them apart: " + logged);
        close(view);
    }

    @Test
    void aListThatShrankUnderTheReaderTellsWhoeverWasReadingThePage() {
        List<Integer> entries = new ArrayList<>(numbers(7));
        MutableState<Integer> version = State.of(0);
        Pager pager = new Pager();
        Screen<Layout> screen = Screen.of(3);
        screen.list(entries)
                .pager(pager)
                .dependsOn(version)
                .into(Slots.of(0, 1, 2))
                .render((entry, icon) -> icon.from(paper(entry)));
        //a second component reading the page, which is what a title that says "1/3" really is
        screen.addComponent(component -> {
            component.remember(pager);
            component.render(writer -> writer.icon(8, Icon.of(ItemSpec.of("minecraft:book", pager.getPage()))));
        });

        ScreenView view = world.open(screen, steve);
        RecordingSurface surface = surfaceOf(view);
        pager.last();
        world.advanceTicks(1);
        assertEquals(3, amountAt(surface, 8), "the reader is on the last page");

        entries.subList(3, entries.size()).clear();
        version.set(1);   //only the list is told; the page reader has to hear it from the pager
        world.advanceTicks(1);
        assertEquals(1, pager.getPage(), "the page they were on no longer exists");

        world.advanceTicks(1);   //being told is a change like any other, and it lands on the next tick

        assertEquals(1, amountAt(surface, 8),
                "a page number nobody was told about is a screen that reads one and shows another");
        close(view);
    }

    @Test
    void aPageSourceIsAskedForOnePageAndCountedApart() {
        AtomicReference<int[]> asked = new AtomicReference<>();
        Pager pager = new Pager();
        Screen<Layout> screen = Screen.of(3);
        screen.<Integer>list((page, size) -> {
                    asked.set(new int[]{page, size});
                    return Arrays.asList(page * 10, page * 10 + 1);
                })
                .total(() -> 40)
                .pager(pager)
                .into(Slots.of(0, 1, 2))
                .render((entry, icon) -> icon.from(paper(entry)));

        ScreenView view = world.open(screen, steve);

        assertEquals(1, asked.get()[0], "pages count from 1");
        assertEquals(3, asked.get()[1], "what is asked for is one region's worth");
        assertEquals(14, pager.getTotalPages(), "forty entries over three slots");
        assertEquals(10, amountAt(surfaceOf(view), 0));
        close(view);
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
        Icon icon = Icon.of(item("anvil"))
                .addState(Availability.LOCKED, item("barrier"))
                .addState(Availability.MAXED, item("diamond"))
                .states(Availability.class, availability::get);

        ScreenView view = world.open(Screen.of(3).addComponent(component -> {
            component.remember(availability);
            component.render(writer -> writer.icon(13, icon));
        }), steve);
        RecordingSurface surface = surfaceOf(view);
        assertEquals("minecraft:anvil", materialAt(surface, 13));

        availability.set(Availability.LOCKED);
        world.advanceTicks(1);

        assertEquals("minecraft:barrier", materialAt(surface, 13));
        close(view);
    }

    @Test
    void aConstantNoAppearanceWasDeclaredForFallsBackToTheIconItself() {
        Icon icon = Icon.of(item("anvil"))
                .addState(Availability.LOCKED, item("barrier"))
                .states(Availability.class, () -> Availability.MAXED);

        Screen<Layout> screen = Screen.of(3);
        screen.icon(13, icon);
        ScreenView view = world.open(screen, steve);

        assertEquals("minecraft:anvil", materialAt(surfaceOf(view), 13),
                "a domain enum is allowed to carry more constants than the screen has looks");
        close(view);
    }

    @Test
    void aCycleWalksTheConstantsInDeclarationOrderAndWrapsAround() {
        AtomicReference<Availability> stored = new AtomicReference<>(Availability.DEFAULT);
        MutableState<Availability> bound = State.bound(stored::get, stored::set);
        List<Availability> seen = new ArrayList<>();

        Screen<Layout> screen = Screen.of(3);
        screen.debounce(0);
        screen.icon(13, Icon.of(item("anvil"))
                        .addState(Availability.LOCKED, item("barrier"))
                        .addState(Availability.MAXED, item("diamond")))
                .cycle(Availability.class, bound).onCycle(seen::add);

        ScreenView view = world.open(screen, steve);
        RecordingSurface surface = surfaceOf(view);

        world.click(view, 13, ClickGesture.PRIMARY, ClickKind.NOTHING);
        world.advanceTicks(1);
        assertEquals(Availability.LOCKED, stored.get(), "the cycle writes through the state it was given");
        assertEquals("minecraft:barrier", materialAt(surface, 13));

        world.click(view, 13, ClickGesture.PRIMARY, ClickKind.NOTHING);
        world.click(view, 13, ClickGesture.PRIMARY, ClickKind.NOTHING);
        world.advanceTicks(1);

        assertEquals(Availability.DEFAULT, stored.get(), "past the last constant the walk starts over");
        assertEquals(Arrays.asList(Availability.LOCKED, Availability.MAXED, Availability.DEFAULT), seen);
        assertEquals("minecraft:anvil", materialAt(surface, 13));
        close(view);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  State that outlives the screen
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aBoundStateReadsAndWritesThroughTheFunctionsItWasGiven() {
        AtomicReference<Availability> stored = new AtomicReference<>(Availability.LOCKED);
        MutableState<Availability> bound = State.bound(stored::get, stored::set);

        assertEquals(Availability.LOCKED, bound.get(), "the value is read from where it lives");

        Icon icon = Icon.of(item("anvil"))
                .addState(Availability.LOCKED, item("barrier"))
                .states(Availability.class, bound::get);
        ScreenView view = world.open(Screen.of(3).addComponent(component -> {
            component.remember(bound);
            component.render(writer -> writer.icon(13, icon));
        }), steve);
        RecordingSurface surface = surfaceOf(view);
        assertEquals("minecraft:barrier", materialAt(surface, 13));

        bound.set(Availability.DEFAULT);
        world.advanceTicks(1);

        assertEquals(Availability.DEFAULT, stored.get(), "the setter is what persists it");
        assertEquals("minecraft:anvil", materialAt(surface, 13));
        close(view);
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
        Screen<UpgradesLayout> screen = Screen.of(layout);

        assertEquals(3, screen.getRows());
        assertEquals("Upgrades", screen.getTitle());

        ScreenView view = world.open(screen, steve);
        RecordingSurface surface = surfaceOf(view);

        assertEquals("minecraft:glass", materialAt(surface, 8),
                "an icon nothing bound is still painted - that is what a layout is for");
        assertEquals("minecraft:paper", materialAt(surface, 0));
        close(view);
    }

    @Test
    void aTemplateIconStopsBeingPaintedOnceTheListPoursIntoIt() {
        UpgradesLayout layout = Layouts.of(UpgradesLayout.class);
        Screen<UpgradesLayout> screen = Screen.of(layout);
        screen.list(numbers(2))
                .into(l -> l.OPTION)
                .pagedBy(l -> l.PREVIOUS, l -> l.NEXT)
                .render((entry, icon) -> icon.from(paper(entry)));

        ScreenView view = world.open(screen, steve);
        RecordingSurface surface = surfaceOf(view);

        assertEquals(1, amountAt(surface, 0));
        assertEquals(2, amountAt(surface, 1));
        assertTrue(isEmpty(surface, 2),
                "the third slot has no entry, and the template must not stand in for one: " + surface.getSlot(2));
        assertTrue(isEmpty(surface, 18), "one page needs no arrows, layout-declared or not");
        close(view);
    }

    @Test
    void anIconSelectedByFieldDrawsTheStateTheMenuChose() {
        MutableState<Availability> availability = State.of(Availability.LOCKED);
        Screen<UpgradesLayout> screen = Screen.of(UpgradesLayout.class);
        screen.icon(l -> l.TOGGLE).states(Availability.class, availability::get);

        ScreenView view = world.open(screen, steve);
        RecordingSurface surface = surfaceOf(view);
        assertEquals("minecraft:barrier", materialAt(surface, 4));

        availability.set(Availability.DEFAULT);
        world.advanceTicks(2); //one tick polls the state, the next runs the pass it dirtied

        assertEquals("minecraft:anvil", materialAt(surface, 4),
                "the poll is what carries a change nobody told the screen about");
        close(view);
    }

}
