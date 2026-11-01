package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.quest.command.ArgParserQuest;
import br.com.finalcraft.evernifecore.quest.command.QuestCommandNode;
import br.com.finalcraft.evernifecore.quest.screen.QuestOfferScreen;
import br.com.finalcraft.evernifecore.group.command.CoreGroupCommands;
import br.com.finalcraft.evernifecore.dialogue.command.CoreDialogueCommands;
import br.com.finalcraft.evernifecore.group.command.GroupCommandNode;
import br.com.finalcraft.evernifecore.logic.audit.command.AuditCommandNode;
import br.com.finalcraft.evernifecore.logic.command.CoreLogicCommands;
import br.com.finalcraft.evernifecore.logic.delivery.command.CoreDeliveriesCommands;
import br.com.finalcraft.evernifecore.logic.delivery.command.DeliveriesCommandNode;
import br.com.finalcraft.evernifecore.region.command.CoreRegionCommands;
import br.com.finalcraft.evernifecore.region.command.RegionCommandNode;
import br.com.finalcraft.evernifecore.region.selection.SelectionSession;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.FCDefaultExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.Cooldowns;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.pageviewer.nav.PVExtraMessages;
import br.com.finalcraft.evernifecore.pageviewer.theme.ClassicPageTheme;
import br.com.finalcraft.evernifecore.screen.ConfirmScreen;
import br.com.finalcraft.evernifecore.screen.DefaultIcons;
import br.com.finalcraft.evernifecore.screen.DeliveriesScreen;
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
                PVExtraMessages.class,
                SelectionTool.class,
                SelectionSession.class,
                RegionCommandNode.class,
                CoreRegionCommands.class,
                GroupCommandNode.class,
                CoreGroupCommands.class,
                CoreDialogueCommands.class,
                CoreLogicCommands.class,
                AuditCommandNode.class,
                DeliveriesCommandNode.class,
                CoreDeliveriesCommands.class,
                DefaultIcons.class,
                ConfirmScreen.class,
                DeliveriesScreen.class,
                QuestCommandNode.class,
                ArgParserQuest.class,
                QuestOfferScreen.class
        );

        FCLocaleManager.updateEverNifeCoreLocale();

        PlayerController.initialize();
    }

    public static void reloadCooldownConfig(){
        cooldowns = ConfigFactory.open(EverNifeCore.instance.getEcPluginData(), "Cooldowns.yml");
        Cooldowns.loadServerFile();
    }

}
