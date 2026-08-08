package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.ConfirmGui;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Layouts;
import br.com.finalcraft.evernifecore.minecraft.gui.state.MutableState;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.ClickSimulator;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chain of screens a player walks into, and what walking back gives them.
 *
 * <p>The subject is that a chain holds views and not descriptions. Going back has to hand the player
 * the very screen they left - the page it was on, the filter it had, the counter a component
 * remembered - and that is only true if nothing in between rebuilt it. Every test here changes some
 * per-viewer state before walking deeper and reads it again on the way back, because a chain that
 * reopened the screen instead of reviving it would pass any assertion about titles and fail those.</p>
 *
 * <p>Each screen carries a counter of its own, rendered as the size of the stack in slot 0. That
 * number is the whole observable: {@code IRON_INGOT x7} coming back means the state survived, and the
 * item factory here answers no metadata, so an amount is exactly what a stack compares by.</p>
 */
class GuiNavigationTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;
    private PlayerDouble player;
    private ClickSimulator clicks;

    /** Each screen's own counter, as its view built it - keyed by the title the screen was given. */
    private final Map<String, MutableState<Integer>> counters = new LinkedHashMap<>();
    /** How many times each screen's own state was created, which a revived screen must not increase. */
    private final Map<String, AtomicInteger> declarations = new LinkedHashMap<>();
    /** The futures every {@code ctx.open(...)} handed back, in the order they were opened. */
    private final List<CompletableFuture<Object>> answers = new ArrayList<>();

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        player = world.newPlayer("Steve");
        clicks = world.getClicks();
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
    //  The screens the tests navigate between
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A screen whose counter is drawn in slot 0 as a stack of {@code material}, starting at one.
     *
     * <p>The counter is created inside the component declaration, so it belongs to the view rather
     * than to the description - which is what makes it the right thing to read after a walk back.</p>
     */
    private Gui<LayoutBase> screen(String title, Material material) {
        declarations.put(title, new AtomicInteger());
        Gui<LayoutBase> gui = Gui.of(3).title(title).debounce(0);
        gui.component(component -> {
            MutableState<Integer> counter = component.remember(1);
            counters.put(title, counter);
            declarations.get(title).incrementAndGet();
            component.render(slots -> slots.icon(0, Icon.of(new ItemStack(material, counter.get()))));
        });
        return gui;
    }

    /** Slot 1 of every screen: walks one step deeper and keeps the future the step answers with. */
    private Icon opens(Gui<?> next) {
        return Icon.of(new ItemStack(Material.ARROW)).onClick(context -> answers.add(context.open(next)));
    }

    /** Slot 2 of every screen: walks back, saying {@code value} to whoever opened this step. */
    private Icon goesBackWith(Object value) {
        return Icon.of(new ItemStack(Material.BARRIER)).onClick(context -> context.back(value));
    }

    /** Slot 3 of every screen: puts {@code next} where this screen is. */
    private Icon replacesWith(Gui<?> next) {
        return Icon.of(new ItemStack(Material.NETHER_STAR)).onClick(context -> context.replace(next));
    }

    private void counterOf(String title, int value) {
        counters.get(title).set(value);
        world.advanceTicks(1);
    }

    /** What slot 0 of the window the player is looking at right now holds. */
    private ItemStack onScreen() {
        return world.getSurface().getItem(0);
    }

    private Object valueOf(CompletableFuture<Object> answer) {
        try {
            return answer.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError("The screen was expected to have answered by now", e);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Walking a chain and walking back out of it
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void everyStepBackGivesTheScreenBackWithTheStateItWasLeftOn() {
        Gui<?> third = screen("C", Material.EMERALD).icon(2, goesBackWith("from C"));
        Gui<?> second = screen("B", Material.GOLD_INGOT).icon(1, opens(third)).icon(2, goesBackWith("from B"));
        Gui<?> first = screen("A", Material.IRON_INGOT).icon(1, opens(second));

        GuiView viewA = world.open(first, player);
        counterOf("A", 7);
        clicks.leftClick(player, 1);

        GuiView viewB = GuiViews.getOpenView(player.asPlayer());
        assertNotSame(viewA, viewB, "the second screen is a screen of its own, not the first redrawn");
        assertTrue(viewA.isSuspended(), "the screen underneath keeps everything it holds and stops drawing");
        counterOf("B", 5);
        clicks.leftClick(player, 1);

        GuiView viewC = GuiViews.getOpenView(player.asPlayer());
        assertTrue(viewB.isSuspended());
        assertEquals(2, answers.size());
        assertFalse(answers.get(0).isDone(), "the first step is still waiting on the second");

        clicks.leftClick(player, 2);

        assertEquals("from C", valueOf(answers.get(1)));
        assertTrue(viewC.isClosed(), "the screen walked out of is torn down, not set aside");
        assertSame(viewB, GuiViews.getOpenView(player.asPlayer()), "the very view that was left, revived");
        assertFalse(viewB.isSuspended());
        assertEquals(Material.GOLD_INGOT, onScreen().getType());
        assertEquals(5, onScreen().getAmount(), "the second screen came back on the count it was left on");

        clicks.leftClick(player, 2);

        assertEquals("from B", valueOf(answers.get(0)));
        assertSame(viewA, GuiViews.getOpenView(player.asPlayer()));
        assertEquals(Material.IRON_INGOT, onScreen().getType());
        assertEquals(7, onScreen().getAmount(), "and so did the first, two levels down");
        assertEquals(1, declarations.get("A").get(), "a revived screen declares nothing again - a second "
                + "declaration would mean a second counter, and the count above would have read one");
        assertEquals(1, declarations.get("B").get());
    }

    @Test
    void leavingTheBottomOfTheChainSimplyClosesTheWindow() {
        GuiView only = world.open(screen("A", Material.IRON_INGOT).icon(2, goesBackWith("nobody asked")), player);

        clicks.leftClick(player, 2);

        assertTrue(only.isClosed());
        assertNull(GuiViews.getOpenView(player.asPlayer()), "there was nothing underneath to reveal");
        assertNull(player.getOpenView());
    }

    @Test
    void anOpenNobodyNavigatedToStartsOverInsteadOfContinuingTheChain() {
        Gui<?> second = screen("B", Material.GOLD_INGOT).icon(2, goesBackWith("from B"));
        GuiView viewA = world.open(screen("A", Material.IRON_INGOT).icon(1, opens(second)), player);
        clicks.leftClick(player, 1);
        assertTrue(viewA.isSuspended());

        //a command, not a button: the player is sent somewhere the chain knows nothing about
        GuiView fresh = world.open(screen("Elsewhere", Material.DIAMOND), player);

        assertTrue(viewA.isClosed(), "a screen the player can no longer reach must not stay behind holding "
                + "its tasks and a Player");
        assertTrue(answers.get(0).isCancelled(), "and whoever was waiting on it is told nobody answered");

        clicks.leftClick(player, 2);
        assertSame(fresh, GuiViews.getOpenView(player.asPlayer()), "slot 2 belongs to the abandoned screen, "
                + "not to this one, so nothing here answers it");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What a step answers with
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aConfirmationHandsBackTheAnswerTheButtonMeans() {
        List<Boolean> answered = new ArrayList<>();
        Gui<?> menu = Gui.of(3).debounce(0).icon(1, Icon.of(new ItemStack(Material.ARROW))
                .onClick(context -> context.open(ConfirmGui.of("§eSell it?")).thenAccept(answered::add)));
        GuiView viewMenu = world.open(menu, player);

        clicks.leftClick(player, 1);
        clicks.leftClick(player, denySlot());

        assertEquals(1, answered.size());
        assertEquals(Boolean.FALSE, answered.get(0), "denying is an answer, not a walk away");
        assertSame(viewMenu, GuiViews.getOpenView(player.asPlayer()), "and it gives the screen back");

        clicks.leftClick(player, 1);
        clicks.leftClick(player, confirmSlot());

        assertEquals(2, answered.size());
        assertEquals(Boolean.TRUE, answered.get(1));
        assertSame(viewMenu, GuiViews.getOpenView(player.asPlayer()));
    }

    @Test
    void walkingAwayFromAConfirmationAnswersNobody() {
        List<Boolean> answered = new ArrayList<>();
        List<CompletableFuture<Boolean>> asked = new ArrayList<>();
        Gui<?> menu = Gui.of(3).debounce(0).icon(1, Icon.of(new ItemStack(Material.ARROW))
                .onClick(context -> {
                    CompletableFuture<Boolean> answer = context.open(ConfirmGui.of("§eSell it?"));
                    asked.add(answer);
                    answer.thenAccept(answered::add);
                }));
        world.open(menu, player);
        clicks.leftClick(player, 1);

        player.asPlayer().closeInventory();

        assertTrue(asked.get(0).isCancelled(), "a question nobody answered is not a 'no'");
        assertTrue(answered.isEmpty(), "so nothing downstream of it runs");
    }

    @Test
    void aReplacedScreenIsNotWhereTheStepBackGoes() {
        Gui<?> replacement = screen("Replacement", Material.REDSTONE).icon(2, goesBackWith("from the replacement"));
        Gui<?> middle = screen("Middle", Material.GOLD_INGOT)
                .icon(2, goesBackWith("from the middle"))
                .icon(3, replacesWith(replacement));
        Gui<?> root = screen("Root", Material.IRON_INGOT).icon(1, opens(middle));

        GuiView viewRoot = world.open(root, player);
        counterOf("Root", 4);
        clicks.leftClick(player, 1);
        GuiView viewMiddle = GuiViews.getOpenView(player.asPlayer());

        clicks.leftClick(player, 3);

        GuiView viewReplacement = GuiViews.getOpenView(player.asPlayer());
        assertNotSame(viewMiddle, viewReplacement);
        assertTrue(viewMiddle.isClosed(), "the screen replaced is gone, not set aside");
        assertTrue(viewRoot.isSuspended(), "and the one underneath was never touched");
        assertFalse(answers.get(0).isDone(), "whoever opened this step is still waiting - now on the "
                + "replacement, which inherited the debt");

        clicks.leftClick(player, 2);

        assertEquals("from the replacement", valueOf(answers.get(0)));
        assertSame(viewRoot, GuiViews.getOpenView(player.asPlayer()), "the step back skipped the screen that "
                + "was replaced and landed on the one below it");
        assertEquals(Material.IRON_INGOT, onScreen().getType());
        assertEquals(4, onScreen().getAmount());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Set aside, and picked up again
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aScreenSetAsideIsNeitherClickedNorDrawnUntilItComesBack() {
        AtomicInteger underneath = new AtomicInteger();
        AtomicInteger onTop = new AtomicInteger();
        Gui<?> second = screen("B", Material.GOLD_INGOT)
                .icon(1, Icon.of(new ItemStack(Material.ARROW)).onClick(context -> onTop.incrementAndGet()))
                .icon(2, goesBackWith("done"));
        Gui<?> first = screen("A", Material.IRON_INGOT)
                .icon(1, Icon.of(new ItemStack(Material.ARROW)).onClick(context -> {
                    underneath.incrementAndGet();
                    answers.add(context.open(second));
                }));

        GuiView viewA = world.open(first, player);
        counterOf("A", 7);
        SurfaceDouble setAside = world.getSurface();
        clicks.leftClick(player, 1);
        assertEquals(1, underneath.get());
        assertTrue(viewA.isSuspended());

        setAside.forgetWrites();
        counters.get("A").set(9);
        world.advanceTicks(5);

        assertEquals(0, setAside.getWriteCount(), "a screen with nowhere to write does not write: "
                + setAside.getWrites());
        assertEquals(7, setAside.getItem(0).getAmount(), "the container it used to have still shows the "
                + "count from before it was set aside");

        clicks.leftClick(player, 1);

        assertEquals(1, underneath.get(), "the two screens both have a button on slot 1, and only the one "
                + "the player is looking at answered");
        assertEquals(1, onTop.get());

        clicks.leftClick(player, 2);

        assertFalse(viewA.isSuspended(), "picked up again");
        assertSame(viewA, GuiViews.getOpenView(player.asPlayer()));
        assertNotSame(setAside, world.getSurface(), "on a container it has never written into");
        assertEquals(9, onScreen().getAmount(), "so what changed while it was away is drawn now, and the "
                + "state that changed is the one it kept");
        assertTrue(world.getSurface().getWriteCount() > 0, "the whole screen is redrawn, since nothing on "
                + "this container was ever the framework's");
    }

    @Test
    void onlyTheScreenOnDisplayCanBeSetAsideAndOnlyASetAsideOneComesBack() {
        GuiView view = world.open(screen("A", Material.IRON_INGOT), player);

        assertTrue(GuiNavigation.suspend(view));
        assertTrue(view.isSuspended());
        assertFalse(GuiNavigation.suspend(view), "a screen already set aside has no window left to give up");

        assertTrue(GuiNavigation.resume(view));
        assertFalse(view.isSuspended());
        assertFalse(GuiNavigation.resume(view), "and one that has a window has nothing to be given back");

        GuiView other = world.open(screen("B", Material.GOLD_INGOT), player);

        assertTrue(view.isClosed(), "an open outside the chain took the window from the first screen");
        assertFalse(GuiNavigation.suspend(view), "and a screen that is gone cannot be set aside");
        assertTrue(GuiNavigation.suspend(other));

        player.online(false);

        assertFalse(GuiNavigation.resume(other), "there is nobody left to give it back to");
        assertTrue(other.isSuspended(), "so it stays set aside rather than pretending it is on screen");
    }

    @Test
    void closingTheWindowMidChainLeavesNoScreenNoTaskAndNobodyWaiting() {
        Gui<?> third = screen("C", Material.EMERALD);
        Gui<?> second = screen("B", Material.GOLD_INGOT).icon(1, opens(third));
        Gui<?> first = screen("A", Material.IRON_INGOT).icon(1, opens(second));
        //something for each screen to keep alive, so "no task left" is a measurement and not a vacuum
        first.component(component -> component.every(2).render(slots -> { }));
        second.component(component -> component.every(2).render(slots -> { }));
        third.component(component -> component.every(2).render(slots -> { }));

        GuiView viewA = world.open(first, player);
        clicks.leftClick(player, 1);
        GuiView viewB = GuiViews.getOpenView(player.asPlayer());
        clicks.leftClick(player, 1);
        GuiView viewC = GuiViews.getOpenView(player.asPlayer());
        assertEquals(3, world.getScheduler().getPeriodicTaskCount(), "three screens, three clocks");

        player.asPlayer().closeInventory();
        world.advanceTicks(3);

        assertTrue(viewA.isClosed(), "the whole chain goes, not just the window that was on screen");
        assertTrue(viewB.isClosed());
        assertTrue(viewC.isClosed());
        assertNull(viewA.getViewer(), "and none of them keeps the Player");
        assertNull(viewB.getViewer());
        assertEquals(0, GuiViews.getOpenCount());
        assertEquals(0, world.getScheduler().getPeriodicTaskCount(), "nor a clock of its own");
        assertTrue(answers.get(0).isCancelled(), "and each step is told its answer never came");
        assertTrue(answers.get(1).isCancelled());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Where the shared confirmation puts its buttons - the file the admin restyles, read as the test reads it
    // -----------------------------------------------------------------------------------------------------------------

    private int confirmSlot() {
        return slotOf("CONFIRM");
    }

    private int denySlot() {
        return slotOf("DENY");
    }

    private int slotOf(String iconName) {
        LayoutBase.PlacedIcon placed = Layouts.of(ConfirmGui.ConfirmLayout.class).getIcons().get(iconName);
        int[] slots = placed.getSlots().resolve(Gui.of(3).getGeometry()).toArray();
        return slots[0];
    }

}
