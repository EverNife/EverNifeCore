package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import jakarta.annotation.Nonnull;

public class ArgParserContextualHytaleFPlayer extends ArgParserContextual<HytaleFPlayer> {

    public ArgParserContextualHytaleFPlayer(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<HytaleFPlayer> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of((HytaleFPlayer) call.getSender());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }

}
