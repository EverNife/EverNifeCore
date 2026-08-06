package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a screen starts costing something, and when it stops.
 *
 * <p>Both edges are leaks if they are wrong. An open the server refused must leave nothing behind -
 * no registration, no task, no retained player - and a screen that closed must stop paying for
 * itself immediately rather than whenever a collector gets round to it. In between, a periodic
 * redraw has to run on the period it was given: {@code every(20)} once a second, not ten times.</p>
 */
class GuiLifecycleTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private static Icon stone() {
        return Icon.of(new ItemStack(Material.STONE));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  An open that was refused
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anOpenForSomebodyWhoLeftLeavesNothingBehind() {
        PlayerDouble gone = world.newPlayer("Steve").online(false);

        CompletableFuture<GuiView> future = world.tryOpen(Gui.of(3).icon(0, stone()), gone);

        ExecutionException failure = assertThrows(ExecutionException.class, future::get);
        assertTrue(failure.getCause().getMessage().contains("no longer online"), failure.getCause().getMessage());
        assertEquals(0, GuiViews.getOpenCount());
        assertEquals(0, world.getScheduler().getActiveTaskCount());
        assertTrue(world.getCreatedSurfaces().isEmpty(), "not even a container was asked for");
    }

    @Test
    void anOpenTheServerRefusedRegistersNothingAndSchedulesNothing() {
        PlayerDouble player = world.newPlayer("Steve").refuseOpens(true);
        Gui gui = Gui.of(3).component(component -> {
            component.every(20);
            component.render(slots -> slots.icon(4, stone()));
        });

        CompletableFuture<GuiView> future = world.tryOpen(gui, player);

        ExecutionException failure = assertThrows(ExecutionException.class, future::get);
        assertTrue(failure.getCause().getMessage().contains("did not open the window"),
                failure.getCause().getMessage());
        assertEquals(0, GuiViews.getOpenCount());
        assertEquals(0, world.getScheduler().getActiveTaskCount(),
                "the periodic redraw belongs to a view that was never confirmed");
        assertEquals(0, world.getSurface().getWriteCount(), "and nothing was drawn into the container either");
        assertNull(player.getOpenView());
    }

    @Test
    void aRefusalIsLoggedSoIgnoringTheFutureIsStillSafe() {
        PlayerDouble player = world.newPlayer("Steve").refuseOpens(true);

        world.tryOpen(Gui.of(3).icon(0, stone()), player);

        assertTrue(loggedMessages().stream().anyMatch(line -> line.contains("did not open")),
                "a plugin that ignores the future still gets told: " + loggedMessages());
    }

