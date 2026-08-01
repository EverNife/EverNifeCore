package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFCommandSender;
import jakarta.annotation.Nonnull;

public class ArgParserContextualHytaleFCommandSender extends ArgParserContextual<HytaleFCommandSender> {

    public ArgParserContextualHytaleFCommandSender(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<HytaleFCommandSender> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of((HytaleFCommandSender) call.getSender());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }

}
