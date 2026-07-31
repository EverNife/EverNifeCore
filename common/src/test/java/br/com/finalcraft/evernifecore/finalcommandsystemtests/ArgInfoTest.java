package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgRequirementType;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgSource;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ResolvedArguments;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserPageVisualization;
import br.com.finalcraft.evernifecore.pageviewer.PageVisualization;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One piece of metadata for every family, which only works while it stays honest about where the
 * argument came from: what has no position never answers as if it had one.
 */
class ArgInfoTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    //The delegating parser below refuses through FCMessageUtil, which does not exist in a bare JVM
    private FinalCmdTestHarness harness;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("ArgInfo", tempDir);
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private static ArgData argData(String name) {
        return new ArgData().setName(name).setContext("");
    }

    @Test
    void onlyAPositionalArgumentAnswersWithAnIndex() {
        assertEquals(3, ArgInfo.positional(String.class, argData("<value>"), 3, ArgRequirementType.REQUIRED).getIndex());
    }

    @Test
    void aFlagHasNoPositionAndSaysSoInsteadOfAnsweringOne() {
        ArgInfo flag = ArgInfo.flag(Integer.class, argData("--page"));

        assertEquals(ArgSource.FLAG, flag.getSource());
        IllegalStateException refused = assertThrows(IllegalStateException.class, flag::getIndex);
        assertTrue(refused.getMessage().contains("--page"), "the message names the argument: " + refused.getMessage());
        assertTrue(refused.getMessage().contains("FLAG"), "and the source it really has: " + refused.getMessage());
    }

    @Test
    void aContextualArgumentHasNoPositionAndSaysSoInsteadOfAnsweringOne() {
        ArgInfo contextual = ArgInfo.contextual(PageVisualization.class, argData(""));

        assertEquals(ArgSource.CONTEXTUAL, contextual.getSource());
        IllegalStateException refused = assertThrows(IllegalStateException.class, contextual::getIndex);
        assertTrue(refused.getMessage().contains("CONTEXTUAL"), "the message names the real source: " + refused.getMessage());
        assertTrue(refused.getMessage().contains("PageVisualization"),
                "an unannotated parameter is named by its type: " + refused.getMessage());
    }

    @Test
    void aValueThatNeverCameFromACommandLineHasNoPositionEither() {
        assertThrows(IllegalStateException.class, ArgInfo.standalone(Integer.class, argData("gui.rows"))::getIndex);
    }

    /**
     * Printing an argument is how somebody debugs one, so it can never be the thing that throws - which
     * it would be the moment it went through the getter instead of the field.
     */
    @Test
    void printingAnArgumentThatSitsAtNoPositionWorks() {
        assertTrue(ArgInfo.flag(Integer.class, argData("--page")).toString().contains("FLAG"));
        assertTrue(ArgInfo.contextual(String.class, argData("")).toString().contains("CONTEXTUAL"));
    }

    /** Nothing routes above a flag or a contextual parameter, so neither is ever optional. */
    @Test
    void everySourceWithoutAPositionIsRequired() {
        assertTrue(ArgInfo.flag(Integer.class, argData("--page")).isRequired());
        assertTrue(ArgInfo.contextual(String.class, argData("")).isRequired());
        assertTrue(ArgInfo.standalone(Integer.class, argData("gui.rows")).isRequired());
    }

    @Test
    void deriveForKeepsEverythingButTheType() {
        ArgData shared = argData("[page...]");
        ArgInfo original = ArgInfo.positional(String.class, shared, 2, ArgRequirementType.OPTIONAL, true);

        ArgInfo derived = original.deriveFor(Integer.class);

        assertEquals(Integer.class, derived.getArgumentType());
        assertSame(shared, derived.getArgData(), "both parsers read and write the same declaration");
        assertEquals(ArgSource.POSITIONAL, derived.getSource());
        assertEquals(2, derived.getIndex());
        assertEquals(ArgRequirementType.OPTIONAL, derived.getRequirementType());
        assertTrue(derived.isGreedy());
    }

    /**
     * The parser that delegates to another one over the same argument: it hands its own metadata over
     * rather than rebuilding it, which is the only reason it can be declared as a flag at all.
     */
    @Test
    void theDelegatingPageParserWorksOnAnArgumentThatSitsAtNoPosition() {
        ArgData declaration = argData("--page");
        ArgParserPageVisualization parser = new ArgParserPageVisualization(ArgInfo.flag(PageVisualization.class, declaration));

        assertEquals("[1:*]", declaration.getContext(), "a page starts at 1 unless the command says otherwise");

        assertEquals(ParseResult.Kind.DENIED, parser.parse(callOf(parser, "0")).getKind(),
                "the default context reached the delegate");
        assertEquals(4, parser.parse(callOf(parser, "4")).getValue().getPageStart());
    }

    private ParseCall callOf(ArgParserPageVisualization parser, String token) {
        return new ParseCall(new TestCommandSender("console"),
                new Argumento(token),
                parser.getArgInfo(),
                null,
                ResolvedArguments.none(),
                true);
    }
}
