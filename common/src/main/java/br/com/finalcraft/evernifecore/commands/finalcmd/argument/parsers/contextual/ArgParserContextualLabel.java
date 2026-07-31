package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import jakarta.annotation.Nonnull;

public class ArgParserContextualLabel extends ArgParserContextual<String> {

    public ArgParserContextualLabel(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<String> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of(call.getPath().getLabel());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }
}
