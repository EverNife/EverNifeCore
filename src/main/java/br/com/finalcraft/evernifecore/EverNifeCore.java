package br.com.finalcraft.evernifecore;

import br.com.finalcraft.evernifecore.autoupdater.SpigotUpdateChecker;
import br.com.finalcraft.evernifecore.commands.CommandRegisterer;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.dependencies.DependencyManager;
import br.com.finalcraft.evernifecore.featherboard.FeatherBoardUtils;
import br.com.finalcraft.evernifecore.integration.VaultIntegration;
import br.com.finalcraft.evernifecore.integration.WorldEditIntegration;
import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.listeners.PlayerInteractListener;
import br.com.finalcraft.evernifecore.listeners.PlayerLoginListener;
import br.com.finalcraft.evernifecore.listeners.PluginListener;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.metrics.Metrics;
import br.com.finalcraft.evernifecore.protection.handlers.ProtectionPlugins;
import br.com.finalcraft.evernifecore.thread.SaveConfigThread;
import br.com.finalcraft.evernifecore.version.MCVersion;
import me.tom.sparse.spigot.chat.menu.ChatMenuAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class EverNifeCore extends JavaPlugin {

    public static EverNifeCore instance;

    public static void info(String msg) {
        instance.getLogger().info("[Info] " + msg);
    }

    public static void debug(String msg) {
        instance.getLogger().info("[Debug] " + msg);
    }

    public static void warning(String msg) {
        instance.getLogger().warning("[Warning] " + msg);
    }

    private static DependencyManager dependencyManager;

    public static DependencyManager getDependencyManager() {
        if (EverNifeCore.instance == null) throw new IllegalStateException("EverNifeCore was not initialized yet, you can't get it's DependencyManager");
        if (dependencyManager == null){
            dependencyManager = new DependencyManager(EverNifeCore.instance);
            dependencyManager.addJitPack();
            dependencyManager.addJCenter();
            dependencyManager.addMavenCentral();
            dependencyManager.addSonatype();
            dependencyManager.addRepository("https://maven.petrus.dev/public");
        }
        return dependencyManager;
    }

    @Override
    public void onEnable() {
        instance = this;
        info("§aStarting EverNifeCore");
        info("§aServer Minecraft Version " + MCVersion.getCurrent().name() + " !");

        info("§aInstalling BStats");
        Metrics metrics = new Metrics(this, 13351);//EverNifeCore MetricsID: 13351

        EverForgeLibIntegration.initialize();

        info("§aLoading up Configurations...");
        ConfigManager.initialize(this);

        SpigotUpdateChecker.checkForUpdates(this, "97739", ConfigManager.getMainConfig()); //EverNifeCore SpigotID: 97739

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

        info("§aSearching for Protection plugins...");
        ProtectionPlugins.initialize();

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
        Config.shutdownSaveScheduller();
        ChatMenuAPI.disable();
    }

}
