package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.pageviewer.PageVisualization;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a number argument answers with, and what the numeric mini-DSL accepts.
 * <p>
 * The theme is that a declared type is a promise: the parser hands back the type the parameter asked
 * for, and a value that type cannot hold is refused out loud instead of being bent into range.
 */
class NumericParserSystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void reset() {
        TypedNumbersCmd.received = null;
        BoundedCmd.received = null;
        PagesCmd.received = null;
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("Numbers", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // The declared type is what comes back
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "typednumberscmd")
    public static class TypedNumbersCmd {
        static Number received;

        @FinalCMD.SubCMD(subcmd = "afloat")
        public void afloat(FCommandSender sender, @Arg("<value>") Float value) {
            received = value;
        }

        @FinalCMD.SubCMD(subcmd = "along")
        public void along(FCommandSender sender, @Arg("<value>") Long value) {
            received = value;
        }

        @FinalCMD.SubCMD(subcmd = "ashort")
        public void ashort(FCommandSender sender, @Arg("<value>") Short value) {
            received = value;
        }

        @FinalCMD.SubCMD(subcmd = "anint")
        public void anint(FCommandSender sender, @Arg("<value>") Integer value) {
            received = value;
        }
    }

    @Test
    void aFloatArgumentReceivesAFloat() {
        FinalCMDPluginCommand command = newHarness().register(new TypedNumbersCmd());

        harness.dispatch(command, Senders.console(), "afloat 2.5");

        assertEquals(Float.valueOf(2.5f), TypedNumbersCmd.received, "a Float parameter gets a Float, never a Double");
    }

    @Test
    void aLongArgumentReceivesALong() {
        FinalCMDPluginCommand command = newHarness().register(new TypedNumbersCmd());

        harness.dispatch(command, Senders.console(), "along 5000000000");

        assertEquals(Long.valueOf(5000000000L), TypedNumbersCmd.received, "a Long parameter reaches past the Integer range");
    }

    @Test
    void aShortArgumentReceivesAShort() {
        FinalCMDPluginCommand command = newHarness().register(new TypedNumbersCmd());

        harness.dispatch(command, Senders.console(), "ashort 12");

        assertEquals(Short.valueOf((short) 12), TypedNumbersCmd.received, "a Short parameter gets a Short");
    }

    @Test
    void aValueTheTypeCannotHoldIsRefusedInsteadOfClamped() {
        FinalCMDPluginCommand command = newHarness().register(new TypedNumbersCmd());
        TestCommandSender sender = Senders.console();

        harness.dispatch(command, sender, "anint 5000000000");

        assertNull(TypedNumbersCmd.received, "an Integer parameter must not silently receive Integer.MAX_VALUE");
        assertTrue(sender.anyMessageContains("2147483647"),
                "the refusal names the range the value missed: " + sender.getMessages());
    }

