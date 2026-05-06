package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFCommandSender;
import jakarta.annotation.Nonnull;

public class ArgParserContextualMinecraftFCommandSender extends ArgParserContextual<MinecraftFCommandSender> {

    public ArgParserContextualMinecraftFCommandSender(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public MinecraftFCommandSender parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return (MinecraftFCommandSender) sender;
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }

}
