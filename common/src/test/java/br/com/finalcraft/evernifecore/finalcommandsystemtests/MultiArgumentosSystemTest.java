package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.FlagedArgumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the manual {@code -flag:value} facility (matrix F) as it behaves TODAY: no
 * {@link br.com.finalcraft.evernifecore.commands.finalcmd} harness needed, this is a plain
 * {@link MultiArgumentos} unit-test - the existing test at
 * {@code minecraft/src/test/.../argumento/MultiArgumentosTest.java} stays where it is until F4
 * moves this facility to the new {@code --name value} syntax. Two of the rows below (F6, F7) pin
 * KNOWN bugs on purpose - F4 will fix them and update these tests then, not before.
 */
class MultiArgumentosSystemTest {

    // ------------------------------------------------------------------
    // F1 - "-name:value" becomes a flag; the token is removed from the positionals after
    // getFlags(), and positional indices close up
    // ------------------------------------------------------------------

    @Test
    void f1_nameColonValueBecomesAFlagAndPositionalsCloseUp() {
        MultiArgumentos args = new MultiArgumentos("hello -nome:valor world".split(" "));

        assertEquals("valor", args.getFlag("nome").getFlagValue());

        //Taking the flag rearranges the positionals: the flag token is gone, indices close up
        assertEquals("hello", args.get(0).toString());
        assertEquals("world", args.get(1).toString());
        assertEquals(2, args.getStringArgs().size());
    }

    // ------------------------------------------------------------------
    // F2 - "-msg:'multi word'" spans tokens until the closing quote
    // ------------------------------------------------------------------

    @Test
    void f2_quotedFlagValueSpansTokensUntilTheClosingQuote() {
        MultiArgumentos args = new MultiArgumentos("say -msg:'multi word phrase' end".split(" "));

        assertEquals("multi word phrase", args.getFlag("msg").getFlagValue());
        assertEquals("say", args.get(0).toString());
        assertEquals("end", args.get(1).toString());
    }

    // ------------------------------------------------------------------
    // F3 - "-5" is NOT a flag (negative-number guard)
    // ------------------------------------------------------------------

    @Test
    void f3_negativeNumberIsNotTreatedAsAFlag() {
        MultiArgumentos args = new MultiArgumentos("give -5".split(" "));

        assertTrue(args.getFlags().isEmpty());
        assertEquals("-5", args.get(1).toString());
    }

    // ------------------------------------------------------------------
    // F4 - getFlag("x") == getFlag("-x"), case-insensitive; "--x" and "-x" are DISTINCT flags today
    // ------------------------------------------------------------------

    @Test
    void f4_flagLookupIsCaseInsensitiveAndAtLeastOneDashIsEnforcedButDoubleDashIsDistinct() {
        MultiArgumentos args = new MultiArgumentos("cmd -X:1 --x:2".split(" "));

        assertEquals(args.getFlag("-x").getInteger(), args.getFlag("x").getInteger());
        assertEquals(1, args.getFlag("x").getInteger());
        assertEquals(2, args.getFlag("--x").getInteger());
    }

    // ------------------------------------------------------------------
    // F5 - a missing flag is FlagedArgumento.EMPTY_ARG: isSet() == false, raw value "false"
    // ------------------------------------------------------------------

    @Test
    void f5_missingFlagIsTheEmptyArgWithFalseAsItsRawValue() {
        MultiArgumentos args = new MultiArgumentos("cmd".split(" "));

        FlagedArgumento flag = args.getFlag("missing");

        assertFalse(flag.isSet());
        assertTrue(flag.equals("false"));
    }

    // ------------------------------------------------------------------
    // F6 - "-flag:" (colon with no value) crashes - a KNOWN bug F4 fixes; pinned as-is
    // ------------------------------------------------------------------

    @Test
    void f6_colonWithNoValueCrashesWithArrayIndexOutOfBounds() {
        MultiArgumentos args = new MultiArgumentos(new String[]{"-flag:"});

        assertThrows(ArrayIndexOutOfBoundsException.class, args::getFlags);
    }

    // ------------------------------------------------------------------
    // F7 - "-time:10:30" truncates to "10" - a KNOWN bug F4 fixes; pinned as-is
    // ------------------------------------------------------------------

    @Test
    void f7_multipleColonsTruncateTheFlagValue() {
        MultiArgumentos args = new MultiArgumentos(new String[]{"-time:10:30"});

        assertTrue(args.getFlag("time").equals("10"));
    }

    // ------------------------------------------------------------------
    // F8 - emptyArgs(...), getStringArg out of range -> "", get out of range -> EMPTY_ARG
    // ------------------------------------------------------------------

    @Test
    void f8_emptyArgsAndOutOfRangeAccessors() {
        MultiArgumentos args = new MultiArgumentos(new String[]{"only"});

        assertFalse(args.emptyArgs(0));
        assertTrue(args.emptyArgs(1)); //index 1 doesn't exist

        assertEquals("", args.getStringArg(5));
        assertEquals(Argumento.EMPTY_ARG, args.get(5));
    }
}
