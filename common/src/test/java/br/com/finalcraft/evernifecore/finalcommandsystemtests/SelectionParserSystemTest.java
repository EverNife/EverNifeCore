package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One policy for declared choices: whatever a {@code context()} lists is what the argument accepts -
 * nothing more, nothing less - matching is case-insensitive, and the value handed to the method is the
 * spelling its own author declared.
 * <p>
 * What used to happen instead was three policies in three parsers, plus a built-in boolean vocabulary
 * that answered for words the refusal message never offered.
 */
class SelectionParserSystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void reset() {
        ChoicesCmd.receivedBoolean = null;
        ChoicesCmd.receivedString = null;
        ChoicesCmd.receivedArgumento = null;
        PlainBooleanCmd.received = null;
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("Choices", tempDir);
        return harness;
    }

    public enum Metal {
        GOLD,
        SILVER
    }

    @FinalCMD(aliases = "choicescmd")
    public static class ChoicesCmd {
        static Boolean receivedBoolean;
        static String receivedString;
        static Argumento receivedArgumento;

        @FinalCMD.SubCMD(subcmd = "trade")
        public void trade(FCommandSender sender, @Arg(value = "<side>", context = "[buy|sell]") Boolean side) {
            receivedBoolean = side;
        }

        @FinalCMD.SubCMD(subcmd = "switch")
        public void toggle(FCommandSender sender, @Arg(value = "<state>", context = "[on|off]") Boolean state) {
            receivedBoolean = state;
        }

        @FinalCMD.SubCMD(subcmd = "role")
        public void role(FCommandSender sender, @Arg(value = "<role>", context = "admin") String role) {
            receivedString = role;
        }

        @FinalCMD.SubCMD(subcmd = "action")
        public void action(FCommandSender sender, @Arg(value = "<action>", context = "[Add|Remove]") String action) {
            receivedString = action;
        }

        @FinalCMD.SubCMD(subcmd = "raw")
        public void raw(FCommandSender sender, @Arg(value = "<action>", context = "[Add|Remove]") Argumento action) {
            receivedArgumento = action;
        }
    }

    // ------------------------------------------------------------------
    // A declared pair is the whole truth table
    // ------------------------------------------------------------------

    @Test
    void aDeclaredPairDoesNotQuietlyAcceptTheBuiltinVocabulary() {
        FinalCMDPluginCommand command = newHarness().register(new ChoicesCmd());
        TestCommandSender sender = Senders.console();

        harness.dispatch(command, sender, "trade sim");

        assertNull(ChoicesCmd.receivedBoolean, "[buy|sell] never offered 'sim', so it must not accept it");
        assertTrue(sender.anyMessageContains("buy"), "the refusal lists what IS accepted: " + sender.getMessages());
    }

    @Test
    void aDeclaredPairReadsByPosition() {
        FinalCMDPluginCommand command = newHarness().register(new ChoicesCmd());

        harness.dispatch(command, Senders.console(), "trade buy");
        assertEquals(Boolean.TRUE, ChoicesCmd.receivedBoolean, "the first option is the true one");

        harness.dispatch(command, Senders.console(), "trade sell");
        assertEquals(Boolean.FALSE, ChoicesCmd.receivedBoolean, "the second option is the false one");
    }

    @Test
    void aPairThatAgreesWithTheVocabularyStillReadsTheSameWay() {
        FinalCMDPluginCommand command = newHarness().register(new ChoicesCmd());

        harness.dispatch(command, Senders.console(), "switch on");
        assertEquals(Boolean.TRUE, ChoicesCmd.receivedBoolean);

        harness.dispatch(command, Senders.console(), "switch off");
        assertEquals(Boolean.FALSE, ChoicesCmd.receivedBoolean);
    }

    @FinalCMD(aliases = "invertedbooleancmd")
    public static class InvertedBooleanCmd {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg(value = "<state>", context = "[off|on]") Boolean state) {}
    }

    @Test
    void aPairWrittenAgainstItsOwnMeaningIsRefusedAtBoot() {
        String message = newHarness().registerExpectingError(new InvertedBooleanCmd()).getMessage();

        assertTrue(message.contains("[off|on]"), "the message quotes the context: " + message);
        assertTrue(message.contains("first option"), "and says which side is the true one: " + message);
    }

    @FinalCMD(aliases = "plainbooleancmd")
    public static class PlainBooleanCmd {
        static Boolean received;

        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg("<state>") Boolean state) {
            received = state;
        }
    }

    @Test
    void withoutADeclaredPairTheBuiltinVocabularyStillAnswers() {
        FinalCMDPluginCommand command = newHarness().register(new PlainBooleanCmd());

        harness.dispatch(command, Senders.console(), "leaf sim");

        assertEquals(Boolean.TRUE, PlainBooleanCmd.received, "a plain Boolean is what the vocabulary exists for");
    }

    // ------------------------------------------------------------------
    // An enum option that names no constant is a typo, not a choice
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "typedenumcmd")
    public static class TypoedEnumCmd {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg(value = "<metal>", context = "[GOLDEN]") Metal metal) {}
    }

    @Test
    void anEnumOptionThatNamesNoConstantIsRefusedAtBoot() {
        String message = newHarness().registerExpectingError(new TypoedEnumCmd()).getMessage();

        assertTrue(message.contains("GOLDEN"), "the message quotes the option: " + message);
        assertTrue(message.contains("GOLD") && message.contains("SILVER"),
                "and hands over the constants that do exist: " + message);
    }

    // ------------------------------------------------------------------
    // A declared context is a choice even when it lists one option
    // ------------------------------------------------------------------

    @Test
    void aSingleDeclaredOptionIsStillAChoice() {
        FinalCMDPluginCommand command = newHarness().register(new ChoicesCmd());
        TestCommandSender sender = Senders.console();

        harness.dispatch(command, sender, "role anything");

        assertNull(ChoicesCmd.receivedString, "context = \"admin\" was written to mean exactly that word");

        harness.dispatch(command, Senders.console(), "role admin");
        assertEquals("admin", ChoicesCmd.receivedString);
    }

    @Test
    void theValueHandedOverKeepsTheDeclaredSpelling() {
        FinalCMDPluginCommand command = newHarness().register(new ChoicesCmd());

        harness.dispatch(command, Senders.console(), "action add");

        assertEquals("Add", ChoicesCmd.receivedString, "matching is case-insensitive; the value is the declared spelling");
    }

    @Test
    void aRawArgumentoAppliesItsDeclaredContextToo() {
        FinalCMDPluginCommand command = newHarness().register(new ChoicesCmd());
        TestCommandSender sender = Senders.console();

        harness.dispatch(command, sender, "raw whatever");
        assertNull(ChoicesCmd.receivedArgumento, "a raw token type is a convenience, not an exemption");

        harness.dispatch(command, Senders.console(), "raw remove");
        assertEquals("Remove", String.valueOf(ChoicesCmd.receivedArgumento));
    }
}
