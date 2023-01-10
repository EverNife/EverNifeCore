package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.autoupdater.SpigotUpdateChecker;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.FCDefaultExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.gui.layout.LayoutIcon;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.pageviwer.PageViewer;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {

    private static Config mainConfig;
    private static Config playerUUIDs;
    private static Config cooldowns;

    public static Config getMainConfig(){
        return mainConfig;
    }
    public static Config getPlayerUUIDs(){
        return playerUUIDs;
    }
    public static Config getCooldowns(){
        return cooldowns;
    }

    public static void initialize(JavaPlugin instance){

        mainConfig      = new Config(instance,"config.yml");
        playerUUIDs     = new Config(instance,"UUIDs.yml");
        cooldowns       = new Config(instance,"Cooldowns.yml");

        UUIDsController.loadUUIDs();
        ECSettings.initialize();

        FCLocaleManager.loadLocale(EverNifeCore.instance,
                FCMessageUtil.class,
                FCTimeFrame.class,
                Cooldown.class,
                FCBukkitUtil.class,
                SpigotUpdateChecker.class,
                HelpContext.class,
                FCDefaultExecutor.class,
                PageViewer.class,
                LayoutIcon.DefaultIcons.class
        );
        FCLocaleManager.updateEverNifeCoreLocale();

        PlayerController.initialize();
    }

    public static void reloadCooldownConfig(){
        cooldowns = new Config(EverNifeCore.instance,"Cooldowns.yml");
        Cooldown.initialize();
    }

}
