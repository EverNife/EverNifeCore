package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class CMDGetName {

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §7[§2%argumento%§7]§c needs to be a valid UUID!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §7[§2%argumento%§7]§c precisa ser uma UUID válida!")
    private static LocaleMessage NEEDS_TO_BE_UUID;

    @FinalCMD(
            aliases = {"getname", "nameof"},
            usage = "%label% <UUID>",
            permission = PermissionNodes.EVERNIFECORE_COMMAND_NAMEOF
    )
    public void onCommand(CommandSender sender, MultiArgumentos argumentos, HelpLine helpLine) {
        if (argumentos.emptyArgs(0)){
            helpLine.sendTo(sender);
            return;
        }

        UUID uuid = argumentos.get(0).getUUID();

        if (uuid == null){
            NEEDS_TO_BE_UUID
                    .addPlaceholder("%argumento%", argumentos.get(0))
                    .send(sender);
            return;
        }

        PlayerData playerData = argumentos.get(0).getPlayerData();

        if (playerData == null){
            FCMessageUtil.playerDataNotFound(sender, argumentos.getStringArg(0));
            return;
        }

        sender.sendMessage("§a [" + uuid + "] --> §e" + playerData.getPlayerName());
    }
}
