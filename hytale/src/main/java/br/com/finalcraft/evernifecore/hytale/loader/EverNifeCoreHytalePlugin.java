package br.com.finalcraft.evernifecore.hytale.loader;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.eventhandler.ECEventDispatcher;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.hytale.commands.HyCommandRegisterer;
import br.com.finalcraft.evernifecore.hytale.ecplugin.ECHytalePlugin;
import br.com.finalcraft.evernifecore.hytale.integration.HyVaultIntegration;
import br.com.finalcraft.evernifecore.hytale.listeners.PlayerLoginListener;
import br.com.finalcraft.evernifecore.hytale.loader.imp.HyECEventDispatcher;
import br.com.finalcraft.evernifecore.hytale.loader.imp.HyECPluginExtractor;
import br.com.finalcraft.evernifecore.hytale.loader.imp.HyPlatform;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import jakarta.annotation.Nonnull;


/**
 * Hytale entry point. It dogfoods the shared {@link ECHytalePlugin} bridge in symmetry with the
 * Bukkit main: the platform-agnostic wiring runs in {@link #onECPluginEnable()} (delegating to
 * {@link EverNifeCore}) and the Hytale extras in {@link #onECPluginEnablePost()}. Providers are
 * registered in the constructor, before any bootstrap hook runs.
 */
public class EverNifeCoreHytalePlugin extends ECHytalePlugin {

    public static EverNifeCoreHytalePlugin instance;

    public EverNifeCoreHytalePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this; //Attribute Instance at the exact moment that this class is instantiated

        //Register Providers as Early as Possible
        EverNifeCore.getProviders().getBaseProvider().register(
                IECPluginExtractor.class,
                new HyECPluginExtractor()
        );

        EverNifeCore.getProviders().getBaseProvider().register(
                IPlatform.class,
                new HyPlatform()
        );

        EverNifeCore.getProviders().getBaseProvider().register(
                ECEventDispatcher.class,
                new HyECEventDispatcher()
        );

        //Economy resolves lazily, so registering it this early costs nothing and covers a plugin that
        //sets up before EverNifeCore and charges during its own setup.
        HyVaultIntegration.register();

        RegionGridOptions.setCurrent(RegionGridOptions.HYTALE);

        EverNifeCore.instance.onLoaderInstantiate(ECPluginManager.getOrCreateECorePluginData(this));
    }

    @Override
    public void onECPluginEnable() {
        //Shared wiring both platforms run: config, cooldown and the platform-agnostic commands.
        EverNifeCore.instance.onLoadPre();
    }

    @Override
    public void onECPluginEnablePost() {
        ECPluginData ecPluginData = getPluginData();

        HyCommandRegisterer.registerCommands(ecPluginData);

        EverNifeCore.getLog().info("§aRegistering Listeners");
        ECListener.register(ecPluginData, PlayerLoginListener.class);

        if (PAPIIntegration.isPresent()){
//            ECCorePAPIPlaceholders.initialize(ecPluginData);
        }
    }

    @Override
    public void onECPluginShutdown() {
        EverNifeCore.instance.onUnload();
    }

    @Override
    public void onECPluginReload() {
        EverNifeCore.instance.onReload();
    }

}