    private List<String> loggedMessages() {
        return world.getPlatform().getLoggedMessages();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Nothing is scheduled globally
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aScreenWithNothingToAnimateSchedulesNothingAtAll() {
        world.openDetached(Gui.of(3).icon(0, stone()).icon(8, stone()), world.newPlayer("Steve"));

        world.advanceTicks(50);

        assertEquals(0, world.getScheduler().getActiveTaskCount(),
                "a screen nobody is changing has no reason to wake up");
    }

    @Test
    void aClosedScreenLeavesNoTaskRunning() {
        AtomicInteger redraws = new AtomicInteger();
        Gui gui = Gui.of(3)
                .icon(4, Icon.of(new ItemStack(Material.CLOCK)).every(20).render(icon -> redraws.incrementAndGet()))
                .component(component -> {
                    component.watch(redraws::get);
                    component.render(slots -> slots.icon(0, stone()));
                });
        GuiView view = world.openDetached(gui, world.newPlayer("Steve"));

        world.advanceTicks(40);
        assertTrue(world.getScheduler().getPeriodicTaskCount() >= 2,
                "while it is open there is a redraw task and a watch poll");
        int redrawsWhileOpen = redraws.get();

        world.closeDetached(view);

        assertEquals(0, world.getScheduler().getActiveTaskCount(), "zero screens open, zero tasks");
        world.advanceTicks(200);
        assertEquals(redrawsWhileOpen, redraws.get(), "an icon nobody is looking at costs nothing");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The period is the period
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void everyTwentyTicksIsOncePerSecondAndNotOncePerTick() {
        AtomicInteger redraws = new AtomicInteger();
        Icon clock = Icon.of(new ItemStack(Material.CLOCK)).every(20).render(icon -> redraws.incrementAndGet());
        world.openDetached(Gui.of(3).icon(4, clock), world.newPlayer("Steve"));

        world.advanceTicks(100);

        assertEquals(5, redraws.get());
    }

    @Test
    void aComponentRedrawsOnItsOwnPeriod() {
        AtomicInteger renders = new AtomicInteger();
        Gui gui = Gui.of(3).component(component -> {
            component.every(20);
            component.render(slots -> {
                renders.incrementAndGet();
                slots.icon(4, stone());
            });
        });
        world.openDetached(gui, world.newPlayer("Steve"));

        assertEquals(1, renders.get(), "drawn once as it opened");

        //each period marks the component dirty and the pass that redraws it runs on the tick after
        world.advanceTicks(101);

        assertEquals(6, renders.get());
    }

    @Test
    void anAnimatedIconIsCopiedPerViewerSoOneScreenCannotRestyleAnother() {
        //the stack is replaced rather than edited: Icon.amount goes through the item factory, which
        //reads NBT through the server that a unit test does not have
        Icon clock = Icon.of(new ItemStack(Material.CLOCK, 1))
                .every(20)
                .render(icon -> icon.setItemStack(new ItemStack(Material.CLOCK, icon.getItemStack().getAmount() + 1)));
        Gui gui = Gui.of(3).icon(4, clock);

        world.openDetached(gui, world.newPlayer("Steve"));
        world.advanceTicks(60);
        world.openDetached(gui, world.newPlayer("Alex"));

        assertEquals(1, clock.getItemStack().getAmount(), "the declared icon is never the one being animated");
        assertEquals(1, world.getCreatedSurfaces().get(1).getItem(4).getAmount(),
                "the second viewer starts from the declaration, not from the first viewer's frame");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Closing
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void closingUnregistersTheScreenAndLetsThePlayerGo() {
        PlayerDouble player = world.newPlayer("Steve");
        GuiView view = world.open(Gui.of(3).icon(0, stone()), player);

        assertEquals(1, GuiViews.getOpenCount());
        assertNotNull(view.getViewer());

        player.asPlayer().closeInventory();

        assertTrue(view.isClosed());
        assertEquals(0, GuiViews.getOpenCount());
        assertNull(view.getViewer(), "the Player is released there and then, not left to a collector");
        assertEquals("Steve", view.getViewerName(), "who it was is still readable - the reference is not");
    }

    @Test
    void disconnectingRunsOnCloseToo() {
        List<CloseReason> reasons = new ArrayList<>();
        PlayerDouble player = world.newPlayer("Steve");
        world.open(Gui.of(3).icon(0, stone()).onClose(context -> reasons.add(context.getReason())), player);

        world.getEvents().fireQuit(player.asPlayer());

        assertEquals(Arrays.asList(CloseReason.DISCONNECTED), reasons);
        assertEquals(0, GuiViews.getOpenCount());
    }

    @Test
    void theShutdownSweepClosesEveryScreenAndRunsEachOnClose() {
        List<CloseReason> reasons = new ArrayList<>();
        List<Material> returned = new ArrayList<>();
        Gui gui = Gui.of(3)
                .icon(0, stone())
                .onClose(context -> {
                    reasons.add(context.getReason());
                    for (ItemStack item : context.getContents(Slots.of(0))) {
                        returned.add(item == null ? Material.AIR : item.getType());
                    }
                });

        world.open(gui, world.newPlayer("Steve"));
        world.open(gui, world.newPlayer("Alex"));
        assertEquals(2, GuiViews.getOpenCount());

        GuiViews.closeAll();

        assertEquals(0, GuiViews.getOpenCount());
        assertEquals(Arrays.asList(CloseReason.SHUTDOWN, CloseReason.SHUTDOWN), reasons);
        assertEquals(Arrays.asList(Material.STONE, Material.STONE), returned,
                "the container is still readable inside onClose, which is what makes it the place to "
                        + "hand back whatever the screen was holding");
    }

    @Test
    void aSecondCloseChangesNothing() {
        AtomicInteger closes = new AtomicInteger();
        PlayerDouble player = world.newPlayer("Steve");
        world.open(Gui.of(3).icon(0, stone()).onClose(context -> closes.incrementAndGet()), player);

        player.asPlayer().closeInventory();
        GuiViews.closeAll();
        world.getEvents().fireQuit(player.asPlayer());

        assertEquals(1, closes.get(), "the teardown is idempotent, so onClose fires once");
    }

}
