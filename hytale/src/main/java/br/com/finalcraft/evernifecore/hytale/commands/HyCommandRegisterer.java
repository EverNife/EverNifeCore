package br.com.finalcraft.evernifecore.hytale.commands;

import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.hytale.commands.debug.CMDItemInfo;

public class HyCommandRegisterer {

    public static void registerCommands(ECPluginData ecPluginData) {

        FinalCMDManager.registerCommand(ecPluginData, CMDItemInfo.class);

    }

}
