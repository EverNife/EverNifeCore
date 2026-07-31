package br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import jakarta.annotation.Nonnull;

/**
 * Aborts a parse from any depth, carrying the outcome the engine adopts - the legal shortcut for a
 * helper three frames below the parser, which cannot change the return type of the whole chain.
 * <p>
 * Control flow, not a bug: it has no message and no stack trace of its own, because everything a
 * sender is ever told lives in the result it carries.
 */
public final class ArgParseException extends RuntimeException {

    private final ParseResult<?> result;

    public ArgParseException(@Nonnull ParseResult<?> result) {
        super(null, null, false, false); //no suppression, no stack trace: this is not a bug report
        if (result == null || result.hasValue()){
            throw new IllegalArgumentException("An ArgParseException carries a failure, never a value");
        }
        this.result = result;
    }

    public <T> ParseResult<T> toResult() {
        return result.retype();
    }

}
