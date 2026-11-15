package br.com.finalcraft.evernifecore.api.common.providers;

import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.ignore.ECIgnores;
import br.com.finalcraft.evernifecore.spawn.ISpawnProvider;
import jakarta.annotation.Nullable;

public class ECProviders {

    private final ECBaseProvider BASE_PROVIDER = new ECBaseProvider();
    private volatile @Nullable ECPluginData spawnOwner;

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

    /** The server spawn a spawn plugin registered; throws when none is - see {@link #getSpawnOrNull()}. */
    public ISpawnProvider getSpawn(){
        return BASE_PROVIDER.provide(ISpawnProvider.class);
    }

    /** Like {@link #getSpawn()} but returns {@code null} instead of throwing when no spawn plugin registered one. */
    public ISpawnProvider getSpawnOrNull(){
        return BASE_PROVIDER.provideOrNull(ISpawnProvider.class);
    }

    /**
     * Registers {@code spawn}, owned by {@code owner}, as the server spawn - in the base provider, as before, with the
     * owner kept beside it. It leaves on its own when {@code owner} is released.
     */
    public synchronized void setSpawn(ECPluginData owner, ISpawnProvider spawn){
        BASE_PROVIDER.register(ISpawnProvider.class, spawn);
        spawnOwner = owner;
    }

    /** The plugin whose spawn answers, null when none registered one through {@link #setSpawn}. */
    public @Nullable ECPluginData getSpawnOwner(){
        return spawnOwner;
    }

    /**
     * Takes the spawn out if {@code owner} still owns it, so a plugin that was already replaced never evicts its
     * successor. Run by the core when the plugin is released.
     */
    public synchronized boolean unregisterSpawn(ECPluginData owner){
        if (spawnOwner != owner){
            return false;
        }
        BASE_PROVIDER.unregister(ISpawnProvider.class);
        spawnOwner = null;
        return true;
    }

    /** Who ignores whom, per {@link br.com.finalcraft.evernifecore.ignore.IgnoreKind}. Nobody, until a plugin says otherwise. */
    public ECIgnores getIgnores(){
        return ECIgnores.global();
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
