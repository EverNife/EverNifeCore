package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgRequirementType;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseOutcome;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ResolvedArguments;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestLocaleMessage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What the pair {call, result} answers once the engine has already applied the policy - which is why
 * every question here is a lookup and none of them is a decision.
 */
class ParseOutcomeTest {

    private static final ILocaleMessageBase REASON = new TestLocaleMessage("because");

    private static final List<ParseResult.Kind> FATAL_KINDS = Arrays.asList(
            ParseResult.Kind.UNRECOGNIZED,
            ParseResult.Kind.DENIED,
            ParseResult.Kind.INTERNAL_ERROR,
            ParseResult.Kind.MISSING);

    private static ParseCall callFor(String argName) {
        ArgInfo argInfo = ArgInfo.positional(String.class,
                new ArgData().setName(argName).setContext(""),
                0,
                ArgRequirementType.REQUIRED);
        return new ParseCall(new TestCommandSender("console"),
                new Argumento("token"),
                argInfo,
                null,
                ResolvedArguments.none(),
                false);
    }

    private static ParseResult<String> resultOf(ParseResult.Kind kind) {
        switch (kind) {
            case VALUE: return ParseResult.of("kept");
            case EMPTY: return ParseResult.empty();
            case UNRECOGNIZED: return ParseResult.unrecognized(REASON);
            case DENIED: return ParseResult.denied(REASON);
            case INTERNAL_ERROR: return ParseResult.internalError(new RuntimeException("boom"));
            case MISSING: return ParseResult.missing();
        }
        throw new IllegalArgumentException("Unhandled kind: " + kind);
    }

    private static ParseOutcome<String> outcomeOf(ParseResult.Kind kind) {
        return new ParseOutcome<>(callFor("<value>"), resultOf(kind), ParseOutcomeTest.class);
    }

    @Test
    void isFatalPerKind() {
        for (ParseResult.Kind kind : ParseResult.Kind.values()) {
            assertEquals(FATAL_KINDS.contains(kind), outcomeOf(kind).isFatal(), "isFatal on " + kind);
        }
    }

    @Test
    void valueOrNullIsNullOutsideValue() {
        for (ParseResult.Kind kind : ParseResult.Kind.values()) {
            if (kind == ParseResult.Kind.VALUE){
                assertEquals("kept", outcomeOf(kind).getValueOrNull());
            }else {
                assertNull(outcomeOf(kind).getValueOrNull(), "valueOrNull on " + kind);
            }
        }
    }

    @Test
    void describeArgumentIsTheDeclaredArgName() {
        assertEquals("<player>", callFor("<player>").describeArgument());
        assertEquals("[amount]", callFor("[amount]").describeArgument());

        ParseOutcome<String> outcome = new ParseOutcome<>(callFor("<player>"), ParseResult.of("kept"), ParseOutcomeTest.class);
        assertEquals("<player>", outcome.withResult(ParseResult.<String>empty()).getCall().describeArgument(),
                "the call travels untouched through a replaced result");
    }
}
