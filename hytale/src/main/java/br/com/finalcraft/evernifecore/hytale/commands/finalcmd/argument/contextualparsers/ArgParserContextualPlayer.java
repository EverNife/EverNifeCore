package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import com.hypixel.hytale.server.core.entity.entities.Player;
import jakarta.annotation.Nonnull;

public class ArgParserContextualPlayer extends ArgParserContextual<Player> {

    public ArgParserContextualPlayer(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<Player> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of(call.getSender().getDelegate(Player.class));
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }
}
