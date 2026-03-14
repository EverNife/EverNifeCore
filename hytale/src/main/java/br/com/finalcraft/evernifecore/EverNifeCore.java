package br.com.finalcraft.evernifecore;

import br.com.finalcraft.evernifecore.commands.CommandRegisterer;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.yaml.helper.CfgExecutor;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.dependencies.DependencyManager;
import br.com.finalcraft.evernifecore.dependencies.ECoreDependencies;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.integration.ECCorePAPIPlaceholders;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.listeners.PlayerLoginListener;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logger.ECLogger;
import br.com.finalcraft.evernifecore.thread.SaveConfigThread;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.jspecify.annotations.NonNull;


public class EverNifeCore extends JavaPlugin {

    private static final DependencyManager dependencyManager;
    public static EverNifeCore instance; { instance = this; } //Attribute Instance at the exact moment that this class is instantiated
    static {
        System.out.printf("Teste Hermano");
        dependencyManager = new DependencyManager();//This is the DefaultConstrutor for EverNifeCore DependencyManager
        dependencyManager.addJitPack();
        dependencyManager.addJCenter();
        dependencyManager.addMavenCentral();
        dependencyManager.addSonatype();
        dependencyManager.addRepository("https://maven.petrus.dev/public");

        //First thing to do when this class is loaded is to add REQUIRED dependencies, because there ara plugins that depends
        //on EverNifeCore and are loaded before it, for example, FinalEconomy
        ECoreDependencies.initialize(dependencyManager);
    }

    private ECLogger<ECDebugModule> ecLogger = new ECLogger<>(this, ECDebugModule.values());

    public static ECLogger<ECDebugModule> getLog(){
        return instance.ecLogger;
    }

    public static DependencyManager getDependencyManager() {
        return dependencyManager;
    }

    public EverNifeCore(@NonNull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLog().info("§aStarting EverNifeCore");

        getLog().info("§aLoading up Configurations...");
        ConfigManager.initialize(this);

        getLog().info("§aLoading up Cooldown System!");
        Cooldown.initialize();

        getLog().info("§aRegistering Commands!");
        CommandRegisterer.registerCommands(this);

        getLog().info("§aRegistering Listeners");
        ECListener.register(this, PlayerLoginListener.class);

        SaveConfigThread.INSTANCE.start();

        getLog().info("§aEverNifeCore successfully started!");

        if (PAPIIntegration.isPresent()){
            ECCorePAPIPlaceholders.initialize(this);
        }
    }

    @Override
    public void shutdown() {
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
