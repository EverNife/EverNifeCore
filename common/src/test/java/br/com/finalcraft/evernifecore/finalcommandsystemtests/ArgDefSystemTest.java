package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.ICustomFinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.contexts.CustomizeContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code @Arg.def()}: the declarative default parsed by the argument's own {@code ArgParser}
 * when an [optional] argument is omitted, and the fail-fast registration guard that forbids it on
 * required/provided-by-context arguments.
 */
class ArgDefSystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("ArgDef", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // 1 - an omitted [optional] arg with def() gets the def() text parsed as if typed
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "argdef1cmd")
    public static class Scenario1_Cmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "[value]", def = "5") Integer value) {
            received = value;
        }
    }

    @Test
    void scenario1_omittedOptionalWithDefGetsTheParsedDefValue() {
        FinalCMDPluginCommand command = newHarness().register(new Scenario1_Cmd());
        Scenario1_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertEquals(5, Scenario1_Cmd.received);
    }

    // ------------------------------------------------------------------
    // 2 - def() honors the same context bound the parser would apply to a typed value
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "argdef2cmd")
    public static class Scenario2_Cmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "[page]", context = "[1:*]", def = "1") Integer page) {
            received = page;
        }
    }

    @Test
    void scenario2_defWithinTheContextBoundIsAccepted() {
        FinalCMDPluginCommand command = newHarness().register(new Scenario2_Cmd());
        Scenario2_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertEquals(1, Scenario2_Cmd.received);
    }

    // ------------------------------------------------------------------
    // 3 - an invalid def() (unparseable for the arg's type) is the developer's own text failing, not
    // a player's, so it does NOT take the degradation an optional argument gives a token nobody
    // recognized: it aborts, with the stack in the plugin's log
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "argdef3cmd")
    public static class Scenario3_Cmd {
        static boolean invoked = false;
        static Integer received = -1;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "[value]", def = "abc") Integer value) {
            invoked = true;
            received = value;
        }
    }

    @Test
    void scenario3_anUnparseableDefAbortsInsteadOfSilentlyResolvingToNull() {
        FinalCMDPluginCommand command = newHarness().register(new Scenario3_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        Scenario3_Cmd.invoked = false;
        Scenario3_Cmd.received = -1;

        harness.dispatch(command, sender, "sub");

        assertFalse(Scenario3_Cmd.invoked, "a def() the command cannot read is a bug in the command, not an absent value");
        assertEquals(-1, Scenario3_Cmd.received, "the method never ran, so it never got a null");
        assertFalse(sender.getMessages().isEmpty(), "whoever typed it is still told the argument did not work");
    }

    // ------------------------------------------------------------------
    // 4 - a def() outside the declared context bound is refused for a reason the sender cannot act on,
    // so it is reported as the command's own bug rather than as their bad input
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "argdef4cmd")
    public static class Scenario4_Cmd {
        static boolean invoked = false;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "[value]", context = "[1:*]", def = "0") Integer value) {
            invoked = true;
        }
    }

    @Test
    void scenario4_defOutsideTheContextBoundBlamesTheCommandInsteadOfTheSender() {
        FinalCMDPluginCommand command = newHarness().register(new Scenario4_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        Scenario4_Cmd.invoked = false;

        harness.dispatch(command, sender, "sub");

        assertFalse(Scenario4_Cmd.invoked);
        assertFalse(sender.getMessages().isEmpty(), "the argument did not work and they are still told so");

        for (String message : sender.getMessages()) {
            assertFalse(message.contains("higher than"),
                    "nobody typed the 0 - telling them it is out of range asks them to fix what they cannot reach: " + message);
        }
    }

    // ------------------------------------------------------------------
    // 5 - a value the player actually typed always wins over def()
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "argdef5cmd")
    public static class Scenario5_Cmd {
        static Integer received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "[value]", def = "5") Integer value) {
            received = value;
        }
    }

    @Test
    void scenario5_typedValueOverridesDef() {
        FinalCMDPluginCommand command = newHarness().register(new Scenario5_Cmd());
        Scenario5_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub 99");

        assertEquals(99, Scenario5_Cmd.received);
    }

    // ------------------------------------------------------------------
    // 6 - def() empty (the annotation default) keeps the old behavior: an omitted optional is null
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "argdef6cmd")
    public static class Scenario6_Cmd {
        static boolean invoked = false;
        static Integer received = -1;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg("[value]") Integer value) {
            invoked = true;
            received = value;
        }
    }

    @Test
    void scenario6_emptyDefKeepsTheOldNullBehavior() {
        FinalCMDPluginCommand command = newHarness().register(new Scenario6_Cmd());
        Scenario6_Cmd.invoked = false;
        Scenario6_Cmd.received = -1;

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertTrue(Scenario6_Cmd.invoked);
        assertNull(Scenario6_Cmd.received);
    }

    // ------------------------------------------------------------------
    // 7 - def() on a <required> arg fails registration (ArgMountException, swallowed to false - see
    // ArgParsingSystemTest#nameWithoutBracketsFailsRegistration)
    // ------------------------------------------------------------------

    public static class Scenario7_Cmd {
        @FinalCMD(aliases = "argdef7cmd")
        public void run(FCommandSender sender, @Arg(value = "<value>", def = "5") Integer value) {}
    }

    @Test
    void scenario7_defOnARequiredArgFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new Scenario7_Cmd());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // 8 - def() on a required arg that also resolves from the sender fails registration the same way:
    // def() is an OPTIONAL-only attribute, whatever else the argument declares
    // ------------------------------------------------------------------

    public static class Scenario8a_Cmd {
        @FinalCMD(aliases = "argdef8acmd")
        public void run(FCommandSender sender, @Arg(value = "<value>", fromSender = true, def = "5") Integer value) {}
    }

    @Test
    void scenario8_defOnARequiredFromSenderArgFailsRegistration() {
        boolean requiredForm = newHarness().registerExpectingFailure(new Scenario8a_Cmd());

        assertFalse(requiredForm, "<x> with def() should fail registration, fromSender or not");
    }

    // ------------------------------------------------------------------
    // 9 - a %placeholder% inside def() is resolved by CustomizeContext.replace like the rest of
    // ArgData (name/context/locales)
    // ------------------------------------------------------------------

    public static class Scenario9_Cmd implements ICustomFinalCMD {
        static Integer received;

        @FinalCMD(aliases = "argdef9cmd")
        public void run(FCommandSender sender, @Arg(value = "[value]", def = "%default%") Integer value) {
            received = value;
        }

        @Override
        public void customize(@Nonnull CustomizeContext context) {
            context.replace("%default%", "7");
        }
    }

    @Test
    void scenario9_defPlaceholderIsResolvedByCustomizeContextReplace() {
        FinalCMDPluginCommand command = newHarness().register(new Scenario9_Cmd());
        Scenario9_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "");

        assertEquals(7, Scenario9_Cmd.received);
    }

    // ------------------------------------------------------------------
    // 10 - a def() containing a space is a single token to the parser, exactly like
    // 'new Argumento("a b")' - it does NOT get split into two positional args the way a real
    // player's raw command line would be tokenized before reaching FinalCMD
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "argdef10cmd")
    public static class Scenario10_Cmd {
        static String received = "not-called";

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "[value]", def = "a b") String value) {
            received = value;
        }
    }

    @Test
    void scenario10_defWithASpaceArrivesAsASingleToken() {
        FinalCMDPluginCommand command = newHarness().register(new Scenario10_Cmd());
        Scenario10_Cmd.received = "not-called";

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertEquals("a b", Scenario10_Cmd.received);
    }

    // ------------------------------------------------------------------
    // 11 - def() on an OPTIONAL variadic tail: an empty tail takes the declared default instead of
    // the empty text it would otherwise be handed
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "argdef11cmd")
    public static class Scenario11_Cmd {
        static String received;

        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg(value = "[reason...]", def = "no reason given") String reason) {
            received = reason;
        }
    }

    @Test
    void scenario11_anEmptyOptionalTailTakesItsDeclaredDefault() {
        FinalCMDPluginCommand command = newHarness().register(new Scenario11_Cmd());
        Scenario11_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub");

        assertEquals("no reason given", Scenario11_Cmd.received);
    }

    @Test
    void scenario11_aTailWithTokensIgnoresItsDefault() {
        FinalCMDPluginCommand command = newHarness().register(new Scenario11_Cmd());
        Scenario11_Cmd.received = null;

        harness.dispatch(command, new TestCommandSender("console"), "sub griefou a base");

        assertEquals("griefou a base", Scenario11_Cmd.received);
    }
}
