package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import com.google.common.collect.ImmutableList;
import org.bukkit.command.CommandSender;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class FlagParser<T extends Object> implements ITabParser {

    protected final ArgInfo argInfo;

    public FlagParser(ArgInfo argInfo) {
        this.argInfo = argInfo;
    }

    public ArgInfo getArgInfo() {
        return argInfo;
    }

    public abstract T parserArgument(@Nonnull CommandSender sender, @Nonnull Argumento argumento) throws ArgParseException;

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        return ImmutableList.of();
    }
}
