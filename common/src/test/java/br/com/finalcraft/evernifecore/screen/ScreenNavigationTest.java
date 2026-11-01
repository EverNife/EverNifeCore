package br.com.finalcraft.evernifecore.screen;

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
 * <p>The subject is that a chain holds views and not descriptions. Going back has to hand the player the very view they
 * left - the page it was on, the filter it had, the counter a component remembered - and that is only true if nothing in
 * between rebuilt it. Every test here changes some per-viewer state before walking deeper and reads it again on the way
 * back, because a chain that reopened the screen instead of reviving it would pass any assertion about titles and fail
 * those.</p>
 *
 * <p>Each screen carries a counter of its own, rendered as the amount of the item in slot 0. That number is the whole
 * observable: {@code iron_ingot x7} coming back means the state survived.</p>
 */
class ScreenNavigationTest {

    private static final int CONFIRM_SLOT = 11;
    private static final int DENY_SLOT = 15;

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender alex;

    /** Each screen's own counter, as its view built it - keyed by the title the screen was given. */
    private final Map<String, MutableState<Integer>> counters = new LinkedHashMap<>();
    /** How many times each screen's own state was created, which a revived screen must not increase. */
    private final Map<String, AtomicInteger> declarations = new LinkedHashMap<>();
    /** The futures every {@code ctx.open(...)} handed back, in the order they were opened. */
    private final List<CompletableFuture<Object>> answers = new ArrayList<>();

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
    }

    @AfterEach
    void teardown() {
        world.close();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The screens the tests navigate between
    // -----------------------------------------------------------------------------------------------------------------

    private static Icon icon(String material) {
        return Icon.of(ItemSpec.of(material, 1));
    }

    /**
     * A screen whose counter is drawn in slot 0 as a stack of {@code material}, starting at one.
     *
     * <p>The counter is created inside the component declaration, so it belongs to the view rather than to the
     * description - which is what makes it the right thing to read after a walk back.</p>
     */
    private Screen<Layout> screen(String title, String material) {
        declarations.put(title, new AtomicInteger());
        Screen<Layout> screen = Screen.of(3).title(title).debounce(0);
        screen.addComponent(component -> {
            MutableState<Integer> counter = component.remember(1);
            counters.put(title, counter);
            declarations.get(title).incrementAndGet();
            component.render(slots -> slots.icon(0, Icon.of(ItemSpec.of(material, counter.get()))));
        });
        return screen;
    }

    /** Slot 1: walks one step deeper and keeps the future the step answers with. */
    private Screen<Layout> opens(Screen<Layout> on, Screen<?> next) {
        on.icon(1, icon("minecraft:arrow")).onClick(context -> answers.add(context.open(next)));
        return on;
    }

    /** Slot 2: walks back, saying {@code value} to whoever opened this step. */
    private Screen<Layout> goesBackWith(Screen<Layout> on, Object value) {
        on.icon(2, icon("minecraft:barrier")).onClick(context -> context.back(value));
        return on;
    }

    /** Slot 3: puts {@code next} where this screen is. */
    private Screen<Layout> replacesWith(Screen<Layout> on, Screen<?> next) {
        on.icon(3, icon("minecraft:nether_star")).onClick(context -> context.replace(next));
        return on;
    }

    /** A yes/no question: each button hands its answer back to whoever opened it. */
    private static Screen<Layout> confirmation() {
        Screen<Layout> screen = Screen.of(3).title("Sell it?").debounce(0);
        screen.icon(CONFIRM_SLOT, icon("minecraft:lime_wool")).onClick(context -> context.back(Boolean.TRUE));
        screen.icon(DENY_SLOT, icon("minecraft:red_wool")).onClick(context -> context.back(Boolean.FALSE));
        return screen;
    }

    private void counterOf(String title, int value) {
        counters.get(title).set(value);
        world.advanceTicks(1);
    }

    private ScreenView current() {
        return Screens.getOpenView(alex);
    }

    private void click(int slot) {
        world.click(current(), slot, ClickGesture.PRIMARY, ClickKind.NOTHING);
    }

    /** Clicks a button that navigates: the window swap happens one tick after the click, never inside it. */
    private void navigate(int slot) {
        click(slot);
        world.advanceTicks(1);
    }

    /** What slot 0 of the window the player is looking at right now shows. */
    private ItemSpec onScreen() {
        return world.getRenderer().getSurface().getSlot(0).getItem();
    }

    private Object valueOf(CompletableFuture<Object> answer) {
        try {
            return answer.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError("The screen was expected to have answered by now", e);
        }
    }

    private void closeWhatIsOpen() {
        ScreenView open = current();
        if (open != null) {
            open.close();
        }
        world.advanceTicks(2);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Walking a chain and walking back out of it
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void everyStepBackGivesTheScreenBackWithTheStateItWasLeftOn() {
        Screen<Layout> third = goesBackWith(screen("C", "minecraft:emerald"), "from C");
        Screen<Layout> second = goesBackWith(opens(screen("B", "minecraft:gold_ingot"), third), "from B");
        Screen<Layout> first = opens(screen("A", "minecraft:iron_ingot"), second);

        ScreenView viewA = world.open(first, alex);
        counterOf("A", 7);
        navigate(1);

        ScreenView viewB = current();
        assertNotSame(viewA, viewB, "the second screen is a screen of its own, not the first redrawn");
        assertTrue(viewA.isSuspended(), "the screen underneath keeps everything it holds and stops drawing");
        counterOf("B", 5);
        navigate(1);

        ScreenView viewC = current();
        assertTrue(viewB.isSuspended());
        assertEquals(2, answers.size());
        assertFalse(answers.get(0).isDone(), "the first step is still waiting on the second");

        navigate(2);

        assertEquals("from C", valueOf(answers.get(1)));
        assertTrue(viewC.isClosed(), "the screen walked out of is torn down, not set aside");
        assertSame(viewB, current(), "the very view that was left, revived");
        assertFalse(viewB.isSuspended());
        assertEquals("minecraft:gold_ingot", onScreen().getMaterialId());
        assertEquals(5, onScreen().getAmount(), "the second screen came back on the count it was left on");

        navigate(2);

        assertEquals("from B", valueOf(answers.get(0)));
        assertSame(viewA, current());
        assertEquals("minecraft:iron_ingot", onScreen().getMaterialId());
        assertEquals(7, onScreen().getAmount(), "and so did the first, two levels down");
        assertEquals(1, declarations.get("A").get(), "a revived screen declares nothing again - a second "
                + "declaration would mean a second counter, and the count above would have read one");
        assertEquals(1, declarations.get("B").get());
        closeWhatIsOpen();
    }

    @Test
    void theWindowIsNeverSwappedInsideTheClickThatAskedForIt() {
        Screen<Layout> second = screen("B", "minecraft:gold_ingot");
        ScreenView viewA = world.open(opens(screen("A", "minecraft:iron_ingot"), second), alex);

        click(1);

        assertSame(viewA, current(), "a renderer applies the click against the window that is open when the click "
                + "ends, so it must still be the old one");
        assertFalse(viewA.isSuspended());

        world.advanceTicks(1);

        assertNotSame(viewA, current(), "the swap lands one tick later");
        assertTrue(viewA.isSuspended());
        closeWhatIsOpen();
    }

    @Test
    void leavingTheBottomOfTheChainSimplyClosesTheWindow() {
        ScreenView only = world.open(goesBackWith(screen("A", "minecraft:iron_ingot"), "nobody asked"), alex);
        RecordingSurface window = world.getRenderer().surfaceOf(only);

        navigate(2);

        assertTrue(only.isClosed());
        assertNull(current(), "there was nothing underneath to reveal");
        assertTrue(window.isClosed(), "so the window itself goes");
    }

    @Test
    void anOpenNobodyNavigatedToStartsOverInsteadOfContinuingTheChain() {
        Screen<Layout> second = goesBackWith(screen("B", "minecraft:gold_ingot"), "from B");
        ScreenView viewA = world.open(opens(screen("A", "minecraft:iron_ingot"), second), alex);
        navigate(1);
        assertTrue(viewA.isSuspended());

        //a command, not a button: the player is sent somewhere the chain knows nothing about
        ScreenView fresh = world.open(screen("Elsewhere", "minecraft:diamond"), alex);

        assertTrue(viewA.isClosed(), "a screen the player can no longer reach must not stay behind holding "
                + "its tasks and a player");
        assertTrue(answers.get(0).isCancelled(), "and whoever was waiting on it is told nobody answered");

        navigate(2);
        assertSame(fresh, current(), "slot 2 belongs to the abandoned screen, not to this one, so nothing here "
                + "answers it");
        closeWhatIsOpen();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What a step answers with
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aConfirmationHandsBackTheAnswerTheButtonMeans() {
        List<Boolean> answered = new ArrayList<>();
        Screen<Layout> menu = Screen.of(3).debounce(0);
        menu.icon(1, icon("minecraft:arrow"))
                .onClick(context -> context.open(confirmation(), Boolean.class).thenAccept(answered::add));
        ScreenView viewMenu = world.open(menu, alex);

        navigate(1);
        navigate(DENY_SLOT);

        assertEquals(1, answered.size());
        assertEquals(Boolean.FALSE, answered.get(0), "denying is an answer, not a walk away");
        assertSame(viewMenu, current(), "and it gives the screen back");

        navigate(1);
        navigate(CONFIRM_SLOT);

        assertEquals(2, answered.size());
        assertEquals(Boolean.TRUE, answered.get(1));
        assertSame(viewMenu, current());
        closeWhatIsOpen();
    }

    @Test
    void walkingAwayFromAConfirmationAnswersNobody() {
        List<Object> answered = new ArrayList<>();
        List<CompletableFuture<Object>> asked = new ArrayList<>();
        Screen<Layout> menu = Screen.of(3).debounce(0);
        menu.icon(1, icon("minecraft:arrow")).onClick(context -> {
            CompletableFuture<Object> answer = context.open(confirmation());
            asked.add(answer);
            answer.thenAccept(answered::add);
        });
        world.open(menu, alex);
        navigate(1);

        world.getRenderer().surfaceOf(current()).playerCloses();

        assertTrue(asked.get(0).isCancelled(), "a question nobody answered is not a 'no'");
        assertTrue(answered.isEmpty(), "so nothing downstream of it runs");
        assertEquals(0, Screens.getOpenCount());
    }

    @Test
    void aReplacedScreenIsNotWhereTheStepBackGoes() {
        Screen<Layout> replacement = goesBackWith(screen("Replacement", "minecraft:redstone"), "from the replacement");
        Screen<Layout> middle = replacesWith(goesBackWith(screen("Middle", "minecraft:gold_ingot"), "from the middle"),
                replacement);
        Screen<Layout> root = opens(screen("Root", "minecraft:iron_ingot"), middle);

        ScreenView viewRoot = world.open(root, alex);
        counterOf("Root", 4);
        navigate(1);
        ScreenView viewMiddle = current();

        navigate(3);

        ScreenView viewReplacement = current();
        assertNotSame(viewMiddle, viewReplacement);
        assertTrue(viewMiddle.isClosed(), "the screen replaced is gone, not set aside");
        assertTrue(viewRoot.isSuspended(), "and the one underneath was never touched");
        assertFalse(answers.get(0).isDone(), "whoever opened this step is still waiting - now on the "
                + "replacement, which inherited the debt");

        navigate(2);

        assertEquals("from the replacement", valueOf(answers.get(0)));
        assertSame(viewRoot, current(), "the step back skipped the screen that was replaced and landed on the one "
                + "below it");
        assertEquals("minecraft:iron_ingot", onScreen().getMaterialId());
        assertEquals(4, onScreen().getAmount());
        closeWhatIsOpen();
    }

    @Test
    void replacingAScreenSetAsideSwapsItInPlaceAndLeavesTheTopAlone() {
        Screen<Layout> replacement = goesBackWith(screen("Replacement", "minecraft:redstone"), "from the replacement");
        Screen<Layout> top = goesBackWith(screen("Top", "minecraft:emerald"), "from the top");
        Screen<Layout> middle = opens(screen("Middle", "minecraft:gold_ingot"), top);
        Screen<Layout> root = opens(screen("Root", "minecraft:iron_ingot"), middle);

        ScreenView viewRoot = world.open(root, alex);
        navigate(1);
        ScreenView viewMiddle = current();
        navigate(1);
        ScreenView viewTop = current();
        RecordingSurface topWindow = world.getRenderer().surfaceOf(viewTop);

        ScreenNavigation.replace(viewMiddle, replacement); //a plugin swapping a screen the player is not looking at
        world.advanceTicks(1);

        assertSame(viewTop, current(), "the screen on display is not the one replaced, so it keeps the window");
        assertFalse(viewTop.isSuspended());
        assertFalse(topWindow.isClosed());
        assertTrue(viewMiddle.isClosed(), "the replaced screen is torn down");
        assertTrue(viewRoot.isSuspended(), "and the one under it was never touched");

        navigate(2);

        assertEquals("from the top", valueOf(answers.get(1)));
        ScreenView viewReplacement = current();
        assertNotSame(viewMiddle, viewReplacement);
        assertNotSame(viewRoot, viewReplacement, "the step back lands on the replacement, where the middle was");
        assertEquals("minecraft:redstone", onScreen().getMaterialId());
        assertFalse(answers.get(0).isDone(), "the root still waits - on the replacement now");

        navigate(2);

        assertEquals("from the replacement", valueOf(answers.get(0)), "the replacement inherited what the middle owed");
        assertSame(viewRoot, current());
        closeWhatIsOpen();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Set aside, and picked up again
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aScreenSetAsideIsNeitherClickedNorDrawnUntilItComesBack() {
        AtomicInteger underneath = new AtomicInteger();
        AtomicInteger onTop = new AtomicInteger();
        Screen<Layout> second = goesBackWith(screen("B", "minecraft:gold_ingot"), "done");
        second.icon(1, icon("minecraft:arrow")).onClick(context -> onTop.incrementAndGet());
        Screen<Layout> first = screen("A", "minecraft:iron_ingot");
        first.icon(1, icon("minecraft:arrow")).onClick(context -> {
            underneath.incrementAndGet();
            answers.add(context.open(second));
        });

        ScreenView viewA = world.open(first, alex);
        counterOf("A", 7);
        RecordingSurface setAside = world.getRenderer().surfaceOf(viewA);
        navigate(1);
        assertEquals(1, underneath.get());
        assertTrue(viewA.isSuspended());

        setAside.forgetApplies();
        counters.get("A").set(9);
        world.advanceTicks(5);

        assertTrue(setAside.getApplies().isEmpty(), "a screen with nowhere to write does not write: "
                + setAside.getApplies());
        assertEquals(7, setAside.getSlot(0).getItem().getAmount(), "the window it used to have still shows the "
                + "count from before it was set aside");

        click(1);
        assertFalse(world.click(viewA, 1, ClickGesture.PRIMARY, ClickKind.NOTHING),
                "a click routed to the screen set aside is refused outright");

        assertEquals(1, underneath.get(), "the two screens both have a button on slot 1, and only the one "
                + "the player is looking at answered");
        assertEquals(1, onTop.get());

        navigate(2);

        assertFalse(viewA.isSuspended(), "picked up again");
        assertSame(viewA, current());
        RecordingSurface revived = world.getRenderer().surfaceOf(viewA);
        assertNotSame(setAside, revived, "on a window it has never written into");
        assertEquals(9, onScreen().getAmount(), "so what changed while it was away is drawn now, and the "
                + "state that changed is the one it kept");
        assertFalse(revived.getApplies().isEmpty(), "the whole screen is redrawn, since nothing on this window "
                + "was ever the framework's");
        closeWhatIsOpen();
    }

    @Test
    void aScreenSetAsideTellsItsWindowSoARendererThatIsNotTheChestCanLetGo() {
        ScreenView view = world.open(screen("A", "minecraft:iron_ingot"), alex);
        RecordingSurface window = world.getRenderer().surfaceOf(view);
        assertFalse(window.isSuspended());

        assertTrue(ScreenNavigation.suspend(view));

        assertTrue(window.isSuspended(), "the window learns the view went aside - a page or a mod screen has no inventory "
                + "close to hear it from");
        assertNull(window.getClosedWith(), "set aside is not closed: the view comes back on a new window");
    }

    @Test
    void onlyTheScreenOnDisplayCanBeSetAsideAndOnlyASetAsideOneComesBack() {
        ScreenView view = world.open(screen("A", "minecraft:iron_ingot"), alex);

        assertTrue(ScreenNavigation.suspend(view));
        assertTrue(view.isSuspended());
        assertFalse(ScreenNavigation.suspend(view), "a screen already set aside has no window left to give up");

        assertTrue(ScreenNavigation.resume(view));
        assertFalse(view.isSuspended());
        assertFalse(ScreenNavigation.resume(view), "and one that has a window has nothing to be given back");

        ScreenView other = world.open(screen("B", "minecraft:gold_ingot"), alex);

        assertTrue(view.isClosed(), "an open outside the chain took the window from the first screen");
        assertFalse(ScreenNavigation.suspend(view), "and a screen that is gone cannot be set aside");
        assertTrue(ScreenNavigation.suspend(other));

        alex.online(false);

        assertFalse(ScreenNavigation.resume(other), "there is nobody left to give it back to");
        assertTrue(other.isSuspended(), "so it stays set aside rather than pretending it is on screen");

        //a suspended screen has no window to close; forgetting the chain is what releases it
        ScreenNavigation.discard(alex.getUniqueId());
        assertTrue(other.isClosed());
    }

    @Test
    void closingTheWindowMidChainLeavesNoScreenNoTaskAndNobodyWaiting() {
        Screen<Layout> third = screen("C", "minecraft:emerald");
        Screen<Layout> second = opens(screen("B", "minecraft:gold_ingot"), third);
        Screen<Layout> first = opens(screen("A", "minecraft:iron_ingot"), second);
        //something for each screen to keep alive, so "no task left" is a measurement and not a vacuum
        first.addComponent(component -> component.every(2).render(slots -> { }));
        second.addComponent(component -> component.every(2).render(slots -> { }));
        third.addComponent(component -> component.every(2).render(slots -> { }));

        ScreenView viewA = world.open(first, alex);
        navigate(1);
        ScreenView viewB = current();
        navigate(1);
        ScreenView viewC = current();
        assertTrue(world.getTicks().getActiveTaskCount() >= 3, "three screens, three clocks");

        world.getRenderer().surfaceOf(viewC).playerCloses();
        world.advanceTicks(3);

        assertTrue(viewA.isClosed(), "the whole chain goes, not just the window that was on screen");
        assertTrue(viewB.isClosed());
        assertTrue(viewC.isClosed());
        assertNull(viewA.getViewer(), "and none of them keeps the player");
        assertNull(viewB.getViewer());
        assertEquals(0, Screens.getOpenCount());
        assertEquals(0, world.getTicks().getActiveTaskCount(), "nor a clock of its own");
        assertTrue(answers.get(0).isCancelled(), "and each step is told its answer never came");
        assertTrue(answers.get(1).isCancelled());
    }

}
