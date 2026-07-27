package br.com.finalcraft.evernifecore.api.common.providers;

import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ECBaseProvider {

    private static final Logger logger = Logger.getLogger("ECBaseProvider");

    private final ConcurrentHashMap<Class<?>, Object> REGISTERED_PROVIDERS = new ConcurrentHashMap<>();

    public <T> T provide(Class<T> clazz) throws NoSuchElementException {
        Object o = REGISTERED_PROVIDERS.get(clazz);
        if (o == null){
            throw new NoSuchElementException("[ECBaseProvider] No provider found for type: " + clazz.getSimpleName());
        }
        return (T) o;
    }

    /** Like {@link #provide(Class)} but returns {@code null} instead of throwing when none is registered. */
    public <T> T provideOrNull(Class<T> clazz) {
        return (T) REGISTERED_PROVIDERS.get(clazz);
    }

    public <T> T register(Class<T> providerType, T something) {
        Object previousProvider = REGISTERED_PROVIDERS.put(providerType, something);

        logger.info(String.format("[ECBaseProvider] Registering ECProvider#%s with %s",  providerType.getSimpleName(), something.getClass().getName()));
        if (previousProvider != null) {
            logger.warning(String.format("[ECBaseProvider] The previous ECProvider#%s %s was removed!", providerType.getSimpleName(), previousProvider.getClass().getName()));
        }

        return something;
    }

    /**
     * Removes the provider registered for {@code providerType}.
     *
     * <p>Exists so a caller that installed a provider can put the previous world back - a test
     * fixture that never uninstalls turns the registry into shared state between test classes,
     * which shows up as order-dependent failures.</p>
     *
     * @return the provider that was registered, or {@code null} if there was none.
     */
    public <T> T unregister(Class<T> providerType) {
        Object previousProvider = REGISTERED_PROVIDERS.remove(providerType);

        if (previousProvider != null) {
            logger.info(String.format("[ECBaseProvider] Unregistering ECProvider#%s (%s)", providerType.getSimpleName(), previousProvider.getClass().getName()));
        }

        return (T) previousProvider;
    }

}
