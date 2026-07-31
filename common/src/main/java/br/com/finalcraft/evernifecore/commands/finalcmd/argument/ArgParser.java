package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.List;

/**
 * Turns one token into a {@code T}. What a failure COSTS is not decided here - a parser that had to
 * ask {@code argInfo.isRequired()} was writing the framework's decision tree over and over - so every
 * method answers with a {@link ParseResult} saying what happened and, at most, what to say about it.
 * <ul>
 *     <li>{@link #parse} converts, or says why it could not;</li>
 *     <li>{@link #absent} is the value of an optional argument nobody typed;</li>
 *     <li>{@link #fromSender} is the value read off the sender when no token was ceded.</li>
 * </ul>
 * Which one runs is decided by {@link ParseEngine}, never here.
 */
public abstract class ArgParser<T extends Object> extends AbstractArgParser<T, ParseCall> implements ITabParser {

    public ArgParser(ArgInfo argInfo) {
        super(argInfo);
    }

    /** Converts the token, or says why it could not. Never null, never throws to say "no". */
    @Override
    public abstract @Nonnull ParseResult<T> parse(@Nonnull ParseCall call);

    /**
     * The value of an optional argument that got no token and declares no {@code def()}. Override when
     * "not typed" has a natural value of its own.
     */
    public @Nonnull ParseResult<T> absent(@Nonnull ParseCall call) {
        return ParseResult.empty();
    }

    /**
     * The value read off the sender's own state, for an {@code @Arg(fromSender = true)} that got no
     * token. Not overriding it is refused at REGISTRATION - the argument would silently eat the next
     * token instead of being inferred.
     */
    public @Nonnull ParseResult<T> fromSender(@Nonnull ParseCall call) {
        throw new UnsupportedOperationException("[" + getClass().getSimpleName() + "] does not resolve from the sender");
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        return Arrays.asList();
    }

}
