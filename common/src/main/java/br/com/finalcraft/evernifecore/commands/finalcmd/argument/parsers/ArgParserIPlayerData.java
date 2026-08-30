package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nonnull;

/**
 * The PER-PLAYER half of the {@link ArgParserPlayerLookup} family: the named player itself, or one of
 * its {@link PDSection}s. An {@link br.com.finalcraft.evernifecore.playerdata.AccountSection} is keyed
 * by the account rather than by the player, so it has its own parser -
 * {@link ArgParserAccountSection}.
 */
public class ArgParserIPlayerData extends ArgParserPlayerLookup<IPlayerData> {

    public ArgParserIPlayerData(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    protected @Nonnull ParseResult<IPlayerData> project(@Nonnull PlayerData playerData) {
        if (PlayerData.class.equals(argInfo.getArgumentType())){
            return ParseResult.<IPlayerData>of(playerData);
        }

        return ParseResult.<IPlayerData>of(playerData
                .getPDSection((Class<? extends PDSection>) this.argInfo.getArgumentType())
                .join());
    }
}
