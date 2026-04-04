package br.com.finalcraft.evernifecore;

import br.com.finalcraft.evernifecore.api.common.providers.ECProviders;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.commands.CommandRegisterer;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.yaml.helper.CfgExecutor;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logger.ECLogger;
import br.com.finalcraft.evernifecore.thread.SaveConfigThread;


public class EverNifeCore {

    public static EverNifeCore instance = new EverNifeCore();

    public static ECLogger<ECDebugModule> getLog(){
        return (ECLogger<ECDebugModule>) instance.ecPluginData.getLog();
    }

    private ECPluginData ecPluginData;

    private ECProviders ecProviders = new ECProviders();

    public ECProviders getProviders() {
        return ecProviders;
    }

    public static IPlatform getPlatform(){
        return instance.getProviders().getPlatformOperations();
    }


    public void onLoaderInstantiate(ECPluginData ecPluginData){
        this.ecPluginData = ecPluginData;
        ecPluginData.defineDebugModules(ECDebugModule.values());
    }

    public ECPluginData getEcPluginData() {
        return ecPluginData;
    }

    public void onLoadPre() {
        getLog().info("§aStarting EverNifeCore");

        getLog().info("§aLoading up Configurations...");
        ConfigManager.initialize(getEcPluginData());

        getLog().info("§aLoading up Cooldown System!");
        Cooldown.initialize();

        getLog().info("§aRegistering Commands!");
        CommandRegisterer.registerCommands(this.getEcPluginData());
    }

    public void onLoadPost() {
        SaveConfigThread.INSTANCE.start();
        getLog().info("§aEverNifeCore successfully started!");
    }

    public void onUnload() {
        SaveConfigThread.INSTANCE.shutdown();
        PlayerController.savePlayerDataOnConfig();
        CfgExecutor.shutdownExecutorAndScheduler();
    }

    @ECPlugin.Reload
    public void onReload(){
        SaveConfigThread.INSTANCE.shutdownSilently();
        ConfigManager.initialize(ecPluginData);
        ConfigManager.reloadCooldownConfig();
        SaveConfigThread.INSTANCE.start();
    }

}
