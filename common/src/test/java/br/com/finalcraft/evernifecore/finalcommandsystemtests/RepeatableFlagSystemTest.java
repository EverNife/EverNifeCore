package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flag written more than once is read by the type its parameter declares: {@code String} joins the occurrences,
 * {@code List<E>} keeps each in order, {@code Set<E>} keeps the first arrival of each; any other type refuses the
 * second occurrence.
 */
class RepeatableFlagSystemTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("RepeatFlag", tempDir);
        return harness;
    }

    @FinalCMD(aliases = "repeatcmd")
    public static class Repeat_Cmd {
        static boolean ran;
        static String note;
        static List<String> tags;
        static Set<String> unique;
        static List<ObjectNode> settings;
        static List<String> withDef;

        @FinalCMD.SubCMD(subcmd = "note")
        public void note(FCommandSender sender, @Arg.Flag("--note") String note) {
            ran = true;
            Repeat_Cmd.note = note;
        }

        @FinalCMD.SubCMD(subcmd = "tags")
        public void tags(FCommandSender sender, @Arg.Flag(value = "--tag", aliases = "-t") List<String> tags) {
            ran = true;
            Repeat_Cmd.tags = tags;
        }

        @FinalCMD.SubCMD(subcmd = "unique")
        public void unique(FCommandSender sender, @Arg.Flag("--add") Set<String> unique) {
            ran = true;
            Repeat_Cmd.unique = unique;
        }

        @FinalCMD.SubCMD(subcmd = "settings")
        public void settings(FCommandSender sender, @Arg.Flag(value = "--flag-settings", aliases = "--fs") List<ObjectNode> settings) {
            ran = true;
            Repeat_Cmd.settings = settings;
        }

        @FinalCMD.SubCMD(subcmd = "def")
        public void def(FCommandSender sender, @Arg.Flag(value = "--tag", def = "base") List<String> withDef) {
            ran = true;
            Repeat_Cmd.withDef = withDef;
        }

        @FinalCMD.SubCMD(subcmd = "force")
        public void force(FCommandSender sender, @Arg.Flag("--force") Boolean force) {
            ran = true;
        }

        @FinalCMD.SubCMD(subcmd = "page")
        public void page(FCommandSender sender, @Arg.Flag("--page") Integer page) {
            ran = true;
        }
    }

    private FinalCMDPluginCommand command() {
        Repeat_Cmd.ran = false;
        Repeat_Cmd.note = null;
        Repeat_Cmd.tags = null;
        Repeat_Cmd.unique = null;
        Repeat_Cmd.settings = null;
        Repeat_Cmd.withDef = null;
        return newHarness().register(new Repeat_Cmd());
    }

    @Test
    void aStringFlagJoinsItsOccurrencesWithOneSpace() {
        FinalCMDPluginCommand command = command();

        harness.dispatch(command, new TestCommandSender("console"), "note --note 'stay' --note out");

        assertEquals("stay out", Repeat_Cmd.note);
    }

    @Test
    void anEmptyOccurrenceCountsButAddsNothing() {
        FinalCMDPluginCommand command = command();

        harness.dispatch(command, new TestCommandSender("console"), "note --note a --note \"\" --note b");
        assertEquals("a b", Repeat_Cmd.note);

        //Nothing but empty occurrences is one empty value, which the String parser refuses exactly as it does a single one
        Repeat_Cmd.ran = false;
        TestCommandSender sender = new TestCommandSender("console");
        harness.dispatch(command, sender, "note --note \"\" --note \"\"");
        assertFalse(Repeat_Cmd.ran);
        assertFalse(sender.getMessages().isEmpty());
    }

    @Test
    void aListFlagKeepsEveryOccurrenceInOrder() {
        FinalCMDPluginCommand command = command();

        harness.dispatch(command, new TestCommandSender("console"), "tags --tag pvp -t arena --tag pvp");

        assertEquals(Arrays.asList("pvp", "arena", "pvp"), Repeat_Cmd.tags);
    }

    @Test
    void aSetFlagDeduplicatesAndKeepsTheArrivalOrder() {
        FinalCMDPluginCommand command = command();

        harness.dispatch(command, new TestCommandSender("console"), "unique --add zeta --add alpha --add zeta --add mid");

        assertEquals(Arrays.asList("zeta", "alpha", "mid"), new ArrayList<>(Repeat_Cmd.unique));
    }

    @Test
    void aListOfObjectNodeParsesEachOccurrenceWithTheElementParser() {
        FinalCMDPluginCommand command = command();

        harness.dispatch(command, new TestCommandSender("console"), "settings --fs revert=spawn --flag-settings '{\"stuckAfter\":5}'");

        assertEquals(2, Repeat_Cmd.settings.size());
        assertEquals("spawn", Repeat_Cmd.settings.get(0).get("revert").asText());
        assertEquals(5, Repeat_Cmd.settings.get(1).get("stuckAfter").asInt());
    }

    @Test
    void oneOccurrenceOfAListIsAListOfOne() {
        FinalCMDPluginCommand command = command();

        harness.dispatch(command, new TestCommandSender("console"), "tags --tag solo");

        assertEquals(Collections.singletonList("solo"), Repeat_Cmd.tags);
    }

    @Test
    void anAbsentRepeatableFlagIsNull() {
        FinalCMDPluginCommand command = command();

        harness.dispatch(command, new TestCommandSender("console"), "tags");

        assertTrue(Repeat_Cmd.ran);
        assertNull(Repeat_Cmd.tags);
    }

    @Test
    void theDefOfAListIsOneElement() {
        FinalCMDPluginCommand command = command();

        harness.dispatch(command, new TestCommandSender("console"), "def");

        assertEquals(Collections.singletonList("base"), Repeat_Cmd.withDef);
    }

    @Test
    void aBooleanFlagWrittenTwiceIsStillRefused() {
        FinalCMDPluginCommand command = command();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "force --force --force");

        assertFalse(Repeat_Cmd.ran);
        sender.assertAnyMessageContains("was written twice");
    }

    @Test
    void anIntegerFlagWrittenTwiceIsStillRefused() {
        FinalCMDPluginCommand command = command();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "page --page 1 --page 2");

        assertFalse(Repeat_Cmd.ran);
        sender.assertAnyMessageContains("was written twice");
    }

    @FinalCMD(aliases = "rawlistcmd")
    public static class RawList_Cmd {
        @SuppressWarnings("rawtypes")
        @FinalCMD.SubCMD(subcmd = "sub")
        public void sub(FCommandSender sender, @Arg.Flag("--tag") List tags) {
        }
    }

    @Test
    void aRawListIsRefusedAtMount() {
        ArgMountException error = newHarness().registerExpectingError(new RawList_Cmd());

        assertTrue(error.getMessage().contains("--tag"), error.getMessage());
        assertTrue(error.getMessage().contains("List<String>"), error.getMessage());
    }
}
