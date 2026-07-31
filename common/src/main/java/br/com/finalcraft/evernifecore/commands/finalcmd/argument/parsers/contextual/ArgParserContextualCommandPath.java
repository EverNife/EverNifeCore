package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.contextual;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import jakarta.annotation.Nonnull;

/**
 * Hands the method the concrete path it was reached by - what a command that renders its own line
 * (or opens a message scope for an asynchronous answer) needs and cannot rebuild from the label.
 */
public class ArgParserContextualCommandPath extends ArgParserContextual<CommandPath> {

    public ArgParserContextualCommandPath(ArgInfo argInfo) {
        super(argInfo);
    }

    @Override
    public ParseResult<CommandPath> parse(@Nonnull ContextualParseCall call) {
        return ParseResult.of(call.getPath());
    }

    @Override
    public boolean requiresToBeAPlayer() {
        return false;
    }
}
