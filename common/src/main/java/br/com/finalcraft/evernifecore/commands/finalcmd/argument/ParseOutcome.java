package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * One parse, from end to end: who asked, what came out, and who answered. {@link ParseResult} stays a
 * value that knows nothing about its call; this is where the two meet, and it is the engine that
 * pairs them.
 */
public final class ParseOutcome<T> {

    private final @Nonnull IParseCall call;
    private final @Nonnull ParseResult<T> result;
    private final @Nonnull Class<?> parserClass;

    public ParseOutcome(@Nonnull IParseCall call, @Nonnull ParseResult<T> result, @Nonnull Class<?> parserClass) {
        if (call == null || result == null || parserClass == null){
            throw new IllegalArgumentException("A ParseOutcome always names its call, its result and its parser");
        }
        this.call = call;
        this.result = result;
        this.parserClass = parserClass;
    }

    public @Nonnull IParseCall getCall() {
        return call;
    }

    public @Nonnull ParseResult<T> getResult() {
        return result;
    }

    /** The parser that answered - what an internal error has to name for anyone to go fix it. */
    public @Nonnull Class<?> getParserClass() {
        return parserClass;
    }

    /**
     * Whether the invocation stops here. Already normalized by the engine, so the caller never
     * reapplies policy: a miss the argument could absorb is no longer a failure by the time it arrives.
     */
    public boolean isFatal() {
        return result.isFailure();
    }

    /** The value, or null - the shape an argument slot takes, where "no value" is a legal answer. */
    public @Nullable T getValueOrNull() {
        return result.hasValue() ? result.getValue() : null;
    }

    /** The same call and the same parser, answering something else - for a hook that transforms. */
    public <R> ParseOutcome<R> withResult(@Nonnull ParseResult<R> other) {
        return new ParseOutcome<>(call, other, parserClass);
    }

    @Override
    public String toString() {
        return "ParseOutcome[" + parserClass.getSimpleName() + " " + call.describeArgument() + " -> " + result + "]";
    }
}
