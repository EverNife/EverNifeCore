package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.FCDefaultExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.pageviwer.PageViewer;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCHytaleUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.util.Arrays;
import java.util.Objects;

public class ConfigManager {

    private static Config mainConfig;
    private static Config cooldowns;

    public static Config getMainConfig(){
        return mainConfig;
    }
    public static Config getCooldowns(){
        return cooldowns;
    }

    public static void initialize(JavaPlugin instance){

        mainConfig      = new Config(instance,"config.yml");
        cooldowns       = new Config(instance,"Cooldowns.yml");

        ECSettings.initialize();

        FCLocaleManager.loadLocale(EverNifeCore.instance,
                Arrays.asList(
                        FCHytaleUtil.class,
                        FCMessageUtil.class,
                        FCTimeFrame.class,
                        Cooldown.class,
                        HelpContext.class,
                        FCDefaultExecutor.class,
                        PageViewer.class,
                        ECSettings.PAGEVIEWERS_FULL_LOCALIZATION ? PageViewer.PVExtraMessages.class : null
                ).stream().filter(Objects::nonNull).toArray(Class[]::new)
        );
        FCLocaleManager.updateEverNifeCoreLocale();

        PlayerController.initialize();
    }

    public static void reloadCooldownConfig(){
        cooldowns = new Config(EverNifeCore.instance,"Cooldowns.yml");
        Cooldown.initialize();
    }

}
