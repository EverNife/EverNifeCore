package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.implementation;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HyFinalCMDPluginCommandTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void tearDown() {
        if (harness != null) harness.close();
    }

    public static class ThreeLabels {
        @FinalCMD(aliases = {"ecc", "ecoreconformance", "EcConf"})
        public void root(FCommandSender sender) {
        }
    }

    @Test
    void everyLabelAfterTheFirstBecomesAnAliasOfTheServerCommand() {
        harness = new FinalCmdTestHarness("HyAliases", tempDir);
        FinalCMDPluginCommand command = harness.register(new ThreeLabels());

        HyFinalCMDPluginCommand hytaleCommand = new HyFinalCMDPluginCommand(command, new CommandManager());

        assertEquals("ecc", hytaleCommand.getName());
        assertEquals(Set.of("ecoreconformance", "ecconf"), hytaleCommand.getAliases()); //the server lowercases them
    }
}
