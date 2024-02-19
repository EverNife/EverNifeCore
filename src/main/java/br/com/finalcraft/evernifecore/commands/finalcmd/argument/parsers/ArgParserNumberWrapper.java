package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ArgParserNumberWrapper extends ArgParser<NumberWrapper> {

    private final ArgParserNumber ARG_PARSER_NUMBER;

    public ArgParserNumberWrapper(ArgInfo argInfo) {
        super(argInfo);

        this.ARG_PARSER_NUMBER = new ArgParserNumber(argInfo);
    }

    @Override
    public NumberWrapper parserArgument(@NotNull CommandSender sender, @NotNull Argumento argumento) throws ArgParseException {

        Number number = ARG_PARSER_NUMBER.parserArgument(sender, argumento);

        return number == null ? null : NumberWrapper.of(number);
    }

    @Override
    public @NotNull List<String> tabComplete(TabContext tabContext) {
        return ARG_PARSER_NUMBER.tabComplete(tabContext);
    }

}
