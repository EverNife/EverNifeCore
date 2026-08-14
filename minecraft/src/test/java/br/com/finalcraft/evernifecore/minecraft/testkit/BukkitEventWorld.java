package br.com.finalcraft.evernifecore.minecraft.testkit;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.minecraft.eventbus.McBukkitAudience;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimplePluginManager;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The Bukkit event pipeline with no server behind it: Bukkit's own {@link SimplePluginManager} over
 * a {@link Server} double whose only real opinion is which thread is the main one, plus EverNifeCore
 * enabled on top of it - the platform double installed and {@link McBukkitAudience} mirroring the
 * global bus into this server.
 *
 * <pre>{@code
 * try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
 *     List<SampleEvent> heard = new ArrayList<>();
 *     world.listen(SampleEvent.class, heard::add);
 *     ECEventBus.global().post(new SampleEvent());
 *     assertEquals(1, heard.size());
 * }
 * }</pre>
 *
 * <p>The plugin manager is the real one on purpose. Everything this rig is about lives in it and in
 * the classes it drives - the HandlerList a subclass resolves up its hierarchy, the executor's type
 * filter, the refusal to fire an event on the wrong thread - and a double would be a second
 * implementation of exactly the behaviour under test.</p>
 *
 * <p>It touches process-wide state that outlives a test class: {@code Bukkit.server}, the shared
 * HandlerList of every EC event and the global bus. All three are put back by {@link #close()}, so
 * this world only ever runs inside a try-with-resources.</p>
 */
public final class BukkitEventWorld implements AutoCloseable {

    private static final AtomicInteger UNIQUE_SUFFIX = new AtomicInteger();

    private final String pluginName;
    private final Thread mainThread = Thread.currentThread();
    private final ECoreTestWorld platformWorld;
    private final Server previousServer;
    private final Plugin plugin;
    private final SimplePluginManager pluginManager;
    private final McBukkitAudience audience = new McBukkitAudience();
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();

    private boolean closed = false;

    private BukkitEventWorld(Path dataFolder) {
        this.pluginName = "BukkitEventTest_" + UNIQUE_SUFFIX.incrementAndGet();
        this.platformWorld = Platforms.lenient().install()
                .withPluginExtractor(Plugins.fake(pluginName, dataFolder.toFile()));

        //EverNifeCore.getLog() is what the audience reports a refused post through, and only the real
        //bootstrap ever sets it
        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(new Object());
        EverNifeCore.instance.onLoaderInstantiate(ecPluginData);

        this.plugin = buildPlugin();
        AtomicReference<PluginManager> manager = new AtomicReference<>();
        this.previousServer = Bukkit.getServer();
        setBukkitServer(buildServer(manager));
        //after the server is in place: the manager keeps a reference to it and hands it every thread question
        this.pluginManager = new SimplePluginManager(Bukkit.getServer(), null);
        manager.set(pluginManager);

        ECEventBus.global().addNativeAudience(audience);
    }

    /** A server with EverNifeCore enabled on it: the audience is already mirroring into it. */
    public static BukkitEventWorld install(Path dataFolder) {
        return new BukkitEventWorld(dataFolder);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The pieces a test drives
    // -----------------------------------------------------------------------------------------------------------------

    /** The audience this world installed, for the assertions a test makes on the gate itself. */
    public McBukkitAudience getAudience() {
        return audience;
    }

    /** The enabled plugin every registration here is made in the name of. */
    public Plugin getPlugin() {
        return plugin;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    /** The installed platform double, for the assertions a test makes on what was logged. */
    public TestPlatform getPlatform() {
        return platformWorld.platform();
    }

    /**
     * Registers a Bukkit listener for {@code type} the way a plugin does, executor included: the
     * parameter type is the filter, because the HandlerList an EC event resolves to is shared by the
     * whole family and Bukkit's own generated executor drops what the handler did not ask for.
     */
    public <T extends Event> void listen(Class<T> type, Consumer<? super T> handler) {
        listen(type, EventPriority.NORMAL, handler);
    }

    /** As {@link #listen(Class, Consumer)}, at the given Bukkit priority. */
    public <T extends Event> void listen(Class<T> type, EventPriority priority, Consumer<? super T> handler) {
        Listener listener = new Listener() {
        };
        EventExecutor executor = (ignoredListener, event) -> {
            if (!type.isInstance(event)) {
                return;
            }
            handler.accept(type.cast(event));
        };
        pluginManager.registerEvent(type, listener, priority, executor, plugin);
    }

    /** Subscribes on the global bus for the length of this world - the local phase of a post. */
    public <T extends IECEvent> void subscribe(Class<T> eventType, Consumer<? super T> handler) {
        subscriptions.add(ECEventBus.global().subscribe(eventType, handler));
    }

    /**
     * Runs {@code body} on a worker, so {@code Bukkit.isPrimaryThread()} answers false while it does -
     * the rig calls only the installing thread main. Whatever it threw is rethrown here.
     */
    public void offTheMainThread(Runnable body) {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable throwable) {
                thrown.set(throwable);
            }
        }, "bukkit-event-world-worker");
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the worker", interrupted);
        }
        if (thrown.get() != null) {
            throw new AssertionError("The worker failed", thrown.get());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The stubbed server
    // -----------------------------------------------------------------------------------------------------------------

    private Server buildServer(AtomicReference<PluginManager> manager) {
        //the manager is built against this server, so it can only be handed over after both exist
        return Doubles.of(Server.class)
                .on("isPrimaryThread", args -> Thread.currentThread() == mainThread)
                .on("getPluginManager", args -> manager.get())
                .on("getLogger", args -> Logger.getLogger(pluginName))
                //a real server always names a release, and the core resolves MCVersion out of it the
                //first time anything asks
                .on("getBukkitVersion", args -> "1.21.1-R0.1-SNAPSHOT")
                .on("getVersion", args -> "1.21.1-R0.1-SNAPSHOT")
                .build();
    }

    private Plugin buildPlugin() {
        //getDescription is what the plugin manager names in the log when a listener throws
        PluginDescriptionFile description = new PluginDescriptionFile(pluginName, "1.0.0", pluginName);
        return Doubles.of(Plugin.class)
                .on("isEnabled", args -> Boolean.TRUE)
                .on("getName", args -> pluginName)
                .on("getDescription", args -> description)
                .on("getLogger", args -> Logger.getLogger(pluginName))
                .build();
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

        ECEventBus.global().removeNativeAudience(McBukkitAudience.NAME);
        for (ECEventSubscription<?> subscription : subscriptions) {
            subscription.unsubscribe();
        }
        //the EC events share ONE HandlerList for the whole JVM: a registration left behind would keep
        //answering in the next test class, and the audience's gate would read it as "someone listens"
        HandlerList.unregisterAll(plugin);
        setBukkitServer(previousServer);
        ECPluginManager.removePluginData(pluginName);
        platformWorld.close();
    }

}
