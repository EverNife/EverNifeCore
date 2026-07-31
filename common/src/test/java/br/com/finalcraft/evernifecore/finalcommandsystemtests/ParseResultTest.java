package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import br.com.finalcraft.evernifecore.testing.TestLocaleMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The value contract of {@link ParseResult}, one rule per method. Nothing here needs a sender, a
 * command or a plugin - which is the whole point of the outcome being a value.
 */
class ParseResultTest {

    private static final ILocaleMessageBase FIRST = new TestLocaleMessage("first");
    private static final ILocaleMessageBase SECOND = new TestLocaleMessage("second");

    @Test
    void ofRefusesNull() {
        assertThrows(IllegalArgumentException.class, () -> ParseResult.of(null));
    }

    @Test
    void valueOnAKindWithoutOneThrows() {
        assertEquals("kept", ParseResult.of("kept").getValue());

        assertThrows(IllegalStateException.class, () -> ParseResult.empty().getValue());
        assertThrows(IllegalStateException.class, () -> ParseResult.missing().getValue());
        assertThrows(IllegalStateException.class, () -> ParseResult.unrecognized(FIRST).getValue());
        assertThrows(IllegalStateException.class, () -> ParseResult.denied(FIRST).getValue());
        assertThrows(IllegalStateException.class, () -> ParseResult.internalError(new RuntimeException()).getValue());
    }

    @Test
    void retypeOnAResultThatHasAValueThrows() {
        assertThrows(IllegalStateException.class, () -> ParseResult.of("kept").retype());
    }

    @Test
    void retypePreservesKindReasonAndCause() {
        RuntimeException boom = new RuntimeException("boom");

        ParseResult<Integer> retyped = ParseResult.<String>internalError(boom).retype();
        assertEquals(ParseResult.Kind.INTERNAL_ERROR, retyped.getKind());
        assertSame(boom, retyped.getCause());

        ParseResult<Integer> refused = ParseResult.<String>denied(FIRST, SECOND).retype();
        assertEquals(ParseResult.Kind.DENIED, refused.getKind());
        assertEquals(Arrays.asList(FIRST, SECOND), refused.getReason());

        assertEquals(ParseResult.Kind.EMPTY, ParseResult.<String>empty().retype().getKind());
    }

    @Test
    void mapRunsOnlyOnValue() {
        AtomicInteger ran = new AtomicInteger();

        assertEquals(5, ParseResult.of("hello").map(text -> {
            ran.incrementAndGet();
            return text.length();
        }).getValue().intValue());
        assertEquals(1, ran.get());

        List<ParseResult<String>> withoutValue = Arrays.asList(
                ParseResult.<String>empty(),
                ParseResult.<String>missing(),
                ParseResult.<String>unrecognized(FIRST),
                ParseResult.<String>denied(FIRST),
                ParseResult.<String>internalError(new RuntimeException()));

        for (ParseResult<String> result : withoutValue) {
            result.map(text -> {
                ran.incrementAndGet();
                return text.length();
            });
        }

        assertEquals(1, ran.get(), "the mapper only ever sees a value that exists");
    }

    @Test
    void mapRetypesAFailureWithoutTouchingTheReason() {
        ParseResult<Integer> mapped = ParseResult.<String>unrecognized(FIRST, SECOND).map(String::length);

        assertEquals(ParseResult.Kind.UNRECOGNIZED, mapped.getKind());
        assertEquals(Arrays.asList(FIRST, SECOND), mapped.getReason());
        assertFalse(mapped.hasValue());
    }

    @Test
    void asDeniedPromotesOnlyUnrecognized() {
        assertEquals(ParseResult.Kind.DENIED, ParseResult.unrecognized(FIRST).asDenied().getKind());

        assertEquals(ParseResult.Kind.VALUE, ParseResult.of("kept").asDenied().getKind());
        assertEquals(ParseResult.Kind.EMPTY, ParseResult.empty().asDenied().getKind());
        assertEquals(ParseResult.Kind.MISSING, ParseResult.missing().asDenied().getKind());
        assertEquals(ParseResult.Kind.DENIED, ParseResult.denied(FIRST).asDenied().getKind());
        assertEquals(ParseResult.Kind.INTERNAL_ERROR, ParseResult.internalError(new RuntimeException()).asDenied().getKind());
    }

    @Test
    void asDeniedKeepsTheReasonOfThePromotedResult() {
        ParseResult<String> promoted = ParseResult.<String>unrecognized(FIRST, SECOND).asDenied();

        assertEquals(Arrays.asList(FIRST, SECOND), promoted.getReason());
    }

    @Test
    void aLazyReasonIsNotBuiltUntilSomebodyReadsIt() {
        AtomicInteger built = new AtomicInteger();

        ParseResult<String> result = ParseResult.unrecognized(() -> {
            built.incrementAndGet();
            return Collections.singletonList(FIRST);
        });

        assertEquals(ParseResult.Kind.UNRECOGNIZED, result.getKind());
        assertTrue(result.isFailure());
        assertEquals(0, built.get(), "an optional argument discards the miss without ever asking why");

        assertEquals(Collections.singletonList(FIRST), result.getReason());
        assertEquals(1, built.get());
    }

    @Test
    void aLazyReasonIsBuiltOnlyOnce() {
        AtomicInteger built = new AtomicInteger();

        ParseResult<String> result = ParseResult.denied(() -> {
            built.incrementAndGet();
            return Collections.singletonList(FIRST);
        });

        result.getReason();
        result.getReason();
        result.<Integer>retype().getReason();
        result.asDenied().getReason();

        assertEquals(1, built.get(), "retyping or promoting a failure reuses the text it already built");
    }

    @Test
    void reasonOfAValueIsEmptyNeverNull() {
        assertTrue(ParseResult.of("kept").getReason().isEmpty());
        assertTrue(ParseResult.empty().getReason().isEmpty());
        assertTrue(ParseResult.missing().getReason().isEmpty());
        assertTrue(ParseResult.internalError(new RuntimeException()).getReason().isEmpty());

        assertNull(ParseResult.of("kept").getCause());
    }

    @Test
    void anArgParseExceptionRefusesAResultThatHasAValue() {
        assertThrows(IllegalArgumentException.class, () -> new ArgParseException(ParseResult.of("kept")));

        assertEquals(ParseResult.Kind.DENIED, new ArgParseException(ParseResult.denied(FIRST)).toResult().getKind());
        assertEquals(Collections.singletonList(FIRST), new ArgParseException(ParseResult.denied(FIRST)).toResult().getReason());
    }

    @Test
    void anArgParseExceptionCarriesNoStackTrace() {
        ArgParseException aborted = new ArgParseException(ParseResult.unrecognized(FIRST));

        assertEquals(0, aborted.getStackTrace().length, "control flow, not a bug report");
        assertNull(aborted.getMessage());
    }

    @Test
    void aReasonIsNotChangedByWhoeverHandedItIn() {
        ILocaleMessageBase[] handedIn = {FIRST, SECOND};
        ParseResult<String> eager = ParseResult.unrecognized(handedIn);

        handedIn[1] = FIRST;
        assertEquals(Arrays.asList(FIRST, SECOND), eager.getReason(), "the array the caller kept is not the reason");

        List<ILocaleMessageBase> supplied = new ArrayList<>(Arrays.asList(FIRST));
        ParseResult<String> lazy = ParseResult.denied(() -> supplied);
        lazy.getReason();

        supplied.add(SECOND);
        assertEquals(Collections.singletonList(FIRST), lazy.getReason(), "nor is the list the supplier returned");
    }
}
