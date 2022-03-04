package br.com.finalcraft.evernifecore.commands;

import br.com.finalcraft.evernifecore.commands.debug.CMDBiomeInfo;
import br.com.finalcraft.evernifecore.commands.debug.CMDBlockInfo;
import br.com.finalcraft.evernifecore.commands.debug.CMDItemInfo;
import br.com.finalcraft.evernifecore.commands.debug.CMDTestMultiCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserIPlayerData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserNumber;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserString;
import br.com.finalcraft.evernifecore.commands.misc.*;
import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandRegisterer {

    public static void registerCommands(JavaPlugin pluginInstance) {
        addDefaultParsers();

        FinalCMDManager.registerCommand(pluginInstance, CoreCommand.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDCooldown.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDSvInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDGetName.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDGetUUID.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDBiomeInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDBlockInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDItemInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDTestMultiCommand.class);
        FinalCMDManager.registerCommand(pluginInstance, new CMDAlias("xabulings","testcmd"));

    }

    private static void addDefaultParsers(){
        //The ArgParsers bellow will be available to all ECPlugins
        ArgParserManager.addGlobalParser(String.class, ArgParserString.class);
        ArgParserManager.addGlobalParser(Integer.class, ArgParserNumber.class);
        ArgParserManager.addGlobalParser(Float.class, ArgParserNumber.class);
        ArgParserManager.addGlobalParser(Double.class, ArgParserNumber.class);
        ArgParserManager.addGlobalParser(Player.class, ArgParserPlayer.class);
        ArgParserManager.addGlobalParser(IPlayerData.class, ArgParserIPlayerData.class);
    }

}
