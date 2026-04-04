package br.com.finalcraft.evernifecore.api.common.providers;

import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;

public class ECProviders {

    private final ECBaseProvider BASE_PROVIDER = new ECBaseProvider();

    public IECPluginExtractor getECPluginExtractor(){
        return BASE_PROVIDER.provide(IECPluginExtractor.class);
    }

    public IPlatform getPlatformOperations(){
        return BASE_PROVIDER.provide(IPlatform.class);
    }

    public ECBaseProvider getBaseProvider(){
        return BASE_PROVIDER;
    }

}
