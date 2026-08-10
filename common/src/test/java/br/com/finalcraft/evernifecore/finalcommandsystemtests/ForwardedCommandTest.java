package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.misc.CMDAlias;
import br.com.finalcraft.evernifecore.commands.misc.CMDECLocale;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Commands whose whole effect is another command being run.
 *
 * <p>Nothing else changes when one of these executes - no message, no config, no state - so what the
 * server was asked to run IS the behaviour, and a test that cannot read it cannot tell a working alias
 * from one that silently drops what it was given.</p>
 */
class ForwardedCommandTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private List<String> forwardedLines() {
        List<String> lines = new ArrayList<>();
        for (TestPlatform.DispatchedCommand dispatched : harness.platform.getSenderCommands()) {
            lines.add(dispatched.command);
        }
        return lines;
    }

    @Test
    void anAliasRunsTheCommandItStandsForAsWhoeverTypedIt() {
        harness = Commands.harness("Alias", tempDir);
        FinalCMDPluginCommand command = harness.register(new CMDAlias("wp", "warp"));
        TestFPlayerSender player = new TestFPlayerSender("Steve");

        harness.dispatch(command, player, "spawn");

        assertEquals(1, harness.platform.getSenderCommands().size(),
                "one alias, one command: " + forwardedLines());
        TestPlatform.DispatchedCommand forwarded = harness.platform.getSenderCommands().get(0);
        assertEquals("warp spawn", forwarded.command, "the alias carries its arguments over");
        assertSame(player, forwarded.sender,
                "run as the player, not the console - the target command still checks their permissions");
    }

    /** Setting the locale of every plugin is done by re-running '/eclocale set' once per plugin. */
    @Test
    void setallAsksForOneSetPerPlugin() {
        harness = Commands.harness("LocaleSetAll", tempDir);
        FinalCMDPluginCommand command = harness.register(new CMDECLocale());
        TestFPlayerSender admin = new TestFPlayerSender("Admin")
                .grant(PermissionNodes.EVERNIFECORE_COMMAND_FCLOCALE);

        harness.dispatch(command, admin, "setall pt_br");

        assertTrue(forwardedLines().contains("eclocale set " + harness.pluginName + " pt_br"),
                "every registered plugin has to be named in one of the forwarded commands: " + forwardedLines());
    }

    /** An empty '/eclocale setall' is a help line, not a locale change swept over every plugin. */
    @Test
    void setallWithoutALanguageChangesNothing() {
        harness = Commands.harness("LocaleSetAllEmpty", tempDir);
        FinalCMDPluginCommand command = harness.register(new CMDECLocale());
        TestFPlayerSender admin = new TestFPlayerSender("Admin")
                .grant(PermissionNodes.EVERNIFECORE_COMMAND_FCLOCALE);

        harness.dispatch(command, admin, "setall");

        assertTrue(forwardedLines().isEmpty(), "nothing was asked for, so nothing runs: " + forwardedLines());
    }
}
