package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nonnull;

import java.util.function.Supplier;

/**
 * The policy of a parse, written once: which method of the parser answers, what a failure costs, and
 * who gets told about it. A parser says what happened; this says what it means.
 * <p>
 * Every caller of an {@link AbstractArgParser} goes through here, which is what lets the same parser
 * serve a command line and a config file: subclass, override the reporting, and the routing and the
 * safety net stay exactly as they are.
 */
public class ParseEngine {

    /** Reports to whoever typed the token - the answer a command line wants. */
    public static final ParseEngine DEFAULT = new ParseEngine();

    /**
     * The whole routing table, the safety net and the reporting, in one call, for either family of
     * parser. Never throws on account of the parse: an exception from a parser comes back as an
     * {@link ParseResult.Kind#INTERNAL_ERROR} outcome, and even a reason that fails to be delivered
     * costs a log line instead of the dispatch - so a broken parser stops one argument.
     */
    public final <T, C extends IParseCall> ParseOutcome<T> run(@Nonnull AbstractArgParser<T, C> parser, @Nonnull C call) {
        return run(parser, call, routeOf(parser, call));
    }

    /**
     * Runs the argument's declared {@code def()} instead of reading the call's token - for the cases
     * where "nobody typed this" is known by the caller and cannot be seen in the token itself: an
     * absent flag looks exactly like a flag typed with an empty value.
     * <p>
     * Taking the same road a {@code def()} always takes is the whole point: the text is the
     * developer's, so a default the parser cannot read comes back as the command's bug and never as a
     * message blaming whoever typed a word they did not type.
     */
    public final <T> ParseOutcome<T> runDefault(@Nonnull ArgParser<T> parser, @Nonnull ParseCall call) {
        return run(parser, call, Route.DEF);
    }

    private <T, C extends IParseCall> ParseOutcome<T> run(AbstractArgParser<T, C> parser, C call, Route route) {
        beforeParse(call);

        ParseResult<T> result = normalize(guarded(() -> answerOf(parser, call, route), parser), parser, call, route);

        ParseOutcome<T> outcome = afterParse(new ParseOutcome<T>(call, result, parser.getClass()));

        if (outcome.isFatal() && outcome.getResult().getKind() != ParseResult.Kind.MISSING){
            reportGuarded(outcome);
        }

        return outcome;
    }

    /** Runs before the routing table is read - which sometimes consults no parser at all. */
    protected void beforeParse(@Nonnull IParseCall call) {
    }

    /** Last chance to transform or veto. Runs AFTER normalization, BEFORE reporting. */
    protected <T> ParseOutcome<T> afterParse(@Nonnull ParseOutcome<T> outcome) {
        return outcome;
    }

    /**
     * Delivers the reason. Overriding this replaces the whole delivery; overriding one of the three
     * below replaces a single case and leaves the others alone.
     */
    protected void report(@Nonnull ParseOutcome<?> outcome) {
        switch (outcome.getResult().getKind()) {
            case UNRECOGNIZED:
                onUnrecognized(outcome);
                break;
            case DENIED:
                onDenied(outcome);
                break;
            case INTERNAL_ERROR:
                onInternalError(outcome);
                break;
            default:
                break;
        }
    }

    /** A token nobody could make sense of, on an argument that could not do without it. */
    protected void onUnrecognized(@Nonnull ParseOutcome<?> outcome) {
        sendReason(outcome);
    }

    /** A token that converted and was refused anyway - a rule of the command, not a typo. */
    protected void onDenied(@Nonnull ParseOutcome<?> outcome) {
        sendReason(outcome);
    }

