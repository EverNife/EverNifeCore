package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flag declared {@code insideTail} is read even after the variadic tail opened, and leaves it; every
 * other marker-shaped word there is still the sender's text.
 */
class FlagInsideTailSystemTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    @FinalCMD(aliases = "tailkit")
    public static class TailKit_Cmd {
        static Boolean soulBound;
        static Boolean silent;
        static String time;

        @FinalCMD.SubCMD(subcmd = "give")
        public void give(FCommandSender sender,
                         @Arg("<kit>") String kit,
                         @Arg.Flag(value = "--SoulBound", aliases = "-sb", def = "false", insideTail = true) Boolean soulBound,
                         @Arg.Flag(value = "--silent", def = "false") Boolean silent,
                         @Arg("<time...>") String time) {
            TailKit_Cmd.soulBound = soulBound;
            TailKit_Cmd.silent = silent;
            TailKit_Cmd.time = time;
        }
    }

    private FinalCMDPluginCommand command;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("FlagInsideTail", tempDir);
        command = harness.register(new TailKit_Cmd());
        TailKit_Cmd.soulBound = null;
        TailKit_Cmd.silent = null;
        TailKit_Cmd.time = null;
    }

    @Test
    void aTailReadFlagAfterTheTailIsExtractedAndLeavesIt() {
        harness.dispatch(command, new TestCommandSender("console"), "give vip 7d -sb");

        assertEquals(Boolean.TRUE, TailKit_Cmd.soulBound);
        assertEquals("7d", TailKit_Cmd.time);
    }

    @Test
    void anotherFlagInsideTheTailStaysText() {
        harness.dispatch(command, new TestCommandSender("console"), "give vip 7d --silent -sb");

        assertEquals(Boolean.FALSE, TailKit_Cmd.silent, "--silent is not declared insideTail");
        assertEquals(Boolean.TRUE, TailKit_Cmd.soulBound);
        assertEquals("7d --silent", TailKit_Cmd.time);
    }

    @Test
    void anUndeclaredMarkerInsideTheTailStaysTextWithoutAnError() {
        TestCommandSender sender = new TestCommandSender("console");
        harness.dispatch(command, sender, "give vip 7d -zombie");

        assertEquals(Boolean.FALSE, TailKit_Cmd.soulBound);
        assertEquals("7d -zombie", TailKit_Cmd.time);
    }

    @Test
    void theTabOffersOnlyTheTailReadFlagInsideTheTail() {
        TestCommandSender sender = new TestCommandSender("console");

        List<String> suggestions = harness.tab(command, sender, "give", "vip", "7d", "-");

        assertEquals(List.of("--SoulBound"), suggestions);
        assertTrue(harness.tab(command, sender, "give", "vip", "7d", "--si").isEmpty(),
                "--silent is text inside the tail, so it is not offered there");
    }
}
