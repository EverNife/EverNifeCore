package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A player argument completes only the players the sender can see, so tab never reveals a vanished one. */
class PlayerTabVisibilitySystemTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private FinalCMDPluginCommand command;

    private final TestFPlayerSender alice = new TestFPlayerSender("Alice");
    private final TestFPlayerSender bob = new TestFPlayerSender("Bob");
    private final TestFPlayerSender vanished = new TestFPlayerSender("Ghost");

    @FinalCMD(aliases = "tpvis")
    public static class Tp_Cmd {
        @FinalCMD.SubCMD(subcmd = "to")
        public void to(FCommandSender sender, @Arg("<player>") FPlayer target) {
        }
    }

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("TabVis", tempDir);
        harness.platform.setOnlinePlayers(Arrays.<FPlayer>asList(alice, bob, vanished));
        command = harness.register(new Tp_Cmd());
        alice.hide(vanished);
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    @Test
    void aSenderWhoCannotSeeAPlayerIsNotOfferedThem() {
        assertEquals(Arrays.asList("Alice", "Bob"), harness.tab(command, alice, "to", ""));
    }

    @Test
    void aSenderWhoCanSeeThePlayerIsOfferedThem() {
        assertEquals(Arrays.asList("Alice", "Bob", "Ghost"), harness.tab(command, bob, "to", ""));
    }

    @Test
    void theConsoleSeesEveryone() {
        assertEquals(Arrays.asList("Alice", "Bob", "Ghost"), harness.tab(command, new TestCommandSender("console"), "to", ""));
    }
}
