package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import jakarta.annotation.Nonnull;

public class ArgParserContextualHelpLine extends ArgParserContextual<HelpLine> {

    public ArgParserContextualHelpLine(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<HelpLine> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of(call.getHelpLine());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }
}
