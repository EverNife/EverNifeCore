package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.ECBaseProvider;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.Currency;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.logic.DeferredEffects;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestinations;
import br.com.finalcraft.evernifecore.logic.delivery.InventoryDestination;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventExceptionHandler;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.EventBuses;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;
import br.com.finalcraft.evernifecore.eventbus.ECNativeAudience;
import br.com.finalcraft.evernifecore.notify.Notifier;
import br.com.finalcraft.evernifecore.waypoint.Waypoints;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.spawn.ISpawnProvider;
import br.com.finalcraft.evernifecore.group.IGroupGlowProvider;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.spawn.SpawnPoints;
import br.com.finalcraft.evernifecore.time.ECScheduler;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A platform installed in the global provider registry, plus the way to undo it.
 *
 * <p>The registry is process-wide and Gradle reuses the JVM across test classes, so an install
 * that never unwinds is shared state: the next class inherits a platform it did not ask for, and
 * the failure shows up as order-dependent. Closing this world puts back exactly what was there
 * before - including "nothing at all".</p>
 *
 * <p>The global event bus is held to the same rule, strictly: a listener watch or a native audience
 * that was not on it at install and is still on it at {@link #close()} fails the close, by name. So does a
 * {@link PlatformNotConfiguredException} that reached the bus's exception handler: core code that only reports a failure
 * (a subscriber, a login claim) would otherwise turn a missing builder call into a log line and a green test.</p>
 */
public final class ECoreTestWorld implements AutoCloseable {

    /**
     * The periodic flush would race scripted repositories and save-count assertions; tests drive
     * flushes explicitly. Set before any platform install, since PlayerController reads it at boot.
     */
    private static final String PERIODIC_FLUSH_PROPERTY = "evernifecore.playerdata.periodic-flush";

    /**
     * Comma-separated {@link ECDebugModule} names a world switches on at install, with the core's debug
     * ({@code ./gradlew test -Dec.debug=LOGIC}, which the build hands to the test JVM).
     */
    private static final String DEBUG_PROPERTY = "ec.debug";

    private final TestPlatform platform;
    private final IPlatform previousPlatform;
    private final IECPluginExtractor previousExtractor;
    private final boolean hadExtractor;
    private final IEconomyProvider previousEconomy;
    private final boolean hadEconomy;
    private final ISpawnProvider previousSpawn;
    private final IGroupGlowProvider previousGlow;
    private final ECScheduler.MainThread previousMainThread;
    private final ECPluginData previousCore;
    private final Set<ECListenerWatch> watchesBefore = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<String> audiencesBefore = new ArrayList<>();
    private final List<ECPluginData> currencyOwners = new ArrayList<>();
    private final List<String> reportedRefusals = new CopyOnWriteArrayList<>();
    private final RefusalWatch refusalWatch = new RefusalWatch();
    private ECEventExceptionHandler busHandlerBefore;
    private boolean closed = false;
    private ECPluginData inventoryOwner;

    private ECoreTestWorld(TestPlatform platform, IPlatform previousPlatform, IECPluginExtractor previousExtractor, IEconomyProvider previousEconomy) {
        this.platform = platform;
        this.previousPlatform = previousPlatform;
        this.previousExtractor = previousExtractor;
        this.hadExtractor = previousExtractor != null;
        this.previousEconomy = previousEconomy;
        this.hadEconomy = previousEconomy != null;
        this.previousSpawn = EverNifeCore.getProviders().getSpawnOrNull();
        this.previousGlow = EverNifeCore.getProviders().getBaseProvider().provideOrNull(IGroupGlowProvider.class);
        this.previousMainThread = ECScheduler.getMainThread();
        this.previousCore = EverNifeCore.getEcPluginData();
        this.watchesBefore.addAll(ECEventBus.global().getListenerWatches());
        for (ECNativeAudience audience : ECEventBus.global().getNativeAudiences()) {
            this.audiencesBefore.add(audience.name());
        }
    }

    static ECoreTestWorld install(TestPlatform platform) {
        System.setProperty(PERIODIC_FLUSH_PROPERTY, "false");

        ECBaseProvider providers = EverNifeCore.getProviders().getBaseProvider();
        ECoreTestWorld world = new ECoreTestWorld(
                platform,
                providers.provideOrNull(IPlatform.class),
                providers.provideOrNull(IECPluginExtractor.class),
                providers.provideOrNull(IEconomyProvider.class)
        );

        providers.register(IPlatform.class, platform);
        world.busHandlerBefore = EventBuses.installExceptionHandler(ECEventBus.global(), world.refusalWatch);
        restore(providers, IGroupGlowProvider.class, platform.testGlow, platform.testGlow != null);
        ECScheduler.setMainThread(platform.mainThread());
        ensureCorePluginData(platform);
        enableDebugModules();
        SpawnPoints.resetWarning(); //a new world is a new boot: its first spawn fallback reports again
        if (platform.testInventory != null) {
            //the core already registered owns it; else a core of its own, out of the registry, that no forget reaches
            ECPluginData registered = ECPluginManager.getECPluginsMap().get(Plugins.CORE);
            if (registered != null) {
                world.inventoryOwner = registered;
            } else {
                world.inventoryOwner = Plugins.fakeCore(tempFolder("ecore-test-inventory"));
                Plugins.forget(Plugins.CORE);
            }
            DeliveryDestinations.register(world.inventoryOwner, platform.testInventory);
        }
        return world;
    }

    /** The installed double, for the assertions a test makes on captured commands, messages or shutdowns. */
    public TestPlatform platform() {
        return platform;
    }

    /**
     * Installs a plugin extractor for the lifetime of this world. The previous one is restored on
     * {@link #close()} together with the platform - both are global, and both used to leak.
     */
    public ECoreTestWorld withPluginExtractor(IECPluginExtractor extractor) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class, extractor);
        return this;
    }

    /**
     * Installs an economy for the lifetime of this world - {@link Economies} builds the usual ones.
     * Without this call there is no economy at all: {@code Currencies.money()} answers {@code UNAVAILABLE}.
     */
    public ECoreTestWorld withEconomy(IEconomyProvider economy) {
        EverNifeCore.getProviders().getBaseProvider().register(IEconomyProvider.class, economy);
        return this;
    }

    /**
     * Registers {@code currency} under {@code owner} for the lifetime of this world - {@link Economies#currency} builds
     * one; {@link #close()} takes out every currency {@code owner} registered.
     */
    public ECoreTestWorld withCurrency(ECPluginData owner, Currency currency, String... aliases) {
        Currencies.register(owner, currency, aliases);
        currencyOwners.add(owner);
        return this;
    }

    /**
     * Registers a spawn plugin's {@link ISpawnProvider} for the lifetime of this world. Without this call there is
     * none, and {@code SpawnPoints} falls back to the world spawn ({@code Platforms.worldSpawn}).
     */
    public ECoreTestWorld withSpawn(ISpawnProvider spawn) {
        EverNifeCore.getProviders().getBaseProvider().register(ISpawnProvider.class, spawn);
        return this;
    }

    /**
     * Makes {@code core} the core's own plugin data ({@code EverNifeCore.getEcPluginData()}, the owner of
     * {@code EverNifeCore.getLog()} and of the core's debug modules) for the lifetime of this world; {@code null}
     * clears it. {@link #close()} hands back whatever was there at install.
     */
    public ECoreTestWorld withCorePluginData(ECPluginData core) {
        EverNifeCore.instance.onLoaderInstantiate(core);
        return this;
    }

    /**
     * Puts the providers back and checks the global event bus.
     *
     * @throws AssertionError naming every listener watch and native audience opened since the install and never closed
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        //the region singletons (EventSourceUsage's watch on region enter/leave, the movement tracker) and any manager a
        //test left open live for the whole JVM; the engine shuts them down the way the core does, before the check
        RegionManager.closeAll();
        //a notify queue writes to the platform it started on: stopped (waiting for a tick in flight) before that platform goes
        Notifier.get().clearAll();
        Waypoints.get().clearAll();
        for (ECPluginData owner : currencyOwners) {
            Currencies.unregisterAll(owner);
        }
        if (inventoryOwner != null) {
            DeliveryDestinations.unregister(inventoryOwner, InventoryDestination.ID);
        }
        Deliveries.closeAll();
        //a sink a test registered by hand outlives Deliveries: put the dropping one back for the next test
        DeferredEffects.register(null);
        List<String> leaked = leftOnTheGlobalBus();

        ECBaseProvider providers = EverNifeCore.getProviders().getBaseProvider();
        restore(providers, IPlatform.class, previousPlatform, previousPlatform != null);
        restore(providers, IECPluginExtractor.class, previousExtractor, hadExtractor);
        restore(providers, IEconomyProvider.class, previousEconomy, hadEconomy);
        restore(providers, ISpawnProvider.class, previousSpawn, previousSpawn != null);
        restore(providers, IGroupGlowProvider.class, previousGlow, previousGlow != null);
        ECScheduler.setMainThread(previousMainThread);
        //a plugin data lent to the core outlives its test otherwise, and its debug log asks the next test's platform
        EverNifeCore.instance.onLoaderInstantiate(previousCore);
        SpawnPoints.resetWarning();
        if (ECEventBus.global().getExceptionHandler() == refusalWatch) {
            EventBuses.installExceptionHandler(ECEventBus.global(), busHandlerBefore);
        }

        if (!leaked.isEmpty()) {
            throw new AssertionError("The test left " + leaked + " on the global event bus. Whoever opened it closes it"
                    + " before the world closes: ECListenerWatch.stop() for a watch, ECEventBus.removeNativeAudience(name)"
                    + " for an audience - otherwise the next test class inherits it and its gate counts change.");
        }
        if (!reportedRefusals.isEmpty()) {
            throw new AssertionError("The platform double refused a call this test never configured, and the core only"
                    + " reported it (nothing failed where it happened): " + reportedRefusals);
        }
    }

    /**
     * The global bus's handler for the life of this world: notes every {@link PlatformNotConfiguredException} a failure
     * carries, then hands the failure on unchanged to the handler that was there before.
     */
    private final class RefusalWatch implements ECEventExceptionHandler {

        @Override
        public void onSubscriberFailure(ECEventSubscription<?> subscription, IECEvent event, Throwable failure) {
            note(failure);
            busHandlerBefore.onSubscriberFailure(subscription, event, failure);
        }

        @Override
        public void onWatchFailure(ECListenerWatch watch, boolean onFirstListener, Throwable failure) {
            note(failure);
            busHandlerBefore.onWatchFailure(watch, onFirstListener, failure);
        }

        private void note(Throwable failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
                if (cause instanceof PlatformNotConfiguredException) {
                    reportedRefusals.add(cause.getMessage());
                    return;
                }
            }
        }

        @Override
        public String toString() {
            return "ECoreTestWorld refusal watch over " + busHandlerBefore;
        }
    }

    /**
     * A move through {@code FCEcoUtil} is attributed to the core, whose plugin data only the real bootstrap sets: a world
     * gives it one when no test did, and takes it out of the plugin registry at once so a later {@code Plugins.fakeCore}
     * still builds its own. It is built on a lenient double, so a strict world that configured no logging still gets
     * one; its log goes to stdout.
     */
    private static void ensureCorePluginData(TestPlatform platform) {
        if (EverNifeCore.getEcPluginData() != null) {
            return;
        }
        ECBaseProvider providers = EverNifeCore.getProviders().getBaseProvider();
        providers.register(IPlatform.class, Platforms.lenient().build());
        try {
            EverNifeCore.instance.onLoaderInstantiate(Plugins.fakeCore(tempFolder("ecore-test-core")));
            Plugins.forget(Plugins.CORE);
        } finally {
            providers.register(IPlatform.class, platform);
        }
    }

    /** Switches on the core's debug and the modules {@link #DEBUG_PROPERTY} names; after the core's config was read, which would switch them off again. */
    private static void enableDebugModules() {
        String names = System.getProperty(DEBUG_PROPERTY, "").trim();
        if (names.isEmpty()) {
            return;
        }
        EverNifeCore.getEcPluginData().setDebugEnabled(true);
        for (String name : names.split(",")) {
            ECDebugModule.valueOf(name.trim().toUpperCase(Locale.ROOT)).setEnabled(true);
        }
    }

    private static File tempFolder(String prefix) {
        try {
            return Files.createTempDirectory(prefix).toFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> leftOnTheGlobalBus() {
        List<String> leaked = new ArrayList<>();
        for (ECListenerWatch watch : ECEventBus.global().getListenerWatches()) {
            if (!watchesBefore.contains(watch)) {
                leaked.add("watch " + watch);
            }
        }
        for (ECNativeAudience audience : ECEventBus.global().getNativeAudiences()) {
            if (!audiencesBefore.contains(audience.name())) {
                leaked.add("audience '" + audience.name() + "'");
            }
        }
        return leaked;
    }

    private static <T> void restore(ECBaseProvider providers, Class<T> type, T previous, boolean hadPrevious) {
        if (hadPrevious) {
            providers.register(type, previous);
        } else {
            providers.unregister(type);
        }
    }
}
