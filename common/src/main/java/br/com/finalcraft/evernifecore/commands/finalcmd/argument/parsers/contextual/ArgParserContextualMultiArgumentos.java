package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import jakarta.annotation.Nonnull;

/**
 * Hands the method the executable's own window, as the dispatch left it. The dispatch scans every
 * command, whether or not anything on the path declares an {@code @Arg.Flag}, so this window is ALWAYS
 * already extracted: the flag tokens and the bare {@code --} are gone from it, and
 * {@link MultiArgumentos#flagify()} is a no-op, which means {@code getFlags()} answers exactly what the
 * path declared and never sniffs. There is one reading of this parameter, not one per declaration.
 * <p>
 * That the window comes post-extraction is the honest thing to hand over: it is exactly what the
 * method's own positionals were read from, so a parameter that walks the tokens sees the same line the
 * framework did. Sniffed mode is what an instance nothing has scanned still does - one built by hand,
 * one from {@link MultiArgumentos#sliceFrom(int)}, or a variadic tail, which arrives exactly as typed
 * because the scan stops where the tail begins.
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
