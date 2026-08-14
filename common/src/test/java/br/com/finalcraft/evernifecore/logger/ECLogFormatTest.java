package br.com.finalcraft.evernifecore.logger;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The formatter's whole contract, case by case. Every one of these is an input that used to end a
 * log call with an exception under {@code String.format}, so what is pinned here is not the happy
 * path - it is that no input has an exceptional outcome left.
 */
class ECLogFormatTest {

    private static final String NL = System.lineSeparator();

    /** A parameter whose {@code toString()} is the failure being logged. */
    private static final class HostileToString {
        @Override
        public String toString() {
            throw new IllegalStateException("toString blew up");
        }
    }

    /** Worse: the trailing throwable itself refuses to render, message and stack alike. */
    private static final class HostileThrowable extends RuntimeException {
        @Override
        public String toString() {
            throw new IllegalStateException("toString blew up");
        }

        @Override
        public void printStackTrace(java.io.PrintWriter writer) {
            throw new IllegalStateException("printStackTrace blew up");
        }
    }

    @Test
    void placeholdersAreFilledLeftToRight() {
        assertEquals("a b c", ECLogFormat.format("a {} c", "b"));
        assertEquals("Loaded 12 arenas for world", ECLogFormat.format("Loaded {} arenas for {}", 12, "world"));
    }

    @Test
    void aPlaceholderWithNoArgumentStaysLiteral() {
        assertEquals("a b {}", ECLogFormat.format("a {} {}", "b"));
        assertEquals("a {} b", ECLogFormat.format("a {} b"));
    }

    @Test
    void surplusArgumentsAreAppended() {
        assertEquals("a b [c]", ECLogFormat.format("a {}", "b", "c"));
        assertEquals("a b [c, 3]", ECLogFormat.format("a {}", "b", "c", 3));
        assertEquals("a [b]", ECLogFormat.format("a", "b"));
    }

    @Test
    void aBackslashEscapesThePlaceholderWithoutConsumingTheArgument() {
        assertEquals("a {} b [x]", ECLogFormat.format("a \\{} b", "x"));
    }

    @Test
    void anEscapedBackslashLeavesTheFollowingPlaceholderReal() {
        assertEquals("a \\x b", ECLogFormat.format("a \\\\{} b", "x"));
    }

    @Test
    void theMessageIsReadTheSameWayWithAndWithoutArguments() {
        //both sides of each pair on purpose: what broke here was not one output but the two disagreeing.
        //a fast path for the argument-less call used to skip the scanner, and the escape - which only
        //the scanner consumes - leaked its backslash into the log line whenever nothing followed it
        assertEquals("a {} b", ECLogFormat.format("a \\{} b"));
        assertEquals("a {} b [x]", ECLogFormat.format("a \\{} b", "x"));

        //escaped backslash: one backslash either way, and the placeholder after it is a real one -
        //filled when an argument exists, literal when none is left, exactly like any other placeholder
        assertEquals("a \\{} b", ECLogFormat.format("a \\\\{} b"));
        assertEquals("a \\x b", ECLogFormat.format("a \\\\{} b", "x"));

        assertEquals("a {} b", ECLogFormat.format("a {} b"));
        assertEquals("a x b", ECLogFormat.format("a {} b", "x"));
    }

    @Test
    void aBackslashOutsideAnEscapeSurvivesUntouched() {
        //the argument-less shortcut may only skip the scan when there is nothing for it to do, so a
        //message carrying backslashes goes through the scanner and has to come out unchanged
        assertEquals("C:\\logs\\latest.log", ECLogFormat.format("C:\\logs\\latest.log"));
        assertEquals("ends with a backslash \\", ECLogFormat.format("ends with a backslash \\"));

        //the sharp edge of the escape rule: a path separator that lands right before a placeholder IS
        //an escape, so the placeholder goes literal and its argument spills into the surplus list
        assertEquals("C:\\logs{} [latest.log]", ECLogFormat.format("C:\\logs\\{}", "latest.log"));
        //doubling the separator is how that message keeps its placeholder
        assertEquals("C:\\logs\\latest.log", ECLogFormat.format("C:\\logs\\\\{}", "latest.log"));
    }

