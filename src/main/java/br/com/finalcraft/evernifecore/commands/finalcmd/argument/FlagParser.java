package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import com.google.common.collect.ImmutableList;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class FlagParser<T extends Object> implements ITabParser {

    protected final ArgInfo argInfo;

    public FlagParser(ArgInfo argInfo) {
        this.argInfo = argInfo;
    }

    public ArgInfo getArgInfo() {
        return argInfo;
    }

    public abstract T parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException;

    @Override
    public @NotNull List<String> tabComplete(Context context) {
        return ImmutableList.of();
    }
}
