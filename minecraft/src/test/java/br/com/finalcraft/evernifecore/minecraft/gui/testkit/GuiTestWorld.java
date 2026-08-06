package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.view.CloseReason;
import br.com.finalcraft.evernifecore.minecraft.gui.view.DetachedViews;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiViews;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemRuntime;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitRegistries;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.Inventory;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * A whole gui running with no server: the framework's own classes, its real listener and its real
 * {@code GuiViews} registry, over a container that records writes and a clock a test moves by hand.
 *
 * <pre>{@code
 * try (GuiTestWorld world = GuiTestWorld.install()) {
 *     PlayerDouble player = world.newPlayer("Steve");
 *     GuiView view = world.open(Gui.of(3).icon(13, icon), player);
 *     world.advanceTicks(1);
 *     assertEquals(1, world.getSurface().getWriteCount());
 * }
 * }</pre>
 *
 * <p>Three things are stubbed and nothing else. The <b>main thread</b> is whichever thread installed
 * this world, so the commit's refusal to write from anywhere else stays testable. The <b>item
 * factory</b> answers no metadata, which makes two stacks compare by type and amount - enough to
 * tell icons apart and exactly what the buffer's diff reads. And <b>createInventory</b> answers a
 * {@link SurfaceDouble} wearing the platform's interface, so a window opened through the framework's
 * real entry point still records every write.</p>
 *
 * <p>There are two ways in, and which one a test wants depends on whether it needs the clock.
 * {@link #open(Gui, PlayerDouble)} goes through {@code GuiViews}, so the view is registered and
 * clicks reach it - but its tasks go to the server scheduler, which no headless JVM has.
 * {@link #openDetached(Gui, PlayerDouble)} hands the view a {@link SchedulerDouble} instead, and
 * then nothing happens until {@link #advanceTicks(long)} says a tick passed.</p>
 */
public final class GuiTestWorld implements AutoCloseable {

    private static final AtomicInteger UNIQUE_SUFFIX = new AtomicInteger();

    //what the stubbed server below actually is: a modern version that answers none of the questions
    //an item asks. Naming it here is what makes every reduced answer a named refusal instead of a
    //surprise, and it is why the item path needs no reflection to be testable
    private static final ItemRuntime RUNTIME = ItemRuntime.of(MCDetailedVersion.v1_21_R1);

    private final ECoreTestWorld platformWorld;
    private final String pluginName;
    private final SchedulerDouble scheduler = new SchedulerDouble();
    private final GuiEventBus events = new GuiEventBus();
    private final ClickSimulator clicks = new ClickSimulator(events);
    private final List<SurfaceDouble> createdSurfaces = new ArrayList<>();
    private final List<GuiView> detachedViews = new ArrayList<>();
    private final Thread mainThread = Thread.currentThread();
    private final Server previousServer;

    private boolean closed = false;

    private GuiTestWorld(Path dataFolder) {
        this.pluginName = "GuiTest_" + UNIQUE_SUFFIX.incrementAndGet();
        this.platformWorld = Platforms.lenient().install()
                .withPluginExtractor(Plugins.fake(pluginName, dataFolder.toFile()));

        //EverNifeCore.getLog() is what the gui reports a refused open or a broken handler through,
        //and only the real bootstrap ever sets it
        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(new Object());
        EverNifeCore.instance.onLoaderInstantiate(ecPluginData);

        this.previousServer = Bukkit.getServer();
        setBukkitServer(buildServer());
        ItemEngine.install(RUNTIME);
    }

    public static GuiTestWorld install(Path dataFolder) {
        return new GuiTestWorld(dataFolder);
    }

    /** The runtime this world's item engine stands on, for a test that wants to name what it lost. */
    public ItemRuntime getItemRuntime() {
        return RUNTIME;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The pieces a test drives
    // -----------------------------------------------------------------------------------------------------------------

    public SchedulerDouble getScheduler() {
        return scheduler;
    }

    public ClickSimulator getClicks() {
        return clicks;
    }

    public GuiEventBus getEvents() {
        return events;
    }

    /** The installed platform double, for the assertions a test makes on what was logged. */
    public TestPlatform getPlatform() {
        return platformWorld.platform();
    }

    public PlayerDouble newPlayer(String name) {
        return new PlayerDouble(name, events);
    }

    /** Every container the framework asked the server for, oldest first - a title change adds one. */
    public List<SurfaceDouble> getCreatedSurfaces() {
        return new ArrayList<>(createdSurfaces);
    }

    /** The container of the most recently created window. */
    public SurfaceDouble getSurface() {
        return createdSurfaces.get(createdSurfaces.size() - 1);
    }

    public void advanceTicks(long ticks) {
        scheduler.advanceTicks(ticks);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Opening
    // -----------------------------------------------------------------------------------------------------------------

    /** Opens {@code gui} and hands back the view, failing the test if the open was refused. */
    public GuiView open(Gui gui, PlayerDouble player) {
        try {
            return gui.open(player.asPlayer()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError("The gui was expected to open for [" + player.getName() + "]", e);
        }
    }

    /** Opens without demanding success - for the tests that are about the refusal. */
    public CompletableFuture<GuiView> tryOpen(Gui gui, PlayerDouble player) {
        return gui.open(player.asPlayer());
    }

    /**
     * Opens {@code gui} straight onto this world's doubles: a fresh {@link SurfaceDouble} and the
     * manual clock, instead of a Bukkit container and the server scheduler.
     *
     * <p>This is the form a test about rendering wants, because the clock is the test's. The view is
     * not registered with the framework, so a test about clicks or about the shutdown sweep uses
     * {@link #open(Gui, PlayerDouble)} instead - see {@link DetachedViews}.</p>
     */
    public GuiView openDetached(Gui gui, PlayerDouble player) {
        SurfaceDouble surface = new SurfaceDouble(gui.getType().sizeOf(gui.getRows()));
        createdSurfaces.add(surface);
        GuiView view = DetachedViews.open(gui, player.asPlayer(), surface, scheduler);
        detachedViews.add(view);
        return view;
    }

    /**
     * Opens detached and then makes the view reachable: the player gets the window, and the framework
     * registers the view, so the real listener routes clicks to it.
     *
     * <p>This is the only way to click a screen whose container is not a Bukkit one. The framework's
     * own open registers a view but always builds a Bukkit container; {@link #openDetached} hands over
     * a {@link SurfaceDouble} but registers nothing.</p>
     */
    public GuiView openDetachedAndRegistered(Gui gui, PlayerDouble player) {
        GuiView view = openDetached(gui, player);
        player.asPlayer().openInventory(getSurface().asInventory());
        DetachedViews.register(player.asPlayer(), view);
        return view;
    }

    /**
     * Closes a detached view. The close event a player would send has nowhere to arrive - the view is
     * not in the registry that routes it - so the teardown is asked for directly.
     */
    public void closeDetached(GuiView view) {
        detachedViews.remove(view);
        DetachedViews.release(view, CloseReason.PLAYER_CLOSED);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The stubbed server
    // -----------------------------------------------------------------------------------------------------------------

    private Server buildServer() {
        ItemFactory itemFactory = Doubles.of(ItemFactory.class)
                .on("getItemMeta", args -> null)
                //ItemStack asks this with two metas; with none ever produced, both are null and equal
                .on("equals", args -> args.length == 2 && args[0] == args[1])
                .build();

        return Doubles.of(Server.class)
                .on("isPrimaryThread", args -> Thread.currentThread() == mainThread)
                .on("getItemFactory", args -> itemFactory)
                .on("getRegistry", args -> BukkitRegistries.forType((Class<?>) args[0]))
                .on("getScheduler", args -> scheduler.asBukkitScheduler())
                .on("getLogger", args -> Logger.getLogger(pluginName))
                .on("createInventory", args -> createInventory(args))
                .build();
    }

    private Inventory createInventory(Object[] args) {
        int size = args[1] instanceof InventoryType
                ? ((InventoryType) args[1]).getDefaultSize()
                : (Integer) args[1];
        SurfaceDouble surface = new SurfaceDouble(size);
        createdSurfaces.add(surface);
        return surface.asInventory();
    }

    private static void setBukkitServer(Server server) {
        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Bukkit.server could not be replaced; the stub server this "
                    + "test rig is built on cannot be installed.", e);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (GuiView view : detachedViews) {
            DetachedViews.release(view, CloseReason.SHUTDOWN);
        }
        GuiViews.closeAll();
        ItemEngine.uninstall();
        setBukkitServer(previousServer);
        ECPluginManager.removePluginData(pluginName);
        platformWorld.close();
    }

}