    /**
     * A failure that is nobody's typing. The stack goes to the log, where somebody can act on it, and
     * whoever typed the command gets told the argument did not work - never the exception.
     */
    protected void onInternalError(@Nonnull ParseOutcome<?> outcome) {
        EverNifeCore.getLog().severe("[{}] failed while parsing {} for {}"
                        + " - this is a bug in the parser, not in what was typed",
                outcome.getParserClass().getName(), outcome.getCall().describeArgument(),
                outcome.getCall().getSender().getName(), outcome.getResult().getCause());

        FCMessageUtil.INVALID_ARGUMENT
                .addPlaceholder("argument", outcome.getCall().describeArgument())
                .send(outcome.getCall().getSender());
    }

    /** Sends every message the result carries, in the order the parser listed them. */
    protected final void sendReason(@Nonnull ParseOutcome<?> outcome) {
        for (ILocaleMessageBase message : outcome.getResult().getReason()) {
            message.send(outcome.getCall().getSender());
        }
    }

    /**
     * Delivering the reason is the last thing that happens, and it is not allowed to be the thing that
     * fails: a reason that carries a null message, or a subclass whose delivery throws, would otherwise
     * blow up frames above a dispatch that has nothing to do with it. Everything the report is about
     * has already happened, so a broken report costs a log line - never the command.
     * <p>
     * It guards the CALL rather than living inside {@code report}, so a subclass that replaces the whole
     * delivery is covered without having to know this exists.
     */
    private void reportGuarded(@Nonnull ParseOutcome<?> outcome) {
        try {
            report(outcome);
        }catch (RuntimeException broken){
            EverNifeCore.getLog().severe("[{}] failed while reporting a {} on {}"
                            + " - the parse itself stands, only what to say about it was lost",
                    getClass().getName(), outcome.getResult().getKind(),
                    outcome.getCall().describeArgument(), broken);
        }
    }

    /** Which of the parser's methods the state of the argument sends the call to. */
    private enum Route {
        DIRECT, TOKEN, FROM_SENDER, MISSING, DEF, ABSENT, MISMATCHED
    }

    /**
     * A contextual parameter is about no token at all, so there is nothing above its single method to
     * route to. On every other source a required argument with no token consults nobody: there is
     * nothing for a parser to say about a word that was never typed.
     * <p>
     * Whether the argument is contextual has to be the same answer three times over - the source says
     * so, the parser is of that family, and the call carries what that family reads. Two answers that
     * disagree used to be a {@link ClassCastException} thrown from inside the routing, which named
     * neither the argument nor the parser; the invariant is checked here so the mismatch arrives as
     * what it is.
     */
    private static Route routeOf(AbstractArgParser<?, ?> parser, IParseCall call) {
        ArgInfo argInfo = parser.getArgInfo();
        boolean contextualSource = argInfo.getSource() == ArgSource.CONTEXTUAL;

        if (contextualSource != (parser instanceof ArgParserContextual) || contextualSource != (call instanceof ContextualParseCall)){
            return Route.MISMATCHED;
        }
        if (contextualSource){
            return Route.DIRECT;
        }
        if (!tokenCall(call).getArgumento().isEmpty()){
            return Route.TOKEN;
        }
        if (argInfo.isProvidedByContext()){
            //No token was ceded to it, which is precisely what fromSender is for - being inside this
            //call IS the statement that nothing was consumed
            return Route.FROM_SENDER;
        }
        if (argInfo.isRequired()){
            return Route.MISSING;
        }
        return argInfo.getArgData().getDef().isEmpty() ? Route.ABSENT : Route.DEF;
    }

