package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.ICustomFinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.SubCMDData;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CMDAlias implements ICustomFinalCMD {

    private final String[] aliases;
    private final String theCommand;

    public CMDAlias(String alias, String theCommand) {
        this.aliases = new String[]{alias};
        this.theCommand = theCommand;
    }

    public CMDAlias(String[] aliases, String theCommand) {
        this.aliases = aliases;
        this.theCommand = theCommand;
    }

    @FinalCMD(
            aliases = ""
    )
    public void onAliasExecution(CommandSender sender, MultiArgumentos argumentos) {
        FCBukkitUtil.makePlayerExecuteCommand(sender,
                theCommand + " " + String.join(" ", argumentos.getStringArgs())
        );
    }

    @Override
    public void customize(@NotNull FinalCMDData finalCMDData, @NotNull List<SubCMDData> subCMDData) {
        finalCMDData.labels(this.aliases);
        finalCMDData.desc("Alias for the command: [" + theCommand + "]");
    }
}
