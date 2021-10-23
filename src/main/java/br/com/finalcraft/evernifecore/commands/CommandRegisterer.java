package br.com.finalcraft.evernifecore.commands;

import br.com.finalcraft.evernifecore.commands.debug.CMDBiomeInfo;
import br.com.finalcraft.evernifecore.commands.debug.CMDBlockInfo;
import br.com.finalcraft.evernifecore.commands.debug.CMDItemInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.misc.CMDCooldown;
import br.com.finalcraft.evernifecore.commands.misc.CMDGetName;
import br.com.finalcraft.evernifecore.commands.misc.CMDGetUUID;
import br.com.finalcraft.evernifecore.commands.misc.CMDSvInfo;
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

    }

}
