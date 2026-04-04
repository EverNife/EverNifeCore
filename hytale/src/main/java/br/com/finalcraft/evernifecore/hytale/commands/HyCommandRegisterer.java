package br.com.finalcraft.evernifecore.hytale.commands;

import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.hytale.commands.debug.CMDItemInfo;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

public class HyCommandRegisterer {

    public static void registerCommands(JavaPlugin pluginInstance) {

        FinalCMDManager.registerCommand(pluginInstance, CMDItemInfo.class);

    }

}
