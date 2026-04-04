package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgContextualInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserCommandContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import jakarta.annotation.Nonnull;

public class ArgParserContextualPlayerData extends ArgParserContextual<PlayerData> {

    public ArgParserContextualPlayerData(ArgContextualInfo argContextualInfo) {
        super(argContextualInfo);
    }

    @Override
    public PlayerData parserArgument(@Nonnull ArgParserCommandContext argContext, @Nonnull FCommandSender sender) throws ArgParseException {
        return PlayerController.getPlayerData(sender.getUniqueId());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
