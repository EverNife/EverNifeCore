package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.FlagedArgumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@code --name value} flag tokenizer (matrix F, formerly {@code -name:value}): a plain
 * {@link MultiArgumentos} unit-test, no {@link br.com.finalcraft.evernifecore.commands.finalcmd}
 * harness needed.
 */
class MultiArgumentosSystemTest {

    // ------------------------------------------------------------------
    // N1 - "--force" with nothing after it is a presence flag: value "true", isSet, token stripped
    // ------------------------------------------------------------------

    @Test
    void n1_presenceFlagIsTrueAndIsRemovedFromPositionals() {
        MultiArgumentos args = new MultiArgumentos("cmd --force".split(" "));

        FlagedArgumento flag = args.getFlag("force");
        assertTrue(flag.isSet());
        assertEquals("true", flag.getFlagValue());
        assertEquals(List.of("cmd"), args.getStringArgs());
    }

    // ------------------------------------------------------------------
    // N2 - "--page 2" -> "2"; "-p 2" -> "2" too (single dash is still a valid flag marker)
    // ------------------------------------------------------------------

    @Test
    void n2_singleTokenValueIsConsumedRegardlessOfDashCount() {
        MultiArgumentos twoDashes = new MultiArgumentos("cmd --page 2".split(" "));
        assertEquals("2", twoDashes.getFlag("page").getFlagValue());

        MultiArgumentos oneDash = new MultiArgumentos("cmd -p 2".split(" "));
        assertEquals("2", oneDash.getFlag("p").getFlagValue());
    }

    // ------------------------------------------------------------------
    // N3 - Caso A and Caso B, literal from the user's own answer to the study: quoted vs unquoted
    // multi-word value
    // ------------------------------------------------------------------

    @Test
    void n3_casoA_quotedMultiWordValueBecomesASingleFlagValue() {
        MultiArgumentos args = new MultiArgumentos("dbroad Teste My Friend --title 'Title Message'".split(" "));

        assertEquals("Title Message", args.getFlag("title").getFlagValue());
        assertEquals(List.of("dbroad", "Teste", "My", "Friend"), args.getStringArgs());
    }

    @Test
    void n3_casoB_unquotedMultiWordValueOnlyTakesTheNextTokenAndLeavesTheRestPositional() {
        MultiArgumentos args = new MultiArgumentos("dbroad Teste My Friend --title Title Message".split(" "));

        assertEquals("Title", args.getFlag("title").getFlagValue());
        //"Message" was NOT part of the flag value - it remains a positional argument
        assertEquals(List.of("dbroad", "Teste", "My", "Friend", "Message"), args.getStringArgs());
    }

    // ------------------------------------------------------------------
    // N4 - double quotes also group; an unclosed quote swallows the rest of the line
    // ------------------------------------------------------------------

    @Test
    void n4_doubleQuotesAlsoGroupMultiWordValues() {
        MultiArgumentos args = new MultiArgumentos(new String[]{"cmd", "--title", "\"Title", "Message\""});

        assertEquals("Title Message", args.getFlag("title").getFlagValue());
    }

    @Test
    void n4_unclosedQuoteConsumesTheRestOfTheLine() {
        MultiArgumentos args = new MultiArgumentos(new String[]{"cmd", "--msg", "'Hello", "there", "friend"});

        assertEquals("Hello there friend", args.getFlag("msg").getFlagValue());
        assertEquals(List.of("cmd"), args.getStringArgs());
    }

    // ------------------------------------------------------------------
    // N5 - presence flag followed by a value flag, and the reverse order too
    // ------------------------------------------------------------------

    @Test
    void n5_presenceFlagFollowedByValueFlagAndViceVersa() {
        MultiArgumentos forceFirst = new MultiArgumentos("cmd --force --page 2".split(" "));
        assertEquals("true", forceFirst.getFlag("force").getFlagValue());
        assertEquals("2", forceFirst.getFlag("page").getFlagValue());

        MultiArgumentos pageFirst = new MultiArgumentos("cmd --page 2 --force".split(" "));
        assertEquals("2", pageFirst.getFlag("page").getFlagValue());
        assertEquals("true", pageFirst.getFlag("force").getFlagValue());
    }

    // ------------------------------------------------------------------
    // N6 - "-5" and "--5" are NOT flags (negative-number guard)
    // ------------------------------------------------------------------

    @Test
    void n6_negativeNumbersAreNeverTreatedAsFlags() {
        MultiArgumentos oneDash = new MultiArgumentos("give -5".split(" "));
        assertTrue(oneDash.getFlags().isEmpty());
        assertEquals("-5", oneDash.get(1).toString());

        MultiArgumentos twoDashes = new MultiArgumentos("give --5".split(" "));
        assertTrue(twoDashes.getFlags().isEmpty());
        assertEquals("--5", twoDashes.get(1).toString());
    }

    // ------------------------------------------------------------------
    // N7 - "--" alone ends flag scanning: it is removed, and everything after it stays positional
    // literally, even a token that looks like a flag
    // ------------------------------------------------------------------

