package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import jakarta.annotation.Nonnull;

public class ArgParserContextualMinecraftFPlayer extends ArgParserContextual<MinecraftFPlayer> {

    public ArgParserContextualMinecraftFPlayer(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public MinecraftFPlayer parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return (MinecraftFPlayer) sender;
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }

}
