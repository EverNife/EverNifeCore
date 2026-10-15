package br.com.finalcraft.evernifecore.hytale.commands.finalcmd.implementation;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    public static class PlayerOnly {
        FPlayer caller;

        @FinalCMD(aliases = {"whoami"})
        public void root(FPlayer player) {
            this.caller = player;
        }
    }

    @Test
    void aPlayerRefSenderReachesTheCommandAsThatPlayer() {
        harness = new FinalCmdTestHarness("HyPlayerRef", tempDir);
        PlayerOnly executor = new PlayerOnly();
        HyFinalCMDPluginCommand hytaleCommand = new HyFinalCMDPluginCommand(harness.register(executor), new CommandManager());
        //the 0.7.1 server dispatches a player's command (chat or sudo) with the PlayerRef as the sender
        PlayerRef playerRef = new PlayerRef(null, UUID.randomUUID(), "Steve", "en_us", null, null);

        hytaleCommand.dispatch(playerRef, "whoami");

        assertNotNull(executor.caller, "a player-only command refused the PlayerRef sender as if it were the console");
        assertEquals(playerRef.getUuid(), executor.caller.getUniqueId());
    }

    @Test
    void theServerAsksNoGeneratedPermissionNodeOnlyTheFinalCmdOne() {
        harness = new FinalCmdTestHarness("HyNoNode", tempDir);
        HyFinalCMDPluginCommand hytaleCommand = new HyFinalCMDPluginCommand(harness.register(new PlayerOnly()), new CommandManager());
        PlayerRef playerRef = new PlayerRef(null, UUID.randomUUID(), "Alex", "en_us", null, null);

        assertNull(hytaleCommand.getPermission(), "the server would also demand its own node: " + hytaleCommand.getPermission());
        assertTrue(hytaleCommand.hasPermission(playerRef), "a player with no node at all passes the server's gate");
    }
}
