package br.com.finalcraft.evernifecore;

import br.com.finalcraft.evernifecore.commands.CommandRegisterer;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.yaml.helper.CfgExecutor;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.dependencies.DependencyManager;
import br.com.finalcraft.evernifecore.dependencies.ECoreDependencies;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.featherboard.FeatherBoardUtils;
import br.com.finalcraft.evernifecore.integration.VaultIntegration;
import br.com.finalcraft.evernifecore.integration.WorldEditIntegration;
import br.com.finalcraft.evernifecore.listeners.PlayerInteractListener;
import br.com.finalcraft.evernifecore.listeners.PlayerLoginListener;
import br.com.finalcraft.evernifecore.listeners.PluginListener;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logger.ECLogger;
import br.com.finalcraft.evernifecore.thread.SaveConfigThread;
import br.com.finalcraft.evernifecore.version.MCVersion;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import me.tom.sparse.spigot.chat.menu.ChatMenuAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

@ECPlugin(
        spigotID = "97739",
        bstatsID = "13351",
        debugModuleEnum = ECDebugModule.class
)
public class EverNifeCore extends JavaPlugin {

    private static final DependencyManager dependencyManager;

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

    public static EverNifeCore instance;

    private ECLogger<ECDebugModule> ecLogger = new ECLogger<>(this);

    public static ECLogger<ECDebugModule> getLog(){
        return instance.ecLogger;
    }

    public static void info(String msg) {
        instance.getLogger().info("[Info] " + msg);
    }

    public static void debug(String msg) {
        instance.getLogger().info("[Debug] " + msg);
    }

    public static void warning(String msg) {
        instance.getLogger().warning("[Warning] " + msg);
    }

    public static DependencyManager getDependencyManager() {
        return dependencyManager;
    }

    @Override
    public void onEnable() {
        instance = this;
        info("§aStarting EverNifeCore");
        info("§aServer Minecraft Version " + MCVersion.getCurrent().name() + " !");

        info("§aLoading up Configurations...");
        ConfigManager.initialize(this);

        info("§aLoading up Cooldown System!");
        Cooldown.initialize();

        info("§aRegistering Commands!");
        CommandRegisterer.registerCommands(this);

        info("§aHooking into Vault (Economy)");
        VaultIntegration.initialize();

        info("§aRegistering Listeners");
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

        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")){
            ChatMenuAPI.init(instance, false);
        }

        info("§aEverNifeCore successfully started!");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        SaveConfigThread.INSTANCE.shutdown();
        PlayerController.savePlayerDataOnConfig();
        CfgExecutor.shutdownExecutor();
        ChatMenuAPI.disable();
    }

    @ECPlugin.Reload
    public void onReload(){
        ConfigManager.initialize(this);
        ConfigManager.reloadCooldownConfig();
    }

}
