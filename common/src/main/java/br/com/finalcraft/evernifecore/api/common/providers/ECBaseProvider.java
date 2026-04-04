package br.com.finalcraft.evernifecore.api.common.providers;

import br.com.finalcraft.evernifecore.logger.ECDebugModule;

import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

public class ECBaseProvider {

    private final ConcurrentHashMap<Class<?>, Object> REGISTERED_PROVIDERS = new ConcurrentHashMap<>();

    public <T> T provide(Class<T> clazz) throws NoSuchElementException {
        Object o = REGISTERED_PROVIDERS.get(clazz);
        if (o == null){
            throw new NoSuchElementException("[ECBaseProvider] No provider found for type: " + clazz.getSimpleName());
        }
        return (T) o;
    }

    public <T> T register(Class<T> providerType, T something) {
        Object previousProvider = REGISTERED_PROVIDERS.put(providerType, something);

        ECDebugModule.EC_PROVIDERS.infoModule("Registering ECPorvider#%s with %s", providerType.getSimpleName(), something.getClass().getName());
        if (previousProvider != null) {
            ECDebugModule.EC_PROVIDERS.warningModule("  The previous ECPorvider#%s %s was removed!", providerType.getSimpleName(), previousProvider.getClass().getName());
        }

        return something;
    }

}
