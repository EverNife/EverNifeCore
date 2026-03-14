package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;

public class CMDGetUUID {

    @FinalCMD(
            aliases = {"getuuid", "uuidof"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_UUIDOF
    )
    public void onCommand(FCommandSender sender, @Arg(name = "<PlayerUUID>") PlayerData playerData) {
        sender.sendMessage("§a [" + playerData.getName() + "] --> §e" + playerData.getUniqueId());
    }

}