    @Test
    void n7_endOfFlagsMarkerStopsScanningAndIsItselfRemoved() {
        MultiArgumentos args = new MultiArgumentos("cmd --real -- --literal".split(" "));

        //"--" interrupted "--real"'s value consumption, so "real" is a presence flag here
        assertEquals("true", args.getFlag("real").getFlagValue());
        assertEquals(List.of("cmd", "--literal"), args.getStringArgs());
    }

    // ------------------------------------------------------------------
    // N8 - getFlag("x") == getFlag("-x") == getFlag("--x"), case-insensitive, same flag instance
    // ------------------------------------------------------------------

    @Test
    void n8_flagLookupIsDashCountAgnosticAndCaseInsensitive() {
        MultiArgumentos args = new MultiArgumentos("cmd --Force".split(" "));

        FlagedArgumento byBareName = args.getFlag("force");
        FlagedArgumento byOneDash = args.getFlag("-FORCE");
        FlagedArgumento byTwoDashes = args.getFlag("--force");

        assertSame(byBareName, byOneDash);
        assertSame(byOneDash, byTwoDashes);
        assertTrue(byBareName.isSet());
    }

    // ------------------------------------------------------------------
    // N9 - a missing flag is FlagedArgumento.EMPTY_ARG: isSet() == false, raw value "false"
    // ------------------------------------------------------------------

    @Test
    void n9_missingFlagIsTheEmptyArgWithFalseAsItsRawValueAndNotSet() {
        MultiArgumentos args = new MultiArgumentos("cmd".split(" "));

        FlagedArgumento flag = args.getFlag("missing");

        assertSame(FlagedArgumento.EMPTY_ARG, flag);
        assertFalse(flag.isSet());
        assertTrue(flag.equals("false"));
    }

    // ------------------------------------------------------------------
    // N10 - ":" is no longer special: "-name:value" is a flag literally called "name:value" with
    // value "true" (the legacy splitter/truncation bugs are gone by construction)
    // ------------------------------------------------------------------

    @Test
    void n10_colonIsNoLongerSpecialSyntax() {
        MultiArgumentos args = new MultiArgumentos(new String[]{"-name:value"});

        FlagedArgumento flag = args.getFlag("name:value");
        assertTrue(flag.isSet());
        assertEquals("true", flag.getFlagValue());
        //It was NOT parsed as name="name" value="value"
        assertFalse(args.getFlag("name").isSet());

        //The old "-time:10:30 truncates to 10" bug is gone: the whole thing is the flag's name
        MultiArgumentos multiColon = new MultiArgumentos(new String[]{"-time:10:30"});
        assertEquals("true", multiColon.getFlag("time:10:30").getFlagValue());
    }

    // ------------------------------------------------------------------
    // N11 - stripping flag tokens closes positional indices up
    // ------------------------------------------------------------------

    @Test
    void n11_stripClosesPositionalIndices() {
        MultiArgumentos args = new MultiArgumentos("hello --page 2 world".split(" "));

        args.getFlags(); //force flagify()

        assertEquals(List.of("hello", "world"), args.getStringArgs());
        assertEquals("hello", args.get(0).toString());
        assertEquals("world", args.get(1).toString());
    }

    // ------------------------------------------------------------------
    // N12 - flagify() is lazy: string accessors called BEFORE getFlags()/getFlag() still see the
    // raw flag tokens (inherited behavior, pinned on purpose)
    // ------------------------------------------------------------------

    @Test
    void n12_flagifyIsLazySoStringAccessorsSeeRawTokensBeforeGetFlagsIsCalled() {
        MultiArgumentos args = new MultiArgumentos("cmd --page 2".split(" "));

        assertEquals(3, args.getStringArgs().size());
        assertEquals("--page", args.getStringArg(1));

        args.getFlags(); //now it flagifies

        assertEquals(1, args.getStringArgs().size());
    }

    // ------------------------------------------------------------------
    // A quote glued directly onto the flag name (no separating space) is part of the marker token's
    // name, NOT the start of a quoted value: "--title'X'" is a flag literally called "title'X'".
    // ------------------------------------------------------------------

    @Test
    void d1_quoteGluedDirectlyOntoTheFlagNameIsPartOfTheNameNotAQuotedValue() {
        MultiArgumentos args = new MultiArgumentos(new String[]{"--title'X'"});

        FlagedArgumento flag = args.getFlag("title'x'");
        assertTrue(flag.isSet());
        assertEquals("true", flag.getFlagValue());
    }

    // ------------------------------------------------------------------
    // Carried over unchanged from the old matrix F (unrelated to the flag syntax rewrite):
    // emptyArgs(...), getStringArg/get out-of-range accessors
    // ------------------------------------------------------------------

    @Test
    void emptyArgsAndOutOfRangeAccessors() {
        MultiArgumentos args = new MultiArgumentos(new String[]{"only"});

        assertFalse(args.emptyArgs(0));
        assertTrue(args.emptyArgs(1)); //index 1 doesn't exist

        assertEquals("", args.getStringArg(5));
        assertEquals(Argumento.EMPTY_ARG, args.get(5));
    }
}
