package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.ICustomFinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.contexts.CustomizeContext;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import jakarta.annotation.Nonnull;
import org.bukkit.command.CommandSender;

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
            aliases = "",
            desc = "Alias for the command: [%the_command%]"
    )
    public void onAliasExecution(CommandSender sender, MultiArgumentos argumentos) {
        FCBukkitUtil.makePlayerExecuteCommand(sender,
                theCommand + " " + String.join(" ", argumentos.getStringArgs())
        );
    }

    @Override
    public void customize(@Nonnull CustomizeContext context) {
        context.getFinalCMDData().setLabels(this.aliases);
        context.replace("%the_command%", theCommand);
    }

}
