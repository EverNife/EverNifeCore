package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import jakarta.annotation.Nonnull;
import org.bukkit.entity.Player;

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
