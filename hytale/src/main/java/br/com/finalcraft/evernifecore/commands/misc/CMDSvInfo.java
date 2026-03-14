package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.version.FCJavaVersion;

public class CMDSvInfo {

    @FinalCMD(
            aliases = {"serverinfo","svinfo"}
    )
    public void onCommand(FCommandSender sender) {
        sender.sendMessage("§a-------- SV_INFO --------");
        sender.sendMessage("");
        sender.sendMessage(" §a - JavaVersion: §e" + FCJavaVersion.getCurrent().getName());
        sender.sendMessage(" §a - EverNifeCore: " + EverNifeCore.instance.getManifest().getVersion());
        sender.sendMessage("");
        sender.sendMessage("§a-------- SV_INFO --------");
    }
}
