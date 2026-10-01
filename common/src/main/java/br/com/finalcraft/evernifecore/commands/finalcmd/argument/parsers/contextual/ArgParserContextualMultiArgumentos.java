package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import jakarta.annotation.Nonnull;

/**
 * Hands the method the executable's own window, as the dispatch left it: the declared flags of the path
 * and the bare {@code --} already taken out, or every token as typed when the path declares no flag. It
 * is exactly what the method's own positionals were read from, so a parameter that walks the tokens sees
 * the same line the framework did, and {@code getFlag} answers only what the path declared.
 */
public class ArgParserContextualMultiArgumentos extends ArgParserContextual<MultiArgumentos> {

    public ArgParserContextualMultiArgumentos(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<MultiArgumentos> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of(call.getArgumentos());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }
}
