package br.com.finalcraft.evernifecore.minecraft.testkit;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.eventbus.McBukkitAudience;
import br.com.finalcraft.evernifecore.minecraft.eventbus.McForgeAudience;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McPlatform;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The Bukkit event pipeline with no server behind it: Bukkit's own {@link SimplePluginManager} over
 * a {@link Server} double that answers two things for real - which thread is the main one, and a
 * scheduler that queues instead of ticking - plus EverNifeCore enabled on top of it, with the
 * platform double installed and the plugin's own pair of audiences - {@link McBukkitAudience} and
 * {@link McForgeAudience} - on the global bus.
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
    private final ECPluginData ecPluginData;
    private final SimplePluginManager pluginManager;
    private final McBukkitAudience audience = new McBukkitAudience();
    private final McForgeAudience forgeAudience = new McForgeAudience();
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
    private final List<ECListener> ecListeners = new ArrayList<>();
    private final List<Runnable> scheduledTasks = new ArrayList<>();
    private final BukkitScheduler scheduler = buildScheduler();

    private McPlatform realPlatform;
    private boolean closed = false;

    private BukkitEventWorld(Path dataFolder) {
        this.pluginName = "BukkitEventTest_" + UNIQUE_SUFFIX.incrementAndGet();
        this.platformWorld = Platforms.lenient().install()
                .withPluginExtractor(Plugins.fake(pluginName, dataFolder.toFile()));
        installRealListenerRegistration(platformWorld.platform());

        this.plugin = buildPlugin();
        //the plugin data wraps the Bukkit plugin itself, which is what the platform casts it back to
        //when it registers a listener. EverNifeCore.getLog() comes with it, and only the real
        //bootstrap ever sets that.
        this.ecPluginData = ECPluginManager.getOrCreateECorePluginData(plugin);
        EverNifeCore.instance.onLoaderInstantiate(ecPluginData);

        AtomicReference<PluginManager> manager = new AtomicReference<>();
        this.previousServer = Bukkit.getServer();
        setBukkitServer(buildServer(manager));
        //after the server is in place: the manager keeps a reference to it and hands it every thread question
        this.pluginManager = new SimplePluginManager(Bukkit.getServer(), null);
        manager.set(pluginManager);

        //both, in the order the plugin registers them: a test about either one is also a test about
        //what the other does while it is there
        ECEventBus.global().addNativeAudience(audience);
        ECEventBus.global().addNativeAudience(forgeAudience);
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

    /** The Forge audience this world installed alongside it - inert here, since no test JVM is a hybrid. */
    public McForgeAudience getForgeAudience() {
        return forgeAudience;
    }

    /** The enabled plugin every registration here is made in the name of. */
    public Plugin getPlugin() {
        return plugin;
    }

    /** That same plugin as the core knows it - what a platform call takes. */
    public ECPluginData getPluginData() {
        return ecPluginData;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    /**
     * Registers {@code listener} the way {@code ECListener.register} does: the platform first - which
     * is Bukkit's own {@code @EventHandler} scan plus the programmatic {@code @ECEventHandler} route -
     * and then the event bus, which takes the handlers naming an {@code IECEvent}.
     *
     * <p>Whatever the platform refuses is rethrown, so a test can be about the refusal.</p>
     */
    public void registerECListener(ECListener listener) {
        realPlatform().registerECListener(ecPluginData, listener);
        ECEventBus.global().register(listener);
        ecListeners.add(listener);
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
     * Runs every task scheduled for a later tick, in the order they were scheduled, and forgets them.
     * The tick is the test's to give: code that defers work can be watched deciding, and the state it
     * reads can change in between.
     */
    public void runScheduledTasks() {
        List<Runnable> due = new ArrayList<>(scheduledTasks);
        scheduledTasks.clear();
        for (Runnable task : due) {
            task.run();
        }
    }

    /** How many tasks are waiting for {@link #runScheduledTasks()}. */
    public int getScheduledTaskCount() {
        return scheduledTasks.size();
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

    /**
     * Points the platform's two ECListener hooks at the real Bukkit ones and leaves every other answer
     * the double's. {@code ECListener.register} reaches the server through the platform, and the double
     * ignores listeners - in a world whose whole subject is where a registration lands.
     */
    private void installRealListenerRegistration(TestPlatform lenient) {
        IPlatform routed = (IPlatform) Proxy.newProxyInstance(IPlatform.class.getClassLoader(),
                new Class<?>[]{IPlatform.class}, (proxy, method, args) -> {
                    if ("registerECListener".equals(method.getName())) {
                        realPlatform().registerECListener((ECPluginData) args[0], (ECListener) args[1]);
                        return null;
                    }
                    if ("unregisterECListener".equals(method.getName())) {
                        realPlatform().unregisterECListener((ECListener) args[0]);
                        return null;
                    }
                    try {
                        return method.invoke(lenient, args);
                    } catch (InvocationTargetException thrownByTheDouble) {
                        throw thrownByTheDouble.getCause();
                    }
                });
        //what platformWorld.close() puts back is the platform from before it installed, so this goes with it
        EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class, routed);
    }

    /** Built on first use: its static initializer reads the server, which this world installs later. */
    private McPlatform realPlatform() {
        if (realPlatform == null) {
            realPlatform = new McPlatform();
        }
        return realPlatform;
    }

    private Server buildServer(AtomicReference<PluginManager> manager) {
        //the manager is built against this server, so it can only be handed over after both exist
        return Doubles.of(Server.class)
                .on("isPrimaryThread", args -> Thread.currentThread() == mainThread)
                .on("getPluginManager", args -> manager.get())
                .on("getScheduler", args -> scheduler)
                .on("getLogger", args -> Logger.getLogger(pluginName))
                //a real server always names a release, and the core resolves MCVersion out of it the
                //first time anything asks
                .on("getBukkitVersion", args -> "1.21.1-R0.1-SNAPSHOT")
                .on("getVersion", args -> "1.21.1-R0.1-SNAPSHOT")
                .build();
    }

    /**
     * A scheduler that only queues: what a {@code runTaskLater} asked for waits for
     * {@link #runScheduledTasks()}. Anything else it is asked to do answers the neutral value, which
     * is enough for a caller that ignores the task it gets back.
     */
    private BukkitScheduler buildScheduler() {
        BukkitTask task = Doubles.of(BukkitTask.class).build();
        return Doubles.of(BukkitScheduler.class)
                .on("runTaskLater", args -> {
                    scheduledTasks.add((Runnable) args[1]);
                    return task;
                })
                .build();
    }

    private Plugin buildPlugin() {
        //getDescription is what the plugin manager names in the log when a listener throws
        PluginDescriptionFile description = new PluginDescriptionFile(pluginName, "1.0.0", pluginName);
        PluginLoader loader = Doubles.of(PluginLoader.class)
                .on("createRegisteredListeners", args -> scanEventHandlers((Listener) args[0], (Plugin) args[1]))
                .build();
        return Doubles.of(Plugin.class)
                .on("isEnabled", args -> Boolean.TRUE)
                .on("getName", args -> pluginName)
                .on("getDescription", args -> description)
                .on("getPluginLoader", args -> loader)
                .on("getLogger", args -> Logger.getLogger(pluginName))
                .build();
    }

    /**
     * What a server's plugin loader hands {@code registerEvents}: one registration per public
     * {@code @EventHandler} method, over an executor that filters by the parameter type.
     */
    private static Map<Class<? extends Event>, Set<RegisteredListener>> scanEventHandlers(Listener listener, Plugin plugin) {
        Map<Class<? extends Event>, Set<RegisteredListener>> found = new HashMap<>();
        for (Method method : listener.getClass().getMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null || method.getParameterCount() != 1
                    || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                continue;
            }
            Class<? extends Event> eventType = method.getParameterTypes()[0].asSubclass(Event.class);
            EventExecutor executor = (ignoredListener, event) -> {
                if (!eventType.isInstance(event)) {
                    return;
                }
                try {
                    method.invoke(listener, event);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("The @EventHandler " + method + " could not be called", e);
                }
            };
            found.computeIfAbsent(eventType, key -> new LinkedHashSet<>())
                    .add(new RegisteredListener(listener, executor, annotation.priority(), plugin,
                            annotation.ignoreCancelled()));
        }
        return found;
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
        ECEventBus.global().removeNativeAudience(McForgeAudience.NAME);
        for (ECEventSubscription<?> subscription : subscriptions) {
            subscription.unsubscribe();
        }
        for (ECListener listener : ecListeners) {
            ECEventBus.global().unregister(listener);
        }
        //the EC events share ONE HandlerList for the whole JVM: a registration left behind would keep
        //answering in the next test class, and the audience's gate would read it as "someone listens"
        HandlerList.unregisterAll(plugin);
        setBukkitServer(previousServer);
        ECPluginManager.removePluginData(pluginName);
        platformWorld.close();
    }

}
