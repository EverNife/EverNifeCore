package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nonnull;

public class ArgParserContextualPlayerData extends ArgParserContextual<PlayerData> {

    public ArgParserContextualPlayerData(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<PlayerData> parse(@Nonnull ContextualParseCall call) {
        PlayerData playerData = PlayerController.getLoaded(call.getSender().getUniqueId());
        //Memory-only lookup: a player whose data is not resident yet is not an error, the parameter
        //simply gets nothing
        return playerData != null ? ParseResult.of(playerData) : ParseResult.<PlayerData>empty();
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }
}
