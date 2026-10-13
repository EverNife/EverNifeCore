package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.spawn.ISpawnProvider;
import br.com.finalcraft.evernifecore.spawn.SpawnPoints;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.ECBaseProvider;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;
import br.com.finalcraft.evernifecore.eventbus.ECNativeAudience;
import br.com.finalcraft.evernifecore.region.RegionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * A platform installed in the global provider registry, plus the way to undo it.
 *
 * <p>The registry is process-wide and Gradle reuses the JVM across test classes, so an install
 * that never unwinds is shared state: the next class inherits a platform it did not ask for, and
 * the failure shows up as order-dependent. Closing this world puts back exactly what was there
 * before - including "nothing at all".</p>
 *
 * <p>The global event bus is held to the same rule, strictly: a listener watch or a native audience
 * that was not on it at install and is still on it at {@link #close()} fails the close, by name.</p>
 */
public final class ECoreTestWorld implements AutoCloseable {

    /**
     * The periodic flush would race scripted repositories and save-count assertions; tests drive
     * flushes explicitly. Set before any platform install, since PlayerController reads it at boot.
     */
    private static final String PERIODIC_FLUSH_PROPERTY = "evernifecore.playerdata.periodic-flush";

    private final TestPlatform platform;
    private final IPlatform previousPlatform;
    private final IECPluginExtractor previousExtractor;
    private final boolean hadExtractor;
    private final IEconomyProvider previousEconomy;
    private final boolean hadEconomy;
    private final ISpawnProvider previousSpawn;
    private final Set<ECListenerWatch> watchesBefore = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<String> audiencesBefore = new ArrayList<>();
    private boolean closed = false;

    private ECoreTestWorld(TestPlatform platform, IPlatform previousPlatform, IECPluginExtractor previousExtractor, IEconomyProvider previousEconomy) {
        this.platform = platform;
        this.previousPlatform = previousPlatform;
        this.previousExtractor = previousExtractor;
        this.hadExtractor = previousExtractor != null;
        this.previousEconomy = previousEconomy;
        this.hadEconomy = previousEconomy != null;
        this.previousSpawn = EverNifeCore.getProviders().getSpawnOrNull();
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
        SpawnPoints.resetWarning(); //a new world is a new boot: its first spawn fallback reports again
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
     * Without this call there is no economy at all, and every {@code FCEcoUtil} call throws.
     */
    public ECoreTestWorld withEconomy(IEconomyProvider economy) {
        EverNifeCore.getProviders().getBaseProvider().register(IEconomyProvider.class, economy);
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
        List<String> leaked = leftOnTheGlobalBus();

        ECBaseProvider providers = EverNifeCore.getProviders().getBaseProvider();
        restore(providers, IPlatform.class, previousPlatform, previousPlatform != null);
        restore(providers, IECPluginExtractor.class, previousExtractor, hadExtractor);
        restore(providers, IEconomyProvider.class, previousEconomy, hadEconomy);
        restore(providers, ISpawnProvider.class, previousSpawn, previousSpawn != null);
        SpawnPoints.resetWarning();

        if (!leaked.isEmpty()) {
            throw new AssertionError("The test left " + leaked + " on the global event bus. Whoever opened it closes it"
                    + " before the world closes: ECListenerWatch.stop() for a watch, ECEventBus.removeNativeAudience(name)"
                    + " for an audience - otherwise the next test class inherits it and its gate counts change.");
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
