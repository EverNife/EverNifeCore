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
import br.com.finalcraft.evernifecore.util.FCTickUtil;
import br.com.finalcraft.evernifecore.version.MCVersion;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
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
    public static EverNifeCore instance; { instance = this; } //Attribute Instance at the exact moment that this class is instantiated
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

    private ECLogger<ECDebugModule> ecLogger = new ECLogger<>(this);

    public static ECLogger<ECDebugModule> getLog(){
        return instance.ecLogger;
    }

    public static void info(String msg) {
        instance.getLogger().info("[Info] " + msg);
    }

    public static void warning(String msg) {
        instance.getLogger().warning("[Warning] " + msg);
    }

    public static DependencyManager getDependencyManager() {
        return dependencyManager;
    }

    @Override
    public void onEnable() {
        MinecraftVersion.replaceLogger(this.getLogger());//Replace [NBT-API] logger

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

        FCTickUtil.getTickCount();//This will start tickCounting
        info("§aEverNifeCore successfully started!");
//
//
//        getLog().info("NMSUtils.get().getVersion() = " + NMSUtils.get().getClass().getSimpleName());
//        getLog().info("\n\n\n[NMS-1]\n\n\n");
//
//        NMSUtils.get().getItemRegistry().getRegistryResourceLocation().entrySet()
//                .stream()
//                .map(entry -> {
//                    IHasMinecraftIdentifier object = NMSUtils.get().getBlockRegistry().getObject(entry.getKey());
//                    Boolean itemMaterialIsEqual = object == null ? null : object.getMaterial().equals(entry.getValue().getMaterial());
//                    return String.format(
//                            "(ITEM) [%s] --> %s  | hasBlock: %s blockMateriaIsEqual: %s %s",
//                            entry.getKey(),
//                            entry.getValue().getMaterial().name(),
//                            object != null,
//                            itemMaterialIsEqual,
//                            itemMaterialIsEqual != null && itemMaterialIsEqual == false ? object.getMaterial().name() : ""
//                    );
//                }).sorted()
//                .forEach(s -> {
//                    getLog().info(s);
//                });
//
//        getLog().info("\n\n\n[NMS-2]\n\n\n");
//
//        NMSUtils.get().getBlockRegistry().getRegistryResourceLocation().entrySet()
//                .stream()
//                .map(entry -> {
//
//                    IHasMinecraftIdentifier object = NMSUtils.get().getItemRegistry().getObject(entry.getKey());
//                    Boolean itemMaterialIsEqual = object == null ? null : object.getMaterial().equals(entry.getValue().getMaterial());
//                    return String.format(
//                            "(BLOCK) [%s] --> %s  | hasItem: %s itemMateriaIsEqual: %s %s",
//                            entry.getKey(),
//                            entry.getValue().getMaterial().name(),
//                            object != null,
//                            itemMaterialIsEqual,
//                            itemMaterialIsEqual != null && itemMaterialIsEqual == false ? object.getMaterial().name() : ""
//                    );
//
//                }).sorted()
//                .forEach(s -> {
//                    getLog().info(s);
//                });
//
//        getLog().info("\n\n\n[NMS-5]\n\n\n");
//
//        NMSUtils.get().getOreRegistry().getAllOreNames().stream()
//                .sorted()
//                .forEach(oreName -> {
//                    getLog().info("[%s] --> %s",
//                            oreName,
//                            NMSUtils.get().getOreRegistry()
//                                    .getOreItemStacks(oreName)
//                                    .stream()
//                                    .map(itemStack -> FCItemUtils.getMinecraftIdentifier(itemStack))
//                                    .collect(Collectors.joining(","))
//                    );
//                });

    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        SaveConfigThread.INSTANCE.shutdown();
        PlayerController.savePlayerDataOnConfig();
        CfgExecutor.shutdownExecutorAndScheduler();
    }

    @ECPlugin.Reload
    public void onReload(){
        SaveConfigThread.INSTANCE.setSilent(true);
        SaveConfigThread.INSTANCE.shutdown();
        ConfigManager.initialize(this);
        ConfigManager.reloadCooldownConfig();
        SaveConfigThread.INSTANCE.start();
        SaveConfigThread.INSTANCE.setSilent(false);
    }

}
