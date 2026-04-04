package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import com.hypixel.hytale.server.core.entity.entities.Player;
import jakarta.annotation.Nonnull;

public class ArgParserContextualPlayer extends ArgParserContextual<Player> {

    public ArgParserContextualPlayer(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public Player parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return sender.getDelegate(Player.class);
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }
}
