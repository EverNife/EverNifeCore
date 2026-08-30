package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.playerdata.AccountSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import jakarta.annotation.Nonnull;

/**
 * The ACCOUNT-wide half of the {@link ArgParserPlayerLookup} family: the token still names a player,
 * but the row handed over belongs to that player's account, so two linked identities resolve to the
 * SAME live instance. The value carries no player identity of its own (see {@link AccountSection}) -
 * a command that also needs the name or the online state declares the player as a second parameter.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ArgParserAccountSection extends ArgParserPlayerLookup<AccountSection> {

    public ArgParserAccountSection(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    protected @Nonnull ParseResult<AccountSection> project(@Nonnull PlayerData playerData) {
        Class sectionClass = this.argInfo.getArgumentType();
        return ParseResult.<AccountSection>of((AccountSection) playerData.getAccountSection(sectionClass).join());
    }
}
