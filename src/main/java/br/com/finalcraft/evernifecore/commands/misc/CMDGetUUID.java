package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.command.CommandSender;

public class CMDGetUUID {

    @FinalCMD(
            aliases = {"getuuid", "uuidof"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_UUIDOF
    )
    public void onCommand(CommandSender sender, String label, MultiArgumentos argumentos) {
        if (argumentos.get(0).isEmpty()) sender.sendMessage("/" + label + " <Player>");
        PlayerData playerData = argumentos.get(0).getPlayerData();
        if (playerData == null){
            FCMessageUtil.playerDataNotFound(sender, argumentos.getStringArg(0));
            return;
        }
        sender.sendMessage("§a [" + playerData.getUniqueId() + "] pertence ao jogador §e" + playerData.getPlayerName());
    }
}
