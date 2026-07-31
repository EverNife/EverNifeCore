package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ResolvedArguments;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The single bag every family resolves into. Two ways to ask, and the difference between them is the
 * whole point: by type is a convenience that admits ties, by name is exact.
 */
class ResolvedArgumentsTest {

    @Test
    void aTypeLookupAnswersWithTheMostRecentValueOfThatType() {
        ResolvedArguments resolved = new ResolvedArguments();
        resolved.resolved("<from>", "alpha");
        resolved.resolved("<to>", "omega");

        assertEquals("omega", resolved.get(String.class));
    }

    @Test
    void aNameLookupAnswersTheParameterThatDeclaredIt() {
        ResolvedArguments resolved = new ResolvedArguments();
        resolved.resolved("<from>", "alpha");
        resolved.resolved("<to>", "omega");

        assertEquals("alpha", resolved.get("<from>", String.class));
        assertEquals("omega", resolved.get("<to>", String.class));
    }

    /** A flag and a positional never collide, because a name is compared exactly as it was written. */
    @Test
    void aFlagSpellingIsNotThePositionalOfTheSameWord() {
        ResolvedArguments resolved = new ResolvedArguments();
        resolved.resolved("<amount>", 10);
        resolved.resolved("--amount", 20);

        assertEquals(10, (int) resolved.get("<amount>", Integer.class));
        assertEquals(20, (int) resolved.get("--amount", Integer.class));
    }

    /** A value is filed under its concrete class, so asking for what it IS has to find it anyway. */
    @Test
    void aTypeLookupFindsAValueThroughTheInterfaceItImplements() {
        ResolvedArguments resolved = new ResolvedArguments();
        StringBuilder value = new StringBuilder("text");
        resolved.resolved("<text>", value);

        assertSame(value, resolved.get(CharSequence.class));
    }

    @Test
    void aNameLookupOfAnotherTypeAnswersNothingRatherThanTheWrongValue() {
        ResolvedArguments resolved = new ResolvedArguments();
        resolved.resolved("<amount>", 10);

        assertNull(resolved.get("<amount>", String.class));
        assertNull(resolved.get("<nobody>", Integer.class));
    }

    /** "Did not resolve" and "resolved to nothing" read the same to everybody downstream. */
    @Test
    void aNullValueIsAnAbsenceInsteadOfAnEntry() {
        ResolvedArguments resolved = new ResolvedArguments();
        resolved.resolved("<player>", null);

        assertNull(resolved.get("<player>", String.class));
        assertNull(resolved.get(String.class));
    }

    @Test
    void aParameterThatDeclaresNoNameStillLandsInTheBagByType() {
        ResolvedArguments resolved = new ResolvedArguments();
        resolved.resolved(null, "captured");

        assertEquals("captured", resolved.get(String.class));
    }

    @Test
    void theBagOfAParserExercisedOutsideADispatchIsEmptyAndItsOwn() {
        ResolvedArguments first = ResolvedArguments.none();
        first.resolved("<value>", "written");

        assertNull(ResolvedArguments.none().get(String.class), "one caller's bag is never another's");
    }
}
