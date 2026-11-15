package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.booster.command.BoosterCommandNode;
import br.com.finalcraft.evernifecore.quest.command.ArgParserQuest;
import br.com.finalcraft.evernifecore.quest.command.QuestCommandNode;
import br.com.finalcraft.evernifecore.quest.screen.QuestJournalScreen;
import br.com.finalcraft.evernifecore.quest.screen.QuestOfferScreen;
import br.com.finalcraft.evernifecore.group.command.CoreGroupCommands;
import br.com.finalcraft.evernifecore.dialogue.command.CoreDialogueCommands;
import br.com.finalcraft.evernifecore.group.command.GroupCommandNode;
import br.com.finalcraft.evernifecore.logic.audit.command.AuditCommandNode;
import br.com.finalcraft.evernifecore.logic.reward.command.RewardCommandNode;
import br.com.finalcraft.evernifecore.logic.command.CoreLogicCommands;
import br.com.finalcraft.evernifecore.logic.delivery.command.CoreDeliveriesCommands;
import br.com.finalcraft.evernifecore.logic.delivery.command.DeliveriesCommandNode;
import br.com.finalcraft.evernifecore.menu.MenuCommandNode;
import br.com.finalcraft.evernifecore.menu.MenuRegistry;
import br.com.finalcraft.evernifecore.menu.TransactionResult;
import br.com.finalcraft.evernifecore.menu.Transactions;
import br.com.finalcraft.evernifecore.region.command.CoreRegionCommands;
import br.com.finalcraft.evernifecore.region.command.RegionCommandNode;
import br.com.finalcraft.evernifecore.region.selection.SelectionSession;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
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
import br.com.finalcraft.evernifecore.screen.Choice;
import br.com.finalcraft.evernifecore.screen.ConfirmScreen;
import br.com.finalcraft.evernifecore.screen.DefaultIcons;
import br.com.finalcraft.evernifecore.screen.DeliveriesScreen;
import br.com.finalcraft.evernifecore.screen.RankingScreen;
import br.com.finalcraft.evernifecore.screen.command.CoreScreenCommands;
import br.com.finalcraft.evernifecore.screen.prompt.PromptRegistry;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;

public class ConfigManager {

    /** Every core class with locale messages: what the boot scans, so no text of the core reads null in production. */
    static final Class<?>[] CORE_LOCALE_CLASSES = {
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
            RewardCommandNode.class,
            BoosterCommandNode.class,
            DeliveriesCommandNode.class,
            CoreDeliveriesCommands.class,
            DefaultIcons.class,
            CoreScreenCommands.class,
            PromptRegistry.class,
            Choice.class,
            ConfirmScreen.class,
            DeliveriesScreen.class,
            RankingScreen.class,
            QuestCommandNode.class,
            ArgParserQuest.class,
            QuestOfferScreen.class,
            QuestJournalScreen.class,
            MenuCommandNode.class,
            MenuRegistry.class,
            Transactions.class,
            TransactionResult.class
    };

    private static Config mainConfig;

    public static Config getMainConfig(){
        return mainConfig;
    }

    public static void initialize(ECPluginData ecPluginData){
        mainConfig      = ConfigFactory.open(ecPluginData, "config.yml");

        ECSettings.initialize();

        FCLocaleManager.loadLocale(ecPluginData, CORE_LOCALE_CLASSES);

        FCLocaleManager.updateEverNifeCoreLocale();

        PlayerController.initialize();
    }

}
