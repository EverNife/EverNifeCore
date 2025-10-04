package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.version.FCJavaVersion;
import br.com.finalcraft.evernifecore.version.MCVersion;
import br.com.finalcraft.evernifecore.version.ServerType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class CMDSvInfo {

    @FinalCMD(
            aliases = {"serverinfo","svinfo"}
    )
    public void onCommand(CommandSender sender) {
        sender.sendMessage("§a-------- SV_INFO --------");
        sender.sendMessage("");
        sender.sendMessage(" §a - McVersion: §e" + MCVersion.getCurrent().name());
        sender.sendMessage(" §a - SvPlatform: §e" + Bukkit.getName());
        sender.sendMessage(" §a - JavaVersion: §e" + FCJavaVersion.getCurrent().getName());
        if (ServerType.isEverNifePersonalServer()){
            sender.sendMessage(" §d - ServerName: §a" + ServerType.getCurrent().getName());
        }
        sender.sendMessage(" §a - EverNifeCore: " + EverNifeCore.instance.getDescription().getVersion());
        sender.sendMessage("");
        sender.sendMessage("§a-------- SV_INFO --------");
    }
}
