package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import jakarta.annotation.Nonnull;

public class ArgParserContextualFPlayer extends ArgParserContextual<FPlayer> {

    public ArgParserContextualFPlayer(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public FPlayer parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return (FPlayer) sender;
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }
}
