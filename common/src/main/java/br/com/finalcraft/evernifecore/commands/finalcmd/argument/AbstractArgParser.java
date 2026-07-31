package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.function.Supplier;

/**
 * What every parser is, whichever family it belongs to: something that answers one {@link ArgInfo}
 * with a {@link ParseResult}. What a failure COSTS is not decided here - {@link ParseEngine} reads the
 * result and says what it means - so a parser only ever states what happened.
 * <p>
 * The call type is a parameter, which is what keeps the families from ever meeting halfway: an
 * {@link ArgParser} is asked about a token and an {@link ArgParserContextual} is asked about the
 * invocation, and no accident of inheritance can hand one the other's question.
 * <p>
 * It is a class and not an interface because the four shortcuts below are {@code protected}: fabricating
 * a refusal is a parser's own business, not something any caller may do in its name.
 *
 * @param <C> the shape of the question this family answers
 */
public abstract class AbstractArgParser<T extends Object, C extends IParseCall> {

    protected final ArgInfo argInfo;

    protected AbstractArgParser(ArgInfo argInfo) {
        this.argInfo = argInfo;
    }

    public ArgInfo getArgInfo() {
        return argInfo;
    }

    /** Produces the value, or says why it could not. Never null, never throws to say "no". */
    public abstract @Nonnull ParseResult<T> parse(@Nonnull C call);

    /**
     * Nothing here could be made sense of. Fatal only where the argument was required, so on an optional
     * one these messages are never even read - which is what the lazy overload is for.
     */
    protected final ParseResult<T> unrecognized(ILocaleMessageBase... reason) {
        return ParseResult.unrecognized(reason);
    }

    /** @see #unrecognized(ILocaleMessageBase...) */
    protected final ParseResult<T> unrecognized(Supplier<List<ILocaleMessageBase>> reason) {
        return ParseResult.unrecognized(reason);
    }

    /**
     * Refuses a value that came through fine and does not serve - a domain rule, not a parse failure,
     * and always fatal even on an optional argument:
     * <pre>
     * return denied(THERE_IS_NO_REGION_AT_YOUR_LOCATION);
     * return denied(FCMessageUtil.PLAYER_NOT_ONLINE.addPlaceholder("searched_name", name));
     * </pre>
     * Both forms are the same call because {@code addPlaceholder} hands back a NEW message rather than
     * mutating the shared static field - two senders at once never read each other's text.
     */
    protected final ParseResult<T> denied(ILocaleMessageBase... reason) {
        return ParseResult.denied(reason);
    }

    /** @see #denied(ILocaleMessageBase...) */
    protected final ParseResult<T> denied(Supplier<List<ILocaleMessageBase>> reason) {
        return ParseResult.denied(reason);
    }

}
