package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.playerdata.AccountSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import jakarta.annotation.Nonnull;

/**
 * The sender's own ACCOUNT-wide row - the account counterpart of {@link ArgParserContextualPDSection}.
 * Keyed by the sender's accountId, so every identity linked into the account is injected the same
 * instance.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ArgParserContextualAccountSection extends ArgParserContextual<AccountSection> {

    public ArgParserContextualAccountSection(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<AccountSection> parse(@Nonnull ContextualParseCall call) {
        Class sectionClass = getArgInfo().getArgumentType();
        AccountSection section = (AccountSection) PlayerController
                .getAccountSection(call.getSender().getUniqueId(), sectionClass).join();
        //Completes with null only for a player the backend has never heard of - absent, not broken
        return section != null ? ParseResult.of(section) : ParseResult.<AccountSection>empty();
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }
}
