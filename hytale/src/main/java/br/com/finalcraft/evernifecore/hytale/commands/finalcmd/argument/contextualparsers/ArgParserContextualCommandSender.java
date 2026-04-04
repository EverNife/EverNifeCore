package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import jakarta.annotation.Nonnull;

public class ArgParserContextualCommandSender extends ArgParserContextual<CommandSender> {

    public ArgParserContextualCommandSender(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public CommandSender parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return sender.getDelegate(CommandSender.class);
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }

}
