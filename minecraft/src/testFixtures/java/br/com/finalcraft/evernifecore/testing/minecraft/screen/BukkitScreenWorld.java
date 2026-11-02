package br.com.finalcraft.evernifecore.testing.minecraft.screen;

import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemProbe;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemRuntime;
import br.com.finalcraft.evernifecore.minecraft.screen.ChestScreenRenderer;
import br.com.finalcraft.evernifecore.minecraft.screen.prompt.BukkitChatPromptChannel;
import br.com.finalcraft.evernifecore.screen.prompt.PromptRegistry;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.minecraft.BukkitRegistries;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import br.com.finalcraft.evernifecore.testing.minecraft.ItemMetaDouble;
import br.com.finalcraft.evernifecore.testing.minecraft.ItemWorld;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * The chest renderer running with no server: the real {@link ChestScreenRenderer}, its real listener and the real
 * {@code Screens} registry, over containers that record writes and a clock a test moves by hand.
 *
 * <pre>{@code
 * try (BukkitScreenWorld world = BukkitScreenWorld.install(dir)) {
 *     PlayerDouble player = world.newPlayer("Steve");
 *     ScreenView view = world.open(Screen.of(3).icon(13, icon), player);
 *     world.advanceTicks(1);
 *     assertEquals(1, world.getSurface().getWriteCount());
 * }
 * }</pre>
 *
 * <p>Three things are stubbed and nothing else. The <b>main thread</b> is whichever thread installed this world, so
 * the commit's refusal to write from anywhere else stays testable. The <b>item factory</b> answers no metadata, which
 * makes two stacks compare by type and amount. And <b>createInventory</b> answers a {@link SurfaceDouble} wearing the
 * platform's interface, so a window opened through the renderer's real entry point still records every write.</p>
 *
 * <p>{@link #install} brings its own lenient platform: everything a chest asks of a server comes through Bukkit, and
 * the platform is only what the core reaches for on the way past; {@link #install(Path, UnaryOperator)} adjusts it
 * ({@code Platforms::capturingCommands} for a button that runs a command as the player). {@link #over(ScreenWorld, Path)}
 * lays the same server over a {@link ScreenWorld} instead - the form {@code RendererConformance} runs on - and the screen world's clock and
 * prompt answers then move this server's.</p>
 *
 * <p>Published in {@code evernifecore-minecraft-tests}, so a plugin's test opens its own screens on the real chest.
 * {@link #installWithItemMetadata} is the form for a test that reads the names and lore the chest drew:</p>
 *
 * <pre>{@code
 * try (BukkitScreenWorld world = BukkitScreenWorld.installWithItemMetadata(dir)) {
 *     PlayerDouble player = world.newPlayer("Steve");
 *     Screen<Layout> screen = Screen.of(1);
 *     screen.icon(0, Icon.of("minecraft:paper").displayName("§eHello"));
 *     world.open(screen, player);
 *     world.advanceTicks(1);
 *     assertEquals("§eHello", world.getSurface().getItem(0).getItemMeta().getDisplayName());
 * }
 * }</pre>
 *
 * <p>It replaces the Bukkit server and the item engine for its life: one at a time, in a try-with-resources, never
 * beside an open {@link ItemWorld}.</p>
 */
public final class BukkitScreenWorld implements AutoCloseable {

    //a modern version that answers none of the questions an item asks: every reduced answer is a named refusal
    private static final ItemRuntime SILENT_ITEMS = ItemRuntime.of(MCDetailedVersion.v1_21_R1);

    /** The same server, answering metadata: the runtime a screen made of text has to stand on. */
    private static final ItemRuntime SPEAKING_ITEMS = ItemRuntime.of(MCDetailedVersion.v1_21_R1,
            ItemProbe.ITEM_META, ItemProbe.ENCHANT_REGISTRY);

    private final ECoreTestWorld platformWorld;
    private final String pluginName;
    private final ECPluginData ecPluginData;
    private final ItemRuntime runtime;
    private final SchedulerDouble scheduler = new SchedulerDouble();
    private final ScreenEventBus events = new ScreenEventBus();
    private final ClickSimulator clicks = new ClickSimulator(events);
    private final List<SurfaceDouble> createdSurfaces = new ArrayList<>();
    private final Map<UUID, PlayerDouble> players = new ConcurrentHashMap<>();
    private final Thread mainThread = Thread.currentThread();
    private final Server previousServer;
    private final ChestScreenRenderer renderer = new ChestScreenRenderer();

    private boolean closed = false;

    private BukkitScreenWorld(Path dataFolder, ItemRuntime runtime, UnaryOperator<Platforms> platform, ScreenWorld screens) {
        //the core's own name: the chest renderer is keyed evernifecore:chest, and a type is registered by the plugin its
        //key names
        this.pluginName = "EverNifeCore";
        this.runtime = runtime;
        if (screens == null) {
            this.platformWorld = platform.apply(Platforms.lenient()).install()
                    .withPluginExtractor(Plugins.fake(pluginName, dataFolder.toFile()));
            //EverNifeCore.getLog() is what a screen reports a refused open or a broken handler through, and only the
            //real bootstrap ever sets it
            this.ecPluginData = ECPluginManager.getOrCreateECorePluginData(new Object());
            platformWorld.withCorePluginData(ecPluginData);
        } else {
            this.platformWorld = null;
            this.ecPluginData = screens.getCore();
            //the recording renderer would serve every viewer: under conformance the chest is the one that draws
            Screens.unregisterRenderers(screens.getOwner());
            screens.prompts().clear();
            screens.tickingWith(scheduler::advanceTicks).answeringPromptsWith(this::answerPrompt);
        }

        this.previousServer = Bukkit.getServer();
        setBukkitServer(buildServer());
        ItemEngine.install(runtime);
        //the platform's own config codecs - item stacks, locations - a layout file is unreadable without
        ItemWorld.registerConfigTypes();
        Screens.registerRenderer(ecPluginData, renderer);
        PromptRegistry.get().register(ecPluginData, new BukkitChatPromptChannel());
    }

    /** A chest over items that carry no metadata - two stacks then compare by type and amount alone. */
    public static BukkitScreenWorld install(Path dataFolder) {
        return install(dataFolder, UnaryOperator.identity());
    }

    /**
     * {@link #install(Path)} over the lenient platform adjusted by {@code platform}: {@code Platforms::capturingCommands}
     * for a button that makes the player run a command, {@code p -> p.parsingWith(...)} for a placeholder.
     */
    public static BukkitScreenWorld install(Path dataFolder, UnaryOperator<Platforms> platform) {
        return new BukkitScreenWorld(dataFolder, SILENT_ITEMS, platform, null);
    }

    /** A chest over items that remember a name, a lore and a flag - what a test about text needs. */
    public static BukkitScreenWorld installWithItemMetadata(Path dataFolder) {
        return installWithItemMetadata(dataFolder, UnaryOperator.identity());
    }

    /** {@link #installWithItemMetadata(Path)} over the lenient platform adjusted by {@code platform}. */
    public static BukkitScreenWorld installWithItemMetadata(Path dataFolder, UnaryOperator<Platforms> platform) {
        return new BukkitScreenWorld(dataFolder, SPEAKING_ITEMS, platform, null);
    }

    /**
     * The chest laid over {@code screens}: no platform of its own, the recording renderer taken out, the screen world's
     * {@code advanceTicks} moving this server's clock and its {@code answerPrompt} typing in this server's chat. Close
     * this world before {@code screens}.
     */
    public static BukkitScreenWorld over(ScreenWorld screens, Path dataFolder) {
        return new BukkitScreenWorld(dataFolder, SILENT_ITEMS, UnaryOperator.identity(), screens);
    }

    public ItemRuntime getItemRuntime() {
        return runtime;
    }

    public ChestScreenRenderer getRenderer() {
        return renderer;
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

    public ScreenEventBus getEvents() {
        return events;
    }

    /** The installed platform double, for the assertions a test makes on what was logged. */
    public TestPlatform getPlatform() {
        if (platformWorld == null) {
            throw new IllegalStateException("This chest was laid over a ScreenWorld; read its platform there.");
        }
        return platformWorld.platform();
    }

    /**
     * Installs {@code economy} for the life of this world, the way {@link ECoreTestWorld#withEconomy} does - a price, a
     * shop's sale, {@code Currencies.money()} all reach it; {@link #close()} puts the previous provider back.
     */
    public BukkitScreenWorld withEconomy(IEconomyProvider economy) {
        if (platformWorld == null) {
            throw new IllegalStateException("This chest was laid over a ScreenWorld; give that world the economy.");
        }
        platformWorld.withEconomy(economy);
        return this;
    }

    /** The core's plugin data, for the setup a real enable would have done and this one does not. */
    public ECPluginData getPluginData() {
        return ecPluginData;
    }

    public PlayerDouble newPlayer(String name) {
        PlayerDouble player = new PlayerDouble(name, events, this::windowOver);
        players.put(player.getUniqueId(), player);
        return player;
    }

    /** The player the server knows under {@code uniqueId}, made on the spot for a viewer nobody created here. */
    public PlayerDouble playerOf(UUID uniqueId) {
        return players.computeIfAbsent(uniqueId,
                id -> new PlayerDouble("Viewer-" + id.toString().substring(0, 8), id, events, this::windowOver));
    }

    /** Types {@code text} in chat as every player waiting on a prompt - how a conformance answer arrives. */
    private void answerPrompt(String text) {
        for (PlayerDouble player : players.values()) {
            events.typeInChat(player.asPlayer(), text);
        }
    }

    /**
     * The container this world's server names once it opened a window over {@code inventory}: a fresh face of the same
     * storage, the way a real one answers its own wrapper rather than the object it was handed.
     */
    private Inventory windowOver(Inventory inventory) {
        for (SurfaceDouble surface : createdSurfaces) {
            if (surface.isBackedBy(inventory)) {
                return surface.newFace();
            }
        }
        return inventory;
    }

    /** Every container the renderer asked the server for, oldest first - a title change adds one. */
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

    /** Opens {@code screen} and hands back the view, failing the test if the open was refused. */
    public ScreenView open(Screen<?> screen, PlayerDouble player, Object... args) {
        try {
            return tryOpen(screen, player, args).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError("The screen was expected to open for [" + player.getName() + "]", e);
        }
    }

    /** Opens without demanding success - for the tests that are about the refusal. */
    public CompletableFuture<ScreenView> tryOpen(Screen<?> screen, PlayerDouble player, Object... args) {
        return Screens.open(player.asFPlayer(), screen, args);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The stubbed server
    // -----------------------------------------------------------------------------------------------------------------

    private Server buildServer() {
        boolean metadata = runtime.has(ItemProbe.ITEM_META);
        ItemFactory itemFactory = Doubles.of(ItemFactory.class)
                .on("getItemMeta", args -> metadata ? ItemMetaDouble.create() : null)
                .on("isApplicable", args -> true)
                .on("asMetaFor", args -> args[0])
                .on("updateMaterial", args -> args[1])
                //ItemStack asks this with two metas; with none ever produced, both are null and equal
                .on("equals", args -> args.length == 2
                        && (args[0] == args[1] || (args[0] != null && args[0].equals(args[1]))))
                .build();

        //Material.matchMaterial(name, legacy) asks the server to map a legacy name even when there is nothing to map
        UnsafeValues unsafe = Doubles.of(UnsafeValues.class)
                .on("fromLegacy", args -> null)
                .build();

        //a listener the core registers on first use - the chat one - asks for this before it will hear anything;
        //nothing here routes events, which ScreenEventBus does by calling the listener directly
        PluginManager pluginManager = Doubles.of(PluginManager.class).build();

        //a real server always names a release, and the core resolves MCVersion out of it the first time anything asks
        String bukkitVersion = runtime.getVersion() == null
                ? "1.21.1-R0.1-SNAPSHOT"
                : runtime.getVersion().getReleaseFamily() + ".0-R0.1-SNAPSHOT";

        return Doubles.of(Server.class)
                .on("isPrimaryThread", args -> Thread.currentThread() == mainThread)
                .on("getBukkitVersion", args -> bukkitVersion)
                .on("getVersion", args -> bukkitVersion)
                .on("getItemFactory", args -> itemFactory)
                .on("getUnsafe", args -> unsafe)
                .on("getRegistry", args -> BukkitRegistries.forType((Class<?>) args[0]))
                .on("getScheduler", args -> scheduler.asBukkitScheduler())
                .on("getPluginManager", args -> pluginManager)
                .on("getLogger", args -> Logger.getLogger(pluginName))
                .on("createInventory", args -> createInventory(args))
                .on("getPlayer", args -> args[0] instanceof UUID ? playerOf((UUID) args[0]).asPlayer() : null)
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
            throw new IllegalStateException("Bukkit.server could not be replaced; the stub server this test rig is "
                    + "built on cannot be installed.", e);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Screens.closeAll();
        Screens.registry().unregister(ecPluginData, renderer.getId());
        PromptRegistry.get().unregisterAll(ecPluginData);
        ItemEngine.uninstall();
        setBukkitServer(previousServer);
        if (platformWorld != null) {
            ECPluginManager.removePluginData(pluginName);
            platformWorld.close();
        }
    }

}
