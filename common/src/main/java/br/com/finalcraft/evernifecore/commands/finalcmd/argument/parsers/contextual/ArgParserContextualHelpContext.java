package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import jakarta.annotation.Nonnull;

public class ArgParserContextualHelpContext extends ArgParserContextual<HelpContext> {

    public ArgParserContextualHelpContext(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<HelpContext> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of(call.getHelpContext());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }
}
