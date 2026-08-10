package br.com.finalcraft.evernifecore.minecraft.gui.component;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.GuiLayout;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.IconData;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An icon that says when it is alive, and the screen finding that out on its own.
 *
 * <p>The interesting party in each of these is the one nobody talks to: no click, no {@code refresh()},
 * no state anyone on the screen was told about - just a value out in the plugin that moves, and a slot
 * that has to notice. A screen that only notices because some other icon of it happens to be polling is
 * a screen whose correctness depends on its neighbours.</p>
 *
 * <p>The cadence is asserted on its own screen every time, because a look is what these tests measure:
 * one wheel serves the whole view, so an icon polling once a tick next to one polling once a second is
 * the only proof that the interval belongs to the watch and not to the view.</p>
 */
class IconVisibilityTest {

    @TempDirNobodyCleans
    Path tempDir;

    private GuiTestWorld world;
    private PlayerDouble player;

    @GuiLayout(title = "TheMarket", rows = 3)
    public static class MarketLayout extends LayoutBase {

        @IconData(slot = {0})
        public Icon BUY = Icon.of(new ItemStack(Material.EMERALD));

        @IconData(slot = {1})
        public Icon BID = Icon.of(new ItemStack(Material.GOLD_INGOT));
    }

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
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

    // -----------------------------------------------------------------------------------------------------------------
    //  The default: the screen looks by itself
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anIconAloneOnTheScreenStillFollowsAChangeMadeOutsideIt() {
        AtomicBoolean sold = new AtomicBoolean(false);
        Gui<MarketLayout> gui = Gui.of(MarketLayout.class);
        gui.icon(l -> l.BUY).visibleWhen(() -> !sold.get());

        world.openDetached(gui, player);
        SurfaceDouble surface = world.getSurface();

        assertEquals(Material.EMERALD, surface.getItem(0).getType());

        sold.set(true); //another player bought it: nothing on this screen was told, and nothing refreshes it
        world.advanceTicks(2);

        assertNull(surface.getItem(0), "the only icon on the screen has to be enough to arm the look - "
                + "with the poll coming from a neighbouring states(...) icon, this slot would still be for sale");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The cadence, which belongs to the watch
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void twoIconsOnOneScreenKeepTheirOwnCadenceOffTheSameWheel() {
        AtomicBoolean sold = new AtomicBoolean(false);
        AtomicBoolean closed = new AtomicBoolean(false);
        Gui<MarketLayout> gui = Gui.of(MarketLayout.class);
        gui.icon(l -> l.BUY).visibleWhen(() -> !sold.get());
        gui.icon(l -> l.BID).visibleWhen(() -> !closed.get(), 20);

        GuiView view = world.openDetached(gui, player);
        SurfaceDouble surface = world.getSurface();

        assertEquals(1, world.getScheduler().getPeriodicTaskCount(),
                "two watches at two cadences, and one task for both of them");

        sold.set(true);
        closed.set(true);
        world.advanceTicks(2);

        assertNull(surface.getItem(0), "the one that looks every tick is already gone");
        assertEquals(Material.GOLD_INGOT, surface.getItem(1).getType(),
                "and the one that looks once a second has not looked yet");

        world.advanceTicks(18); //tick 20: the look happens here, and every write is the pass after it

        assertEquals(Material.GOLD_INGOT, surface.getItem(1).getType());

        world.advanceTicks(1);

        assertNull(surface.getItem(1), "one interval later the slower one agrees");
        assertEquals(1, world.getScheduler().getPeriodicTaskCount(), "still one task");
        assertNull(view.getIconAt(1), "and the click follows the picture, as ever");
    }

    @Test
    void aCadenceOfZeroPutsNothingOnTheClockAndWaitsToBeTold() {
        AtomicBoolean sold = new AtomicBoolean(false);
        Gui<MarketLayout> gui = Gui.of(MarketLayout.class);
        gui.icon(l -> l.BUY).visibleWhen(() -> !sold.get(), 0);

        GuiView view = world.openDetached(gui, player);
        SurfaceDouble surface = world.getSurface();

        assertEquals(0, world.getScheduler().getPeriodicTaskCount(), "nothing was put on the clock");

        sold.set(true);
        world.advanceTicks(60);

        assertEquals(Material.EMERALD, surface.getItem(0).getType(),
                "a cadence of zero is a promise the screen will not look, three seconds included");

        view.refresh();
        world.advanceTicks(1);

        assertNull(surface.getItem(0), "and being told still works");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The expensive predicate: declared trigger instead of a poll
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aPredicateThatIsNeverPolledRunsOnceForEachStateItDependsOn() {
        MutableState<Integer> blacklistVersion = State.of(0);
        AtomicBoolean blacklisted = new AtomicBoolean(false);
        AtomicInteger asked = new AtomicInteger();

        Gui<MarketLayout> gui = Gui.of(MarketLayout.class);
        gui.icon(l -> l.BUY)
                .visibleWhen(() -> {
                    asked.incrementAndGet();
                    return !blacklisted.get();
                }, 0)
                .dependsOn(blacklistVersion);

        world.openDetached(gui, player);
        SurfaceDouble surface = world.getSurface();

        assertEquals(1, asked.get(), "the render asked, and nothing else did - a watch would have asked too");
        assertEquals(Material.EMERALD, surface.getItem(0).getType());

        blacklisted.set(true);
        blacklistVersion.set(1); //the plugin says the list moved; the predicate itself is never polled
        world.advanceTicks(1);

        assertNull(surface.getItem(0));
        assertEquals(2, asked.get(), "one look per change, on the render the state asked for");
        assertEquals(0, world.getScheduler().getPeriodicTaskCount());

        blacklistVersion.set(2);
        world.advanceTicks(1);

        assertEquals(3, asked.get(), "and the next change costs exactly one more");
    }

    @Test
    void aDeclaredDependencyRedrawsTheIconWithoutAnyPredicateAtAll() {
        MutableState<Boolean> lit = State.of(false);
        Gui<MarketLayout> gui = Gui.of(MarketLayout.class);
        gui.icon(l -> l.BUY).visibleWhen(lit::get, 0).dependsOn(lit);

        world.openDetached(gui, player);
        SurfaceDouble surface = world.getSurface();

        assertNull(surface.getItem(0), "a predicate answering false hides the icon from the first render");

        lit.set(true);
        world.advanceTicks(1);

        assertNotNull(surface.getItem(0), "the state the icon depends on is the whole trigger");
    }

}
