package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.version.MCVersion;
import br.com.finalcraft.evernifecore.version.ServerType;
import org.bukkit.command.CommandSender;

public class CMDSvInfo {

    @FinalCMD(
            aliases = {"serverinfo","svinfo"}
    )
    public void onCommand(CommandSender sender, String label, MultiArgumentos argumentos) {
        sender.sendMessage("§a-------- SV_INFO --------");
        sender.sendMessage("");
        sender.sendMessage(" §a - McVersion: " + MCVersion.getCurrent().name());
        if (ServerType.getCurrent() != ServerType.UNKNOWN) sender.sendMessage(" §a - ServerType: " + ServerType.getCurrent().getName());
        sender.sendMessage(" §a - EverNifeCore: " + EverNifeCore.instance.getDescription().getVersion());
        sender.sendMessage("");
        sender.sendMessage("§a-------- SV_INFO --------");
    }
}
