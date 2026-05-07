package br.com.finalcraft.evernifecore.minecraft.loader;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.eventhandler.ECEventDispatcher;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.minecraft.commands.McCommandRegisterer;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.MinecraftArgParsers;
import br.com.finalcraft.evernifecore.minecraft.config.McConfigManager;
import br.com.finalcraft.evernifecore.minecraft.dependencies.ECoreDependencies;
import br.com.finalcraft.evernifecore.minecraft.integration.VaultIntegration;
import br.com.finalcraft.evernifecore.minecraft.integration.WorldEditIntegration;
import br.com.finalcraft.evernifecore.minecraft.listeners.PlayerInteractListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.PlayerLoginListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.PluginListener;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McCfgLoadableSalvable;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McECEventDispatcher;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McECPluginExtractor;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McPlatform;
import br.com.finalcraft.evernifecore.minecraft.ontime.McDefaultOntimeManager;
import br.com.finalcraft.evernifecore.minecraft.util.FCTickUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.thread.SaveConfigThread;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

@ECPlugin(
        spigotID = "97739",
        bstatsID = "13351"
)
public class EverNifeCoreBukkitPlugin extends JavaPlugin {

    public static EverNifeCoreBukkitPlugin instance;
    static {
        //First thing to do when this class is loaded is to add REQUIRED dependencies, because there ara plugins that depends
        //on EverNifeCore and are loaded before it, for example, FinalEconomy
        ECoreDependencies.initialize();
        MinecraftVersion.disableBStats();
        MinecraftVersion.disableUpdateCheck();
    }

    {
        instance = this; //Attribute Instance at the exact moment that this class is instantiated

        //Register Providers as Early as Possible
        EverNifeCore.getProviders().getBaseProvider().register(
                IECPluginExtractor.class,
                new McECPluginExtractor()
        );

        EverNifeCore.getProviders().getBaseProvider().register(
                IPlatform.class,
                new McPlatform()
        );

        EverNifeCore.getProviders().getBaseProvider().register(
                ECEventDispatcher.class,
                new McECEventDispatcher()
        );

        if (MCVersion.isHigherEquals(MCVersion.v1_19)){
            RegionGridOptions.setCurrent(RegionGridOptions.MINECRAFT);
        }

        McDefaultOntimeManager.initialize();

        EverNifeCore.instance.onLoaderInstantiate(ECPluginManager.getOrCreateECorePluginData(this));
        McCfgLoadableSalvable.initialize();
        MinecraftArgParsers.initialize();
    }

    @Override
    public void onEnable() {
        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(this);

        EverNifeCore.instance.onLoadPre();

        MinecraftVersion.replaceLogger(this.getLogger());//Replace [NBT-API] logger

        EverNifeCore.getLog().info("§aStarting EverNifeCore");
        EverNifeCore.getLog().info("§aServer Minecraft Version " + MCVersion.getCurrent().name() + " !");

        EverNifeCore.getLog().info("§aLoading up Configurations...");
        McConfigManager.initialize(ecPluginData);

        EverNifeCore.getLog().info("§aLoading up Cooldown System!");
        Cooldown.initialize();

        EverNifeCore.getLog().info("§aRegistering Commands!");
        McCommandRegisterer.registerCommands(ecPluginData);

        EverNifeCore.getLog().info("§aHooking into Vault (Economy)");
        VaultIntegration.initialize();

        EverNifeCore.getLog().info("§aRegistering Listeners");
        ECListener.register(ecPluginData, PlayerLoginListener.class);
        ECListener.register(ecPluginData, PlayerInteractListener.class);
        ECListener.register(ecPluginData, PluginListener.class);

        if (Bukkit.getPluginManager().isPluginEnabled("AuthMe")){
            ECListener.register(ecPluginData, PlayerLoginListener.AuthmeLogin.class);
        }else {
            ECListener.register(ecPluginData, PlayerLoginListener.VanillaLogin.class);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) try{WorldEditIntegration.initialize();}catch (Throwable e){e.printStackTrace();}

        SaveConfigThread.INSTANCE.start();

        FCTickUtil.getTickCount();//This will start tickCounting
        EverNifeCore.getLog().info("§aEverNifeCore successfully started!");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        EverNifeCore.instance.onUnload();
    }

    @ECPlugin.Reload
    public void onReload(){
        EverNifeCore.instance.onReload();
    }

}