    @Test
    void arraysRenderTheirContents() {
        assertEquals("[1, 2]", ECLogFormat.format("{}", new int[]{1, 2}));
        assertEquals("[a, b]", ECLogFormat.format("{}", (Object) new String[]{"a", "b"}));
        assertEquals("[[1, 2], [3]]", ECLogFormat.format("{}", (Object) new int[][]{{1, 2}, {3}}));
        assertEquals("[true, false]", ECLogFormat.format("{}", new boolean[]{true, false}));
    }

    @Test
    void aHostileToStringNamesTheClassInsteadOfThrowing() {
        String expected = "<toString failed: " + HostileToString.class.getName() + ">";
        assertEquals("value: " + expected, ECLogFormat.format("value: {}", new HostileToString()));
        //also when it is a surplus argument, and when it hides inside an array
        assertEquals("value [" + expected + "]", ECLogFormat.format("value", new HostileToString()));
        assertTrue(ECLogFormat.format("{}", (Object) new Object[]{new HostileToString()}).startsWith("<toString failed:"));
    }

    @Test
    void aThrowableConsumedByAPlaceholderCarriesNoStackTrace() {
        IOException cause = new IOException("boom");

        String formatted = ECLogFormat.format("fail: {}", cause);

        assertEquals("fail: java.io.IOException: boom", formatted);
        assertFalse(formatted.contains("\tat "), "a consumed throwable is a value, not the failure of the line");
    }

    @Test
    void aTrailingThrowableCarriesItsStackTrace() {
        IOException cause = new IOException("boom");

        String formatted = ECLogFormat.format("fail", cause);

        assertTrue(formatted.startsWith("fail" + NL), formatted);
        assertTrue(formatted.contains("java.io.IOException: boom"), formatted);
        assertTrue(formatted.contains("\tat " + ECLogFormatTest.class.getName()), formatted);
    }

    @Test
    void aTrailingThrowableIsStillTrailingAfterSurplusArguments() {
        IOException cause = new IOException("boom");

        String formatted = ECLogFormat.format("fail {}", "here", "extra", cause);

        assertTrue(formatted.startsWith("fail here [extra]" + NL), formatted);
        assertTrue(formatted.contains("java.io.IOException: boom"), formatted);
    }

    @Test
    void everyLevelGetsTheTrailingStackTrace() {
        //the rule is the formatter's, not a severe(String, Throwable) overload's
        String formatted = ECLogFormat.format("Could not parse {}", "config.yml", new IllegalArgumentException("bad"));

        assertTrue(formatted.startsWith("Could not parse config.yml" + NL), formatted);
        assertTrue(formatted.contains("java.lang.IllegalArgumentException: bad"), formatted);
    }

    @Test
    void printfMarkersAreOrdinaryText() {
        assertEquals("Progress: 100% done", ECLogFormat.format("Progress: 100% done"));
        assertEquals("50% of 4 done", ECLogFormat.format("{}% of {} done", 50, 4));
    }

    @Test
    void nullsHaveAnOutputInsteadOfAnException() {
        assertEquals("null", ECLogFormat.format(null));
        assertEquals("null [x]", ECLogFormat.format(null, "x"));
        assertEquals("a null c", ECLogFormat.format("a {} c", (Object) null));
        assertEquals("a {} c", ECLogFormat.format("a {} c", (Object[]) null));
    }

    @Test
    void aThrowableThatRefusesToRenderDoesNotPropagate() {
        HostileThrowable hostile = new HostileThrowable();

        String formatted = ECLogFormat.format("flush failed", hostile);

        assertTrue(formatted.startsWith("flush failed" + NL), formatted);
        assertTrue(formatted.contains("<stack trace unavailable: " + HostileThrowable.class.getName() + ">"), formatted);
    }
}
