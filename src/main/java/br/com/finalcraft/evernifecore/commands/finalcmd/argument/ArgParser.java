package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import com.google.common.collect.ImmutableList;
import org.bukkit.command.CommandSender;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
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

    public abstract T parserArgument(@Nonnull CommandSender sender, @Nonnull Argumento argumento) throws ArgParseException;

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        return ImmutableList.of();
    }

    public static class ArgContext {
        private final MultiArgumentos argumentos;
        private final LinkedHashMap<Class, Object> parsedArgs;
        private boolean shouldMoveArgIndex = true;

        public ArgContext(MultiArgumentos argumentos, LinkedHashMap<Class, Object> parsedArgs) {
            this.argumentos = argumentos;
            this.parsedArgs = parsedArgs;
        }

        public <T> @Nullable T getPreviouslyParsedArg(Class<T> clazz){
            return (T) parsedArgs.get(clazz);
        }

        public LinkedHashMap<Class, Object> getParsedArgs() {
            return parsedArgs;
        }

        public MultiArgumentos getArgumentos() {
            return argumentos;
        }

        public boolean shouldMoveArgIndex() {
            return shouldMoveArgIndex;
        }

        public void setShouldMoveArgIndex(boolean shouldMoveArgIndex) {
            this.shouldMoveArgIndex = shouldMoveArgIndex;
        }

    }

}
