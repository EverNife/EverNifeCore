package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.version.FCPlatformType;
import br.com.finalcraft.everylibs.version.FCJavaVersion;

public class CMDSvInfo {

    @FinalCMD(
            aliases = {"serverinfo","svinfo"}
    )
    public void onCommand(FCommandSender sender) {
        sender.sendMessage("§a-------- SV_INFO --------");
        sender.sendMessage("");
        sender.sendMessage(" §a - JavaVersion: §e" + FCJavaVersion.getCurrent().getName());
        //Detected from the classpath, not from the running platform provider: printing it is what
        //makes a misdetection visible on a real server instead of only in the rendered messages.
        sender.sendMessage(" §a - Platform: §e" + FCPlatformType.getCurrent().getName());
        sender.sendMessage(" §a - EverNifeCore: " + EverNifeCore.instance.getEcPluginData().getMetaInfo().getVersion());
        sender.sendMessage("");
        sender.sendMessage("§a-------- SV_INFO --------");
    }
}
