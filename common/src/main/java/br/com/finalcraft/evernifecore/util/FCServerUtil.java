package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;

import java.util.List;

public class FCServerUtil {

    public static List<FPlayer> getOnlinePlayers(){
        return EverNifeCore.getPlatform().getOnlinePlayers();
    }

    public static boolean makeConsoleExecuteCommand(String theCommand) {
        return EverNifeCore.getPlatform().makeConsoleExecuteCommand(theCommand);
    }

    public static boolean makePlayerExecuteCommand(FCommandSender sender, String theCommand) {
        return EverNifeCore.getPlatform().makePlayerExecuteCommand(sender, theCommand);
    }

}
