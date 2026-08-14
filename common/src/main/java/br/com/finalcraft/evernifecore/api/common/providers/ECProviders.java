package br.com.finalcraft.evernifecore.api.common.providers;

import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;

public class ECProviders {

    private final ECBaseProvider BASE_PROVIDER = new ECBaseProvider();

    public ECBaseProvider getBaseProvider(){
        return BASE_PROVIDER;
    }

    // -------------------------------------------------------------
    //   Implementations
    // -------------------------------------------------------------

    public IECPluginExtractor getECPluginExtractor(){
        return BASE_PROVIDER.provide(IECPluginExtractor.class);
    }

    public IPlatform getPlatform(){
        return BASE_PROVIDER.provide(IPlatform.class);
    }

    /** Like {@link #getPlatform()} but returns {@code null} instead of throwing when none is registered. */
    public IPlatform getPlatformOrNull(){
        return BASE_PROVIDER.provideOrNull(IPlatform.class);
    }

    public IEconomyProvider getEconomy(){
        return BASE_PROVIDER.provide(IEconomyProvider.class);
    }

    /** Like {@link #getEconomy()} but returns {@code null} instead of throwing when none is registered. */
    public IEconomyProvider getEconomyOrNull(){
        return BASE_PROVIDER.provideOrNull(IEconomyProvider.class);
    }

    /**
     * The process-wide event bus. It is not a registered provider: the bus is a static holder in
     * {@code common}, so it is there before any platform loads and a re-instantiated platform cannot
     * orphan the subscriptions taken against it.
     */
    public ECEventBus getEventBus(){
        return ECEventBus.global();
    }

}
