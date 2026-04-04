package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import jakarta.annotation.Nonnull;

public class ArgParserContextualFCommandSender extends ArgParserContextual<FCommandSender> {

    public ArgParserContextualFCommandSender(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public FCommandSender parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return sender;
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }

    @Override
    public int getPriority() {
        return -100; //Should be lower than FPlayer
    }
}
