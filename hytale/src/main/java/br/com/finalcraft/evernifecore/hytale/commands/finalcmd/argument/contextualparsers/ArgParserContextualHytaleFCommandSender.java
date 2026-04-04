package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.hytale.HytaleFCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import jakarta.annotation.Nonnull;

public class ArgParserContextualHytaleFCommandSender extends ArgParserContextual<HytaleFCommandSender> {

    public ArgParserContextualHytaleFCommandSender(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public HytaleFCommandSender parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return (HytaleFCommandSender) sender;
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }

}
