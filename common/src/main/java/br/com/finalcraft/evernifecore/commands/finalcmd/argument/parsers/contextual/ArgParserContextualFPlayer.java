package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import jakarta.annotation.Nonnull;

public class ArgParserContextualFPlayer extends ArgParserContextual<FPlayer> {

    public ArgParserContextualFPlayer(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<FPlayer> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of((FPlayer) call.getSender());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }
}
