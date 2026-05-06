package br.com.finalcraft.evernifecore.minecraft.loader;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.eventhandler.ECEventDispatcher;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.dependencies.DependencyManager;
import br.com.finalcraft.evernifecore.dependencies.ECoreDependencies;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.featherboard.FeatherBoardUtils;
import br.com.finalcraft.evernifecore.integration.VaultIntegration;
import br.com.finalcraft.evernifecore.integration.WorldEditIntegration;
import br.com.finalcraft.evernifecore.listeners.PlayerInteractListener;
import br.com.finalcraft.evernifecore.listeners.PlayerLoginListener;
import br.com.finalcraft.evernifecore.listeners.PluginListener;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.commands.McCommandRegisterer;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.MinecraftArgParsers;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.HyCfgLoadableSalvable;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McECEventDispatcher;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McECPluginExtractor;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McPlatform;
import br.com.finalcraft.evernifecore.thread.SaveConfigThread;
import br.com.finalcraft.evernifecore.util.FCTickUtil;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.version.MCVersion;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

@ECPlugin(
        spigotID = "97739",
        bstatsID = "13351"
)
public class McEverNifeCore extends JavaPlugin {

    private static final DependencyManager dependencyManager;
    public static McEverNifeCore instance;
    static {
        dependencyManager = new DependencyManager();//This is the DefaultConstrutor for EverNifeCore DependencyManager
        dependencyManager.addJitPack();
        dependencyManager.addJCenter();
        dependencyManager.addMavenCentral();
        dependencyManager.addSonatype();
        dependencyManager.addRepository("https://maven.petrus.dev/public");

        //First thing to do when this class is loaded is to add REQUIRED dependencies, because there ara plugins that depends
        //on EverNifeCore and are loaded before it, for example, FinalEconomy
        ECoreDependencies.initialize(dependencyManager);
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

        RegionGridOptions.setCurrent(RegionGridOptions.HYTALE);

        EverNifeCore.instance.onLoaderInstantiate(ECPluginManager.getOrCreateECorePluginData(this));
        HyCfgLoadableSalvable.initialize();
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
        ConfigManager.initialize(this);

        EverNifeCore.getLog().info("§aLoading up Cooldown System!");
        Cooldown.initialize();

        EverNifeCore.getLog().info("§aRegistering Commands!");
        McCommandRegisterer.registerCommands(ecPluginData);

        EverNifeCore.getLog().info("§aHooking into Vault (Economy)");
        VaultIntegration.initialize();

        EverNifeCore.getLog().info("§aRegistering Listeners");
        ECListener.register(this, PlayerLoginListener.class);
        ECListener.register(this, PlayerInteractListener.class);
        ECListener.register(this, PluginListener.class);

        if (Bukkit.getPluginManager().isPluginEnabled("AuthMe")){
            ECListener.register(this, PlayerLoginListener.AuthmeLogin.class);
        }else {
            ECListener.register(this, PlayerLoginListener.VanillaLogin.class);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("FeatherBoard")) try{FeatherBoardUtils.initialize();}catch (Throwable e){e.printStackTrace();}
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
