package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import jakarta.annotation.Nonnull;

public class ArgParserContextualHytaleFPlayer extends ArgParserContextual<HytaleFPlayer> {

    public ArgParserContextualHytaleFPlayer(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public HytaleFPlayer parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return (HytaleFPlayer) sender;
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }

}
