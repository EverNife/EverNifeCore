package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.implementation;

import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.IPlatformCMD;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleUtil;
import com.hypixel.hytale.server.core.command.system.*;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class HyFinalCMDPluginCommand extends AbstractCommand implements IPlatformCMD {

    public final FinalCMDPluginCommand finalCMDPluginCommand;

    public JavaPlugin getJavaPlugin(){
        return (JavaPlugin) this.finalCMDPluginCommand.getOwningPlugin().getPlugin();
    }

    public HyFinalCMDPluginCommand(FinalCMDPluginCommand finalCMDPluginCommand) {
        this(finalCMDPluginCommand, (JavaPlugin) finalCMDPluginCommand.getOwningPlugin().getPlugin());
    }

    HyFinalCMDPluginCommand(FinalCMDPluginCommand finalCMDPluginCommand, CommandOwner owner) {
        super(finalCMDPluginCommand.getPrimaryLabel(), null);
        //the server only keeps the aliases it is told about before registration
        this.addAliases(finalCMDPluginCommand.getExtraLabels());
        //the @FinalCMD permission is checked by the core; the node the server would generate is one more nobody grants
        this.requireNoPermission();
        this.finalCMDPluginCommand = finalCMDPluginCommand;
        this.setOwner(owner);
    }

    @Override
    protected @org.jspecify.annotations.Nullable CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @org.jspecify.annotations.Nullable CompletableFuture<Void> acceptCall(@Nonnull CommandSender sender, @Nonnull ParserContext parserContext, @Nonnull ParseResult parseResult) {
        if (!getJavaPlugin().isEnabled()) {
            throw new RuntimeException("Cannot execute command '" + parserContext.getInputString().split(" ")[0] + "' in plugin " + getJavaPlugin().getName() + " - plugin is disabled.");
        }
        dispatch(sender, parserContext.getInputString());
        return CompletableFuture.completedFuture(null);
    }

    void dispatch(@Nonnull CommandSender sender, String inputString) {
        String[] inputStrings = inputString.split(" ");
        String commandLabel = inputStrings[0];
        String[] args = Arrays.copyOfRange(inputStrings, 1, inputStrings.length);

        try {
            finalCMDPluginCommand.getExecutor().onCommand(FCHytaleUtil.adapt(sender), commandLabel, args);
        } catch (Throwable ex) {
            throw new RuntimeException("Unhandled exception executing command '" + commandLabel + "' in plugin " + finalCMDPluginCommand.getOwningPlugin().getMetaInfo().getName(), ex);
        }
    }

}
