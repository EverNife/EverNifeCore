package br.com.finalcraft.evernifecore.commands;

import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.misc.*;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

public class CommandRegisterer {

    public static void registerCommands(ECPluginData pluginInstance) {

        FinalCMDManager.registerCommand(pluginInstance, CoreCommand.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDCooldown.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDSvInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDGetName.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDGetUUID.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDECLocale.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDECDynamicCommand.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDList.class);

    }

}
