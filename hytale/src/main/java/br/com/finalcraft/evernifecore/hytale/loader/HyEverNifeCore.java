package br.com.finalcraft.evernifecore.hytale.loader;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.hytale.commands.HyCommandRegisterer;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.HytaleArgParsers;
import br.com.finalcraft.evernifecore.hytale.listeners.PlayerLoginListener;
import br.com.finalcraft.evernifecore.hytale.loader.imp.HyCfgLoadableSalvable;
import br.com.finalcraft.evernifecore.hytale.loader.imp.HyECPluginExtractor;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.jspecify.annotations.NonNull;


public class HyEverNifeCore extends JavaPlugin {

    public static HyEverNifeCore instance;

    public HyEverNifeCore(@NonNull JavaPluginInit init) {
        super(init);
        instance = this; //Attribute Instance at the exact moment that this class is instantiated
        EverNifeCore.instance.onLoaderInstantiate(ECPluginManager.getOrCreateECorePluginData(this));

        //Register Providers as Early as Possible
        EverNifeCore.instance.getProviders().getBaseProvider().register(
                IECPluginExtractor.class,
                new HyECPluginExtractor()
        );

        HyCfgLoadableSalvable.initialize();
        HytaleArgParsers.initialize();
    }

    @Override
    protected void setup() {
        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(this);

        EverNifeCore.instance.onLoadPre();
        HyCommandRegisterer.registerCommands(this);

        EverNifeCore.getLog().info("§aRegistering Listeners");
        ECListener.register(ecPluginData, PlayerLoginListener.class);

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
