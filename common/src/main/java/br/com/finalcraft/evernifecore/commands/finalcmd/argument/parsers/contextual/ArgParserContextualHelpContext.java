package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import jakarta.annotation.Nonnull;

public class ArgParserContextualHelpContext extends ArgParserContextual<HelpContext> {

    public ArgParserContextualHelpContext(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public HelpContext parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return argContext.getHelpContext();
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }
}
