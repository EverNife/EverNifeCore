package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import com.google.common.collect.ImmutableList;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class ArgParser<T extends Object> implements ITabParser {

    protected final ArgInfo argInfo;

    protected transient ArgContext argContext = null; //This value will be populated on every command execution

    public ArgParser(ArgInfo argInfo) {
        this.argInfo = argInfo;
    }

    public ArgInfo getArgInfo() {
        return argInfo;
    }

    public void setArgContext(ArgContext argContext) {
        this.argContext = argContext;
    }

    public ArgContext getArgContext() {
        return argContext;
    }

    public abstract T parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException;

    @Override
    public @NotNull List<String> tabComplete(TabContext tabContext) {
        return ImmutableList.of();
    }

    public static class ArgContext {
        private final MultiArgumentos argumentos;

        public ArgContext(MultiArgumentos argumentos) {
            this.argumentos = argumentos;
        }

        public MultiArgumentos getArgumentos() {
            return argumentos;
        }
    }

}