    // ------------------------------------------------------------------
    // The interval mini-DSL
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "boundedcmd")
    public static class BoundedCmd {
        static Number received;

        @FinalCMD.SubCMD(subcmd = "upto")
        public void upto(FCommandSender sender, @Arg(value = "<value>", context = "[*:10]") Integer value) {
            received = value;
        }
    }

    @Test
    void anOpenLeftSideMeansNoFloorAtAll() {
        FinalCMDPluginCommand command = newHarness().register(new BoundedCmd());
        TestCommandSender sender = Senders.console();

        harness.dispatch(command, sender, "upto 5");

        assertEquals(Integer.valueOf(5), BoundedCmd.received,
                "[*:10] reads as 'up to 10', so 5 is inside it: " + sender.getMessages());
    }

    @FinalCMD(aliases = "invertedintervalcmd")
    public static class InvertedIntervalCmd {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg(value = "<value>", context = "[5:1]") Integer value) {}
    }

    @Test
    void anInvertedIntervalIsRefusedAtBoot() {
        String message = newHarness().registerExpectingError(new InvertedIntervalCmd()).getMessage();

        assertTrue(message.contains("[5:1]"), "the message shows the interval: " + message);
        assertTrue(message.contains("ceiling"), "and says what is wrong with it: " + message);
    }

    @FinalCMD(aliases = "fractionalboundcmd")
    public static class FractionalBoundCmd {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg(value = "<value>", context = "[0:2.5]") Integer value) {}
    }

    @Test
    void aFractionalBoundOnAnIntegerIsRefusedAtBoot() {
        String message = newHarness().registerExpectingError(new FractionalBoundCmd()).getMessage();

        assertTrue(message.contains("2.5"), "the message shows the bound: " + message);
        assertTrue(message.contains("Integer"), "and the type that cannot sit on it: " + message);
    }

    // ------------------------------------------------------------------
    // The key=value mini-DSL
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "emptykeycmd")
    public static class EmptyContextKeyCmd {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg(value = "<id>", context = "[=]") UUID id) {}
    }

    @Test
    void aContextEntryWithoutAKeyIsRefusedAtBoot() {
        String message = newHarness().registerExpectingError(new EmptyContextKeyCmd()).getMessage();

        assertTrue(message.contains("names no key"), "the refusal says which half is missing: " + message);
    }

    @FinalCMD(aliases = "emptyvaluecmd")
    public static class EmptyContextValueCmd {
        @FinalCMD.SubCMD(subcmd = "leaf")
        public void leaf(FCommandSender sender, @Arg(value = "<id>", context = "[online=]") UUID id) {}
    }

    @Test
    void aContextEntryWithAnEmptyValueIsRefusedAtBoot() {
        String message = newHarness().registerExpectingError(new EmptyContextValueCmd()).getMessage();

        assertTrue(message.contains("online="), "the refusal quotes the entry: " + message);
        assertTrue(message.contains("empty value"), "and says an empty value is not 'true': " + message);
    }

    // ------------------------------------------------------------------
    // A page argument answers the same way to everybody
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "pagescmd")
    public static class PagesCmd {
        static PageVisualization received;

        @FinalCMD.SubCMD(subcmd = "list")
        public void list(FCommandSender sender, @Arg("<page>") PageVisualization page) {
            received = page;
        }
    }

    @Test
    void aNegativePageAnswersTheSameWithOrWithoutTheIntervalPermission() {
        FinalCMDPluginCommand command = newHarness().register(new PagesCmd());

        TestCommandSender plain = Senders.console("plain");
        harness.dispatch(command, plain, "list -5");

        TestCommandSender withInterval = Senders.console("withInterval").grant(PermissionNodes.EVERNIFECORE_PAGEVIEWER_INTERVAL);
        harness.dispatch(command, withInterval, "list -5");

        assertEquals(plain.getMessages(), withInterval.getMessages(),
                "a permission decides what a sender MAY do, never what a typo MEANS");
        assertNull(PagesCmd.received, "-5 is not a page either way");
    }

    @Test
    void anIntervalStillWorksForWhoeverMayUseIt() {
        FinalCMDPluginCommand command = newHarness().register(new PagesCmd());

        harness.dispatch(command, Senders.console().grant(PermissionNodes.EVERNIFECORE_PAGEVIEWER_INTERVAL), "list 2-4");

        assertNotNull(PagesCmd.received, "2-4 is still an interval");
        assertEquals(2, PagesCmd.received.getPageStart());
        assertEquals(4, PagesCmd.received.getPageEnd());
    }

    // ------------------------------------------------------------------
    // The same promise on the token itself: Argumento answers with the type asked for, and answers
    // NOTHING for a number that type cannot hold - it never wraps around into a value nobody typed
    // ------------------------------------------------------------------

    @Test
    void aTokenAnswersEveryIntegralTypeItFitsIn() {
        Argumento hundred = new Argumento("100");

        assertEquals(Byte.valueOf((byte) 100), hundred.getNumberWrapper(Byte.class).get());
        assertEquals(Short.valueOf((short) 100), hundred.getNumberWrapper(Short.class).get());
        assertEquals(Integer.valueOf(100), hundred.getNumberWrapper(Integer.class).get());
        assertEquals(Long.valueOf(100L), hundred.getNumberWrapper(Long.class).get());
    }

    @Test
    void aTokenAnIntegralTypeCannotHoldIsNotThatType() {
        assertNull(new Argumento("200").getNumberWrapper(Byte.class), "200 wrapped around to -56 for years");
        assertNull(new Argumento("40000").getNumberWrapper(Short.class));
        assertNull(new Argumento("notANumber").getNumberWrapper(Byte.class));
        assertNull(new Argumento("notANumber").getNumberWrapper(Short.class));
    }
}
