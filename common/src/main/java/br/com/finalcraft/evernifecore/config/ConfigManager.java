package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.FCDefaultExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.pageviewer.nav.PVExtraMessages;
import br.com.finalcraft.evernifecore.pageviewer.theme.ClassicPageTheme;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;

public class ConfigManager {

    private static Config mainConfig;
    private static Config cooldowns;

    public static Config getMainConfig(){
        return mainConfig;
    }

    public static Config getCooldowns(){
        return cooldowns;
    }

    public static void initialize(ECPluginData ecPluginData){
        mainConfig      = ConfigFactory.open(ecPluginData, "config.yml");
        cooldowns       = ConfigFactory.open(ecPluginData, "Cooldowns.yml");

        ECSettings.initialize();

        FCLocaleManager.loadLocale(ecPluginData,
                FCMessageUtil.class,
                FCTimeFrame.class,
                Cooldown.class,
                HelpContext.class,
                FCDefaultExecutor.class,
                ClassicPageTheme.class,
                PVExtraMessages.class
        );

        FCLocaleManager.updateEverNifeCoreLocale();

        PlayerController.initialize();
    }

    public static void reloadCooldownConfig(){
        cooldowns = ConfigFactory.open(EverNifeCore.instance.getEcPluginData(), "Cooldowns.yml");
        Cooldown.initialize();
    }

}
