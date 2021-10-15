package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDBuilder;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDExecutor;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import org.bukkit.command.CommandSender;

public class CMDAliase extends FinalCMDExecutor {

    private final String theCommand;

    public CMDAliase(String command) {
        super();
        this.theCommand = command;
    }

    public CMDAliase(String command, String... moreAliases) {
        super(new FinalCMDBuilder().setAliases(moreAliases));
        this.theCommand = command;
    }

    @Override
    public void onCommand(CommandSender sender, String label, MultiArgumentos argumentos) {
        FCBukkitUtil.makePlayerExecuteCommand(sender, theCommand + " " + String.join(" ", argumentos.getStringArgs()));
    }
}
