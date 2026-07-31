package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import jakarta.annotation.Nonnull;

public class ArgParserContextualPDSection extends ArgParserContextual<PDSection> {

    public ArgParserContextualPDSection(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<PDSection> parse(@Nonnull ContextualParseCall call) {
        PDSection pdSection = (PDSection) PlayerController.getPDSection(call.getSender().getUniqueId(), getArgInfo().getArgumentType()).join();
        //Completes with null only for a player the backend has never heard of - absent, not broken
        return pdSection != null ? ParseResult.of(pdSection) : ParseResult.<PDSection>empty();
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return true;
    }
}
