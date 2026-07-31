package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What a parser answered, as one value. The three outcomes that used to travel on two channels - a
 * nullable return and an exception - are states here, and the difference between "I did not recognize
 * that" and "I recognized it and refuse it anyway" is the difference the framework needs to decide
 * what a failure costs.
 * <p>
 * It is a pure value: it knows nothing about who typed the token or how the message gets delivered,
 * which is what makes it testable on its own.
 */
public final class ParseResult<T> {

    public enum Kind {
        /** Converted. Carries a NON-null value. */
        VALUE,
        /** No value and no failure - the parameter gets null and the invocation goes on. */
        EMPTY,
        /** The token was not recognized. Fatal ONLY when the argument is required. */
        UNRECOGNIZED,
        /** Recognized and refused anyway - a domain rule. ALWAYS fatal. */
        DENIED,
        /** A failure that is nobody's typing. Always fatal, stack trace in the log. */
        INTERNAL_ERROR,
        /**
         * What the sender has to be shown is the command's own usage line. Two origins, one meaning:
         * on an argument that comes off a token the framework produces it and no parser is ever
         * consulted, because nothing was typed where something had to be; on a contextual parameter
         * the parser produces it, because only the parser knows it has nothing to give. Always fatal,
         * and never reported - the usage line IS the message.
         */
        MISSING
    }

    private static final ParseResult<Object> EMPTY_RESULT = new ParseResult<>(Kind.EMPTY, null, Reason.NONE, null);
    private static final ParseResult<Object> MISSING_RESULT = new ParseResult<>(Kind.MISSING, null, Reason.NONE, null);

    private final @Nonnull Kind kind;
    private final @Nullable T value;
    private final @Nonnull Reason reason;
    private final @Nullable Throwable cause;

    private ParseResult(@Nonnull Kind kind, @Nullable T value, @Nonnull Reason reason, @Nullable Throwable cause) {
        this.kind = kind;
        this.value = value;
        this.reason = reason;
        this.cause = cause;
    }

    /** Converted. The value is what the argument gets, so it can never be null. */
    public static <T> ParseResult<T> of(@Nonnull T value) {
        if (value == null){
            throw new IllegalArgumentException("A VALUE result is never null - say ParseResult.empty() when there is no value");
        }
        return new ParseResult<>(Kind.VALUE, value, Reason.NONE, null);
    }

    /** No value, and nothing went wrong: the argument gets null and the command still runs. */
    @SuppressWarnings("unchecked")
    public static <T> ParseResult<T> empty() {
        return (ParseResult<T>) EMPTY_RESULT;
    }

    /** The token was not recognized, and this is what to say about it if anybody ever asks. */
    public static <T> ParseResult<T> unrecognized(ILocaleMessageBase... reason) {
        return new ParseResult<>(Kind.UNRECOGNIZED, null, Reason.eager(reason), null);
    }

    /**
     * The same, with the text built only if it is going to be read - which on an optional argument it
     * never is. Reach for it whenever composing the reason costs more than a placeholder.
     */
    public static <T> ParseResult<T> unrecognized(@Nonnull Supplier<List<ILocaleMessageBase>> reason) {
        return new ParseResult<>(Kind.UNRECOGNIZED, null, Reason.lazy(reason), null);
    }

    /** Recognized and refused anyway. Fatal even on an optional argument. */
    public static <T> ParseResult<T> denied(ILocaleMessageBase... reason) {
        return new ParseResult<>(Kind.DENIED, null, Reason.eager(reason), null);
    }

    /** @see #unrecognized(Supplier) */
    public static <T> ParseResult<T> denied(@Nonnull Supplier<List<ILocaleMessageBase>> reason) {
        return new ParseResult<>(Kind.DENIED, null, Reason.lazy(reason), null);
    }

    /** Nobody's typing: a bug, a broken dependency, an exception nobody expected. */
    public static <T> ParseResult<T> internalError(@Nonnull Throwable cause) {
        if (cause == null){
            throw new IllegalArgumentException("An INTERNAL_ERROR result carries the throwable behind it");
        }
        return new ParseResult<>(Kind.INTERNAL_ERROR, null, Reason.NONE, cause);
    }

