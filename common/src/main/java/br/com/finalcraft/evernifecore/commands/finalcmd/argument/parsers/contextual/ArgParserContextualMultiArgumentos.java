package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import jakarta.annotation.Nonnull;

public class ArgParserContextualMultiArgumentos extends ArgParserContextual<MultiArgumentos> {

    public ArgParserContextualMultiArgumentos(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public MultiArgumentos parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return argContext.getArgumentos();
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }
}
