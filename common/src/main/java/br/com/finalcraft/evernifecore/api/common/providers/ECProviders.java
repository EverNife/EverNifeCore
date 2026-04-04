package br.com.finalcraft.evernifecore.api.common.providers;

import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.eventhandler.ECEventDispatcher;

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

    public IPlatform getPlatformOperations(){
        return BASE_PROVIDER.provide(IPlatform.class);
    }

    public ECEventDispatcher getEventDispatcher(){
        return BASE_PROVIDER.provide(ECEventDispatcher.class);
    }

}
