package br.com.finalcraft.evernifecore.commands.finalcmd;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public abstract class FinalCMDExecutor implements IFinalCMDExecutor{

    private final FinalCMDBuilder finalCmdBuilder;

    public FinalCMDExecutor() {
        this(new FinalCMDBuilder(false));
    }

    public FinalCMDExecutor(FinalCMDBuilder finalCmdBuilder) {
        this.finalCmdBuilder = finalCmdBuilder;
    }

    public FinalCMDExecutor(String... moreAliases) {
        this(new FinalCMDBuilder(false, moreAliases));
    }

    public boolean shouldRegister(){
        return true;
    }

    public abstract void onCommand(CommandSender sender, String label, MultiArgumentos argumentos);

    @Override
    @CMDBuilder()
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MultiArgumentos argumentos = new MultiArgumentos(args, finalCmdBuilder.flagByDefault);
        onCommand(sender,label,argumentos);
        return true;
    }

    public List<String> getAllAliases(){
        return finalCmdBuilder.aliases;
    }

    public String getCommandName(){
        return finalCmdBuilder.name;
    }

    public FinalCMDBuilder getFinalCmdBuilder() {
        return finalCmdBuilder;
    }
}
