package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.cooldown.Cooldowns;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.CustomVariables;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.logic.audit.LogicTrace;
import br.com.finalcraft.evernifecore.logic.core.CoreLogic;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.everydatabase.Storages;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One plugin that owns logic, for a test: its plugin data, an in-memory storage that counts calls, contexts already
 * carrying its {@link Caller}, players that are online on the platform, the core's stock types registered, and the
 * clock and scheduler of {@link TestClock}, and the lines a trace of a player would send ({@link #trace}). Needs a platform with {@code ignoringPlatformRegistrations()} and
 * {@code mainThreadInline()}, {@code platformProviderId(...)} for a type that names platforms or capabilities (and
 * {@code playerState()} for what reads a player's state); {@code offWorldThread()} proves the dispatch of effects.
 *
 * <pre>{@code
 * try (LogicWorld shop = LogicWorld.open("FakeShop", tempDir)) {
 *     TestFPlayerSender alex = shop.player("Alex");
 *     GateResult paid = gate.pay(shop.context(alex).build());
 *     shop.advance(Duration.ofMinutes(10));
 *     shop.runDue();
 * }
 * }</pre>
 */
public final class LogicWorld implements AutoCloseable {

    private static final Object STOCK_LOCK = new Object();
    private static int stockUsers;
    private static boolean stockRegisteredHere;

    private final ECPluginData owner;
    private final String ownerName;
    private final ScriptedStorage backend;
    private final ECStorage storage;
    private final List<FPlayer> players = new ArrayList<>();
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
    private final Map<Class<?>, List<?>> recorded = new HashMap<>();
    private final List<String> traceWatchers = new ArrayList<>();
    private boolean closed;

    private LogicWorld(String ownerName, Path dataFolder) {
        this.ownerName = ownerName;
        TestClock.acquire();
        acquireStock(dataFolder);
        IECPluginExtractor previous = EverNifeCore.getProviders().getBaseProvider().provideOrNull(IECPluginExtractor.class);
        this.owner = Plugins.fakePluginData(ownerName, dataFolder.toFile());
        if (previous != null) {
            //the owner is built; whoever answered for classes before keeps answering for them
            EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class, previous);
        }
        this.backend = ScriptedStorage.wrapping(Storages.createInMemory());
        backend.init().join();
        this.storage = backend.asECStorage();
    }

    /** Opens the logic of a fake plugin named {@code ownerName} whose data folder is {@code dataFolder}. */
    public static LogicWorld open(String ownerName, Path dataFolder) {
        return new LogicWorld(ownerName, dataFolder);
    }

    public ECPluginData getOwner() {
        return owner;
    }

    /** The owner's storage, in memory. */
    public ECStorage getStorage() {
        return storage;
    }

    /** The backend under {@link #getStorage()}, for {@code callsTo("save")}. */
    public ScriptedStorage getBackend() {
        return backend;
    }

    /** The owner's own variables, kept in {@link #getStorage()}: {@code Variables.custom(owner, storage)}. */
    public CustomVariables customVariables() {
        return Variables.custom(owner, storage);
    }

    /** The owner's counters of region, world and group owners, kept in {@link #getStorage()}: {@code Cooldowns.of(owner, storage)}. */
    public Cooldowns cooldowns() {
        return Cooldowns.of(owner, storage);
    }

    /** A caller of this world's plugin at {@code origin}. */
    public Caller caller(String origin) {
        return Caller.of(owner, origin);
    }

    /** A context builder with this world's caller (origin {@code test}) and {@code player}; null builds a context without a player. */
    public ExecutionContext.Builder context(FPlayer player) {
        return ExecutionContext.builder(caller("test")).player(player);
    }

    /** A context builder with this world's caller (origin {@code test}) and no player. */
    public ExecutionContext.Builder context() {
        return context(null);
    }

    /**
     * A player named {@code name} standing at 0, 64, 0 of {@code world} and online on the platform: what
     * {@code getPlayer(uuid)} and {@code getOnlinePlayers()} answer. Leaves the platform's list at {@link #close()}.
     */
    public TestFPlayerSender player(String name) {
        TestFPlayerSender player = new TestFPlayerSender(name).at("world", 0, 64, 0);
        players.add(player);
        refreshOnline();
        return player;
    }

    /** Takes {@code player} off the platform's online list and marks it offline, as a quit does. */
    public void quit(TestFPlayerSender player) {
        player.online(false);
        players.remove(player);
        refreshOnline();
    }

    private void refreshOnline() {
        platform().setOnlinePlayers(players);
    }

    private static TestPlatform platform() {
        return (TestPlatform) EverNifeCore.getPlatform();
    }

    /** What the clock answers - fixed until {@link #advance(Duration)}. */
    public Instant now() {
        return TestClock.now();
    }

    /** Moves the shared clock forward; nothing runs until {@link #runDue()}. */
    public void advance(Duration by) {
        TestClock.advance(by);
    }

    /** Runs every due task on the shared scheduler, once; how many ran. */
    public int runDue() {
        return TestClock.runDue();
    }

    /** Every event of {@code type} posted on the global bus since the first call, in order; the list is live. */
    @SuppressWarnings("unchecked")
    public synchronized <T extends IECEvent> List<T> recorded(Class<T> type) {
        List<T> seen = (List<T>) recorded.get(type);
        if (seen == null) {
            List<T> created = new CopyOnWriteArrayList<>();
            subscriptions.add(ECEventBus.global().subscribe(type, created::add));
            recorded.put(type, created);
            seen = created;
        }
        return seen;
    }

    /**
     * The lines {@code /ecore logic trace <player> <this plugin>} would send an admin from now until {@link #close()}:
     * every condition, cost, effect and multiplier run for {@code player} by this world's callers, the caller in front.
     * The list is live.
     */
    public synchronized List<String> trace(FPlayer player) {
        List<String> lines = new CopyOnWriteArrayList<>();
        String watcher = "logic-world:" + ownerName + ":" + player.getUniqueId();
        LogicTrace.start(watcher, player.getUniqueId(), ownerName, lines::add);
        traceWatchers.add(watcher);
        return lines;
    }

    /** Stops the traces, unsubscribes the recorders, takes this world's players offline, closes the storage and forgets the plugin and its types. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        traceWatchers.forEach(LogicTrace::stop);
        subscriptions.forEach(ECEventSubscription::unsubscribe);
        if (!players.isEmpty()) {
            players.clear();
            refreshOnline();
        }
        Cooldowns.closeOwnedBy(owner);
        CustomVariables.closeOwnedBy(owner);
        storage.close().join();
        Plugins.forget(ownerName);
        releaseStock();
        TestClock.release();
    }

    private static void acquireStock(Path dataFolder) {
        synchronized (STOCK_LOCK) {
            if (stockUsers++ == 0 && !Conditions.registry().find("evernifecore:all").isPresent()) {
                CoreLogic.registerAll(Plugins.fakeCore(dataFolder.resolve("evernifecore").toFile()));
                stockRegisteredHere = true;
            }
        }
    }

    private static void releaseStock() {
        synchronized (STOCK_LOCK) {
            if (--stockUsers == 0 && stockRegisteredHere) {
                stockRegisteredHere = false;
                Plugins.forget(Plugins.CORE); //its types leave every registry with it
            }
        }
    }
}
