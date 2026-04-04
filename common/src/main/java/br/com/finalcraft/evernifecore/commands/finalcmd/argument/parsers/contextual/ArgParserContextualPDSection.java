package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import jakarta.annotation.Nonnull;

public class ArgParserContextualPDSection extends ArgParserContextual<PDSection> {

    public ArgParserContextualPDSection(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public PDSection parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return PlayerController.getPDSection(sender.getUniqueId(), getArgInfo().getArgumentType());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }

    @Override
    public int getPriority() {
        return -100; //Lower priority on PDSection, so specific sections take priority!
    }
}
