package br.com.finalcraft.evernifecore.commands;

import br.com.finalcraft.evernifecore.commands.debug.CMDBiomeInfo;
import br.com.finalcraft.evernifecore.commands.debug.CMDBlockInfo;
import br.com.finalcraft.evernifecore.commands.debug.CMDItemInfo;
import br.com.finalcraft.evernifecore.commands.debug.CMDProtectionTest;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.misc.*;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandRegisterer {

    public static void registerCommands(JavaPlugin pluginInstance) {

        FinalCMDManager.registerCommand(pluginInstance, CoreCommand.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDCooldown.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDSvInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDGetName.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDGetUUID.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDBiomeInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDBlockInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDItemInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDECLocale.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDProtectionTest.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDECDynamicCommand.class);

        //FinalCMDManager.registerCommand(pluginInstance, CMDEcoTest.class);
    }

}
