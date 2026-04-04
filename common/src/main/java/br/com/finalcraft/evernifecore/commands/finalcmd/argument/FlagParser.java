package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

public abstract class FlagParser<T extends Object> implements ITabParser {

    protected final ArgInfo argInfo;

    public FlagParser(ArgInfo argInfo) {
        this.argInfo = argInfo;
    }

    public ArgInfo getArgInfo() {
        return argInfo;
    }

    public abstract T parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender, @Nonnull Argumento argumento) throws ArgParseException;

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        return new ArrayList<>();
    }
}
