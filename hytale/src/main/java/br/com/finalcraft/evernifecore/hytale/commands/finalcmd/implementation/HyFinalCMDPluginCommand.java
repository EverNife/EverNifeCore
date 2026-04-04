package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.implementation;

import br.com.finalcraft.evernifecore.api.hytale.HytaleFCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.IPlatformCMD;
import com.hypixel.hytale.server.core.command.system.*;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class HyFinalCMDPluginCommand extends AbstractCommand implements IPlatformCMD {

    public final FinalCMDPluginCommand finalCMDPluginCommand;

    public JavaPlugin getJavaPlugin(){
        return (JavaPlugin) this.finalCMDPluginCommand.getOwningPlugin().getPlugin();
    }

    public HyFinalCMDPluginCommand(FinalCMDPluginCommand finalCMDPluginCommand) {
        super(finalCMDPluginCommand.getPrimaryLabel(), null);
        this.finalCMDPluginCommand = finalCMDPluginCommand;
        this.setOwner(this.getJavaPlugin());
    }

    public boolean registerCommand() {
        return this.getJavaPlugin().getCommandRegistry()
                .registerCommand(this)
                .isRegistered();
    }

    @Override
    protected @org.jspecify.annotations.Nullable CompletableFuture<Void> execute(@NonNull CommandContext ctx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @org.jspecify.annotations.Nullable CompletableFuture<Void> acceptCall(@NonNull CommandSender sender, @NonNull ParserContext parserContext, @NonNull ParseResult parseResult) {
        String[] inputStrings = parserContext.getInputString().split(" ");
        String commandLabel = inputStrings[0];
        String[] args = Arrays.copyOfRange(inputStrings, 1, inputStrings.length);

        if (!getJavaPlugin().isEnabled()) {
            throw new RuntimeException("Cannot execute command '" + commandLabel + "' in plugin " + getJavaPlugin().getName() + " - plugin is disabled.");
        }

        try {
            finalCMDPluginCommand.getExecutor().onCommand(HytaleFCommandSender.of(sender), commandLabel, args);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable ex) {
            throw new RuntimeException("Unhandled exception executing command '" + commandLabel + "' in plugin " + getJavaPlugin().getName(), ex);
        }
    }

}
