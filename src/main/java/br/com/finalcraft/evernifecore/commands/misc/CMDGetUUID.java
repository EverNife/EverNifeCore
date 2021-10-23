package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.command.CommandSender;

public class CMDGetUUID {

    @FinalCMD(
            aliases = {"getuuid", "uuidof"},
            usage = "%label% <playerName>",
            permission = PermissionNodes.EVERNIFECORE_COMMAND_UUIDOF
    )
    public void onCommand(CommandSender sender, MultiArgumentos argumentos, HelpLine helpLine) {
        if (argumentos.emptyArgs(0)){
            helpLine.sendTo(sender);
            return;
        }

        PlayerData playerData = argumentos.get(0).getPlayerData();

        if (playerData == null){
            FCMessageUtil.playerDataNotFound(sender, argumentos.getStringArg(0));
            return;
        }

        sender.sendMessage("§a [" + playerData.getPlayerName() + "] --> §e" + playerData.getUniqueId());
    }
}
