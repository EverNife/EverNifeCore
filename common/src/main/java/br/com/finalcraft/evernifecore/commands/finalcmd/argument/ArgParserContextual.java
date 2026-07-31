package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import jakarta.annotation.Nonnull;

/**
 * Resolves a parameter from the surroundings of the invocation rather than from a typed token - the
 * sender itself, the help line, the path it was reached by.
 * <p>
 * It answers with a {@link ParseResult} for the same reason an {@link ArgParser} does: what a failure
 * costs, and who gets told about it, is {@link ParseEngine}'s decision and never the parser's. Nothing
 * routes above a contextual parameter, though, so unlike a positional one every refusal it answers is
 * fatal however the parameter was declared.
 */
public abstract class ArgParserContextual<T extends Object> extends AbstractArgParser<T, ContextualParseCall> {

    public ArgParserContextual(ArgInfo argInfo) {
        super(argInfo);
    }

    /** Resolves the parameter, or says why it could not. Never null, never throws to say "no". */
    @Override
    public abstract @Nonnull ParseResult<T> parse(@Nonnull ContextualParseCall call);

    public abstract boolean requiresToBeAPlayer();

    /**
     * When this parser runs, for every parameter that does not override it. Most contextual parameters
     * carry no annotation at all ({@code FCommandSender sender}), so this is the only place a parser
     * that is expensive, or that depends on a typed token, can say so.
     * <p>
     * The default is the early one: it is what lets the parser of a token read what was resolved off
     * the invocation. Answer {@link ResolutionPhase#AFTER_ARGUMENTS} to trade that away for seeing the
     * tokens. Answering {@link ResolutionPhase#PARSER_DEFAULT} is refused at registration - the choice
     * has to end somewhere.
     */
    public ResolutionPhase defaultPhase() {
        return ResolutionPhase.BEFORE_ARGUMENTS;
    }

    /**
     * Nothing to give, and the sender is the one who can fix it: the invocation stops and the command's
     * own usage line is what gets sent, the same answer a required token nobody typed gets. Reach for it
     * instead of {@link #denied} whenever the honest message is the shape of the command itself.
     */
    protected final ParseResult<T> missing() {
        return ParseResult.missing();
    }

}
