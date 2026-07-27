package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.ECBaseProvider;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;

/**
 * A platform installed in the global provider registry, plus the way to undo it.
 *
 * <p>The registry is process-wide and Gradle reuses the JVM across test classes, so an install
 * that never unwinds is shared state: the next class inherits a platform it did not ask for, and
 * the failure shows up as order-dependent. Closing this world puts back exactly what was there
 * before - including "nothing at all".</p>
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
    private boolean closed = false;

    private ECoreTestWorld(TestPlatform platform, IPlatform previousPlatform, IECPluginExtractor previousExtractor) {
        this.platform = platform;
        this.previousPlatform = previousPlatform;
        this.previousExtractor = previousExtractor;
        this.hadExtractor = previousExtractor != null;
    }

    static ECoreTestWorld install(TestPlatform platform) {
        System.setProperty(PERIODIC_FLUSH_PROPERTY, "false");

        ECBaseProvider providers = EverNifeCore.getProviders().getBaseProvider();
        ECoreTestWorld world = new ECoreTestWorld(
                platform,
                providers.provideOrNull(IPlatform.class),
                providers.provideOrNull(IECPluginExtractor.class)
        );

        providers.register(IPlatform.class, platform);
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

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        ECBaseProvider providers = EverNifeCore.getProviders().getBaseProvider();
        restore(providers, IPlatform.class, previousPlatform, previousPlatform != null);
        restore(providers, IECPluginExtractor.class, previousExtractor, hadExtractor);
    }

    private static <T> void restore(ECBaseProvider providers, Class<T> type, T previous, boolean hadPrevious) {
        if (hadPrevious) {
            providers.register(type, previous);
        } else {
            providers.unregister(type);
        }
    }
}
