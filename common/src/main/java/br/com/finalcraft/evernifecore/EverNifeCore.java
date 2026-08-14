package br.com.finalcraft.evernifecore;

import br.com.finalcraft.evernifecore.api.common.providers.ECProviders;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.commands.CommandRegisterer;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.economy.IEconomyProvider;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.annotations.ECPlugin;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.logger.ECLogger;


public class EverNifeCore {

    public static EverNifeCore instance = new EverNifeCore();

    public static ECLogger<ECDebugModule> getLog(){
        return (ECLogger<ECDebugModule>) instance.ecPluginData.getLog();
    }

    private ECPluginData ecPluginData;

    private ECProviders ecProviders = new ECProviders();

    public static ECProviders getProviders() {
        return instance.ecProviders;
    }

    public static IPlatform getPlatform(){
        return instance.getProviders().getPlatform();
    }

    public static ECEventBus getEventBus(){
        return instance.getProviders().getEventBus();
    }

    public void onLoaderInstantiate(ECPluginData ecPluginData){
        this.ecPluginData = ecPluginData;
        ecPluginData.defineDebugModules(ECDebugModule.values());
    }

    public static ECPluginData getEcPluginData() {
        return instance.ecPluginData;
    }

    public void onLoadPre() {
        //The start/enabled banners are the bootstrap orchestrator's job (runECPluginEnable); this
        //is only the shared wiring both platforms run.
        getLog().info("§aLoading up Configurations...");
        ConfigManager.initialize(getEcPluginData());

        getLog().info("§aLoading up Cooldown System!");
        Cooldown.initialize();

        getLog().info("§aRegistering Commands!");
        CommandRegisterer.registerCommands(this.getEcPluginData());

        checkEconomyOnFirstTick();
    }

    /**
     * Reports on economy once every plugin has enabled, which is the only moment the answer is
     * trustworthy: an economy plugin that registers later than EverNifeCore is normal, and complaining
     * during enable would be a false alarm.
     */
    private void checkEconomyOnFirstTick() {
        getPlatform().runOnMainThreadNextTick(() -> {
            IEconomyProvider economy = getProviders().getEconomyOrNull();
            if (economy == null) {
                //Nobody registered one. This is a wiring bug in the platform module, NOT a server
                //without an economy plugin - the two used to look identical from the console, which is
                //how the economy contract stayed orphan for releases.
                getLog().warning("§cNo economy provider registered - this is an EverNifeCore platform wiring bug");
                return;
            }
            economy.warmUp();
        });
    }

    public void onUnload() {
        PlayerController.shutdown(); //flush dirty players + close storage backends
    }

    @ECPlugin.Reload
    public void onReload(){
        ConfigManager.initialize(ecPluginData);
        ConfigManager.reloadCooldownConfig();
    }

}
