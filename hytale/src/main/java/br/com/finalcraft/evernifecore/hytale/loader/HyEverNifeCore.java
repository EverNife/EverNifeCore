package br.com.finalcraft.evernifecore.hytale.loader;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.vector.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.eventhandler.ECEventDispatcher;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.hytale.commands.HyCommandRegisterer;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.HytaleArgParsers;
import br.com.finalcraft.evernifecore.hytale.integration.HyVaultIntegration;
import br.com.finalcraft.evernifecore.hytale.listeners.PlayerLoginListener;
import br.com.finalcraft.evernifecore.hytale.loader.imp.HyCfgLoadableSalvable;
import br.com.finalcraft.evernifecore.hytale.loader.imp.HyECEventDispatcher;
import br.com.finalcraft.evernifecore.hytale.loader.imp.HyECPluginExtractor;
import br.com.finalcraft.evernifecore.hytale.loader.imp.HyPlatform;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.jspecify.annotations.NonNull;


public class HyEverNifeCore extends JavaPlugin {

    public static HyEverNifeCore instance;

    public HyEverNifeCore(@NonNull JavaPluginInit init) {
        super(init);
        instance = this; //Attribute Instance at the exact moment that this class is instantiated

        //Register Providers as Early as Possible
        EverNifeCore.instance.getProviders().getBaseProvider().register(
                IECPluginExtractor.class,
                new HyECPluginExtractor()
        );

        EverNifeCore.instance.getProviders().getBaseProvider().register(
                IPlatform.class,
                new HyPlatform()
        );

        EverNifeCore.instance.getProviders().getBaseProvider().register(
                ECEventDispatcher.class,
                new HyECEventDispatcher()
        );

        RegionGridOptions.setCurrent(RegionGridOptions.HYTALE);

        EverNifeCore.instance.onLoaderInstantiate(ECPluginManager.getOrCreateECorePluginData(this));
        HyCfgLoadableSalvable.initialize();
        HytaleArgParsers.initialize();
    }

    @Override
    protected void setup() {
        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(this);

        EverNifeCore.instance.onLoadPre();

        HyVaultIntegration.initialize();

        HyCommandRegisterer.registerCommands(ecPluginData);

        EverNifeCore.getLog().info("§aRegistering Listeners");
        ECListener.register(ecPluginData, PlayerLoginListener.class);

        if (PAPIIntegration.isPresent()){
//            ECCorePAPIPlaceholders.initialize(ecPluginData);
        }

        EverNifeCore.instance.onLoadPost();

//        if (PAPIIntegration.isPresent()){
//            ECCorePAPIPlaceholders.initialize(this);
//        }
    }

    @Override
    public void shutdown() {
        EverNifeCore.instance.onUnload();
    }

    @ECPlugin.Reload
    public void onReload(){
        EverNifeCore.instance.onReload();
    }

}