    /** The answer is the command's own usage line. @see Kind#MISSING */
    @SuppressWarnings("unchecked")
    public static <T> ParseResult<T> missing() {
        return (ParseResult<T>) MISSING_RESULT;
    }

    public @Nonnull Kind getKind() {
        return kind;
    }

    public boolean hasValue() {
        return kind == Kind.VALUE;
    }

    /** The converted value. Ask {@link #hasValue()} first - there is no null answer here. */
    public @Nonnull T getValue() {
        if (!hasValue()){
            throw new IllegalStateException("A " + kind + " result carries no value");
        }
        return value;
    }

    public boolean isFailure() {
        return kind == Kind.UNRECOGNIZED
                || kind == Kind.DENIED
                || kind == Kind.INTERNAL_ERROR
                || kind == Kind.MISSING;
    }

    /**
     * What to tell whoever typed it. Empty whenever there is nothing to say, never null; a lazy reason
     * is built here, once.
     */
    public @Nonnull List<ILocaleMessageBase> getReason() {
        return reason.get();
    }

    /** What went wrong, on an {@link Kind#INTERNAL_ERROR} - null on every other kind. */
    public @Nullable Throwable getCause() {
        return cause;
    }

    /**
     * Converts the value, if there is one. Every other kind travels through untouched, retyped: a
     * failure says nothing about the type it failed to produce.
     */
    public <R> ParseResult<R> map(@Nonnull Function<? super T, ? extends R> mapper) {
        return hasValue() ? ParseResult.<R>of(mapper.apply(value)) : this.<R>retype();
    }

    /**
     * The same failure, seen as a result of another type - what a parser that delegates to another one
     * needs. Refused when there is a value, because that value would have to be invented.
     */
    @SuppressWarnings("unchecked")
    public <R> ParseResult<R> retype() {
        if (hasValue()){
            throw new IllegalStateException("A VALUE result cannot be retyped - only a failure travels between types");
        }
        return (ParseResult<R>) this;
    }

    /**
     * Promotes "I did not recognize it" to "I refuse it", keeping the reason - for the caller that
     * knows this particular miss is fatal no matter how the argument was declared. Every other kind
     * passes through unchanged.
     */
    public ParseResult<T> asDenied() {
        return kind == Kind.UNRECOGNIZED ? new ParseResult<>(Kind.DENIED, null, reason, cause) : this;
    }

    @Override
    public String toString() {
        return "ParseResult[" + kind + (value == null ? "" : ", " + value) + "]";
    }

    /**
     * The reason, evaluated at most once and only when somebody reads it. Derived results share the
     * holder, so promoting or retyping a failure never builds the text a second time.
     */
    private static final class Reason {

        static final Reason NONE = new Reason(Collections.<ILocaleMessageBase>emptyList(), null);

        private @Nullable List<ILocaleMessageBase> messages;
        private @Nullable Supplier<List<ILocaleMessageBase>> supplier;

        private Reason(@Nullable List<ILocaleMessageBase> messages, @Nullable Supplier<List<ILocaleMessageBase>> supplier) {
            this.messages = messages;
            this.supplier = supplier;
        }

        static Reason eager(ILocaleMessageBase... messages) {
            return messages == null || messages.length == 0
                    ? NONE
                    : new Reason(Collections.unmodifiableList(Arrays.asList(messages.clone())), null);
        }

        static Reason lazy(@Nonnull Supplier<List<ILocaleMessageBase>> supplier) {
            if (supplier == null){
                throw new IllegalArgumentException("A lazy reason needs a supplier");
            }
            return new Reason(null, supplier);
        }

        @Nonnull List<ILocaleMessageBase> get() {
            if (messages == null){
                List<ILocaleMessageBase> built = supplier.get();
                messages = built == null
                        ? Collections.<ILocaleMessageBase>emptyList()
                        : Collections.unmodifiableList(new ArrayList<>(built));
                supplier = null;
            }
            return messages;
        }
    }
}
