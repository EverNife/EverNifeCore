package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class CMDGetName {

    @FinalCMD(
            aliases = {"getname", "nameof"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_NAMEOF
    )
    public void onCommand(CommandSender sender, String label, MultiArgumentos argumentos) {
        if (argumentos.get(0).isEmpty()) sender.sendMessage("/" + label + " <UUID>");
        try {
            UUID uuid = UUID.fromString(argumentos.getStringArg(0));
            PlayerData playerData = PlayerController.getPlayerData(uuid);
            if (playerData == null){
                sender.sendMessage("§cNenhum jogador encontrado com a UUID: §e" + uuid);
            }else {
                sender.sendMessage("§a [" + uuid + "] pertence ao jogador §e" + playerData.getPlayerName());
            }
        }catch (Exception e){
            sender.sendMessage("§cA UUID inserida é Inválida!");
        }
    }
}
