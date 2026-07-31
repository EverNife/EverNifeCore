package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.everylibs.util.numberwrapper.NumberWrapper;
import jakarta.annotation.Nonnull;

import java.util.List;

public class ArgParserNumberWrapper extends ArgParser<NumberWrapper> {

    protected final ArgParserNumber ARG_PARSER_NUMBER;

    public ArgParserNumberWrapper(ArgInfo argInfo) {
        super(argInfo);

        this.ARG_PARSER_NUMBER = new ArgParserNumber(argInfo);
    }

    @Override
    public ParseResult<NumberWrapper> parse(@Nonnull ParseCall call) {
        return ARG_PARSER_NUMBER.parse(call).map(NumberWrapper::of);
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        return ARG_PARSER_NUMBER.tabComplete(tabContext);
    }

}