    private <T, C extends IParseCall> ParseResult<T> answerOf(AbstractArgParser<T, C> parser, C call, Route route) {
        if (route == Route.MISMATCHED){
            return ParseResult.internalError(new ArgMountException(
                    "[" + parser.getClass().getName() + "] was asked to resolve " + call.describeArgument()
                            + ", declared as " + parser.getArgInfo().getSource() + ". A contextual parser reads the invocation and a "
                            + "token parser reads a word, so the source, the parser and the call have to name the same one - "
                            + (parser instanceof ArgParserContextual
                            ? "declare the argument with @Arg.Contextual (or no annotation at all)."
                            : "declare the argument with @Arg, or point it at a token parser.")));
        }
        if (route == Route.DIRECT){
            return parser.parse(call);
        }
        if (route == Route.MISSING){
            return ParseResult.missing();
        }

        ArgParser<T> tokenParser = tokenParser(parser);
        ParseCall tokenCall = tokenCall(call);
        switch (route) {
            case TOKEN:
                return tokenParser.parse(tokenCall);
            case FROM_SENDER:
                return tokenParser.fromSender(tokenCall);
            case DEF:
                //The def() text goes through the same parser, as if it had been typed
                return tokenParser.parse(tokenCall.withArgumento(new Argumento(parser.getArgInfo().getArgData().getDef())));
            default:
                return tokenParser.absent(tokenCall);
        }
    }

    /**
     * The invariant behind the two narrowings below, and the reason they are the routing table's own
     * conclusion instead of a guess: only an argument that is NOT contextual ever leaves
     * {@link Route#DIRECT}, and every one of those is declared with an {@link ArgParser}, which is the
     * only kind of parser a {@link ParseCall} is ever built for.
     */
    private static ParseCall tokenCall(IParseCall call) {
        return (ParseCall) call;
    }

    /** @see #tokenCall(IParseCall) */
    @SuppressWarnings("unchecked")
    private static <T> ArgParser<T> tokenParser(AbstractArgParser<T, ?> parser) {
        return (ArgParser<T>) parser;
    }

    /**
     * The safety net, written once for both hierarchies: whatever the parser does - answer, abort or
     * blow up - a result comes back, and a broken parser stops one argument instead of the dispatch.
     */
    private static <T> ParseResult<T> guarded(Supplier<ParseResult<T>> answer, Object parser) {
        try {
            ParseResult<T> result = answer.get();
            if (result == null){
                return ParseResult.internalError(new NullPointerException(
                        "[" + parser.getClass().getName() + "] answered null instead of a ParseResult"));
            }
            return result;
        }catch (ArgParseException aborted){
            //An abort from three frames down carries its own outcome, and gets the same treatment a
            //returned one would - the shortcut buys no different policy
            return aborted.<T>toResult();
        }catch (RuntimeException unexpected){
            return ParseResult.internalError(unexpected);
        }
    }

    /**
     * An unrecognized token an optional argument can do without is not a failure at all - the argument
     * simply gets nothing, and nobody is told about a word the command never needed. A {@code def()}
     * is the exception: that text is the developer's, not the sender's, so a default the parser cannot
     * use is a bug in the command instead of an absent value nobody notices.
     * <p>
     * A contextual parameter is required by construction, so nothing it answers is ever softened here.
     */
    private <T> ParseResult<T> normalize(ParseResult<T> result, AbstractArgParser<T, ?> parser, IParseCall call, Route route) {
        if (route == Route.DEF && failedOnItsOwnDefault(result)){
            return ParseResult.internalError(new IllegalStateException(
                    "[" + parser.getClass().getName() + "] cannot use the default '"
                            + parser.getArgInfo().getArgData().getDef() + "' declared by def() on "
                            + call.describeArgument() + " - the parser answered " + result.getKind()
                            + " for the command's own text, so nobody who types this command can fix it"));
        }

        if (result.getKind() != ParseResult.Kind.UNRECOGNIZED){
            return result;
        }

        return parser.getArgInfo().isRequired() ? result : ParseResult.<T>empty();
    }

    /**
     * Whether a {@code def()} was refused for something the sender could never influence. An
     * {@code INTERNAL_ERROR} is already addressed to the developer and keeps the cause it came with;
     * every other refusal would blame whoever typed the command for a word they never typed.
     */
    private static boolean failedOnItsOwnDefault(ParseResult<?> result) {
        return result.getKind() == ParseResult.Kind.UNRECOGNIZED || result.getKind() == ParseResult.Kind.DENIED;
    }
}
