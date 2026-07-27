package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.misc.CMDECAccount;
import br.com.finalcraft.evernifecore.commands.misc.CMDECStorage;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the registered shape of the two builtin commands renamed/unified onto the {@code ec*}
 * prefix: {@link CMDECAccount} (bare "account" alias dropped) and {@link CMDECStorage} (the
 * former {@code CMDStorageStatus}/{@code CMDStorageTransfer} pair fused into one command with
 * {@code status}/{@code transfer} subcommands, each gated by its own permission node).
 */
class BuiltinCommandShapeTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("BuiltinShape", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // CMDECAccount - registers under "ecaccount" only, the generic "account" alias is gone
    // ------------------------------------------------------------------

    @Test
    void ecAccountRegistersWithoutTheGenericAlias() {
        FinalCMDPluginCommand command = newHarness().register(new CMDECAccount());

        assertNotNull(command);
        assertEquals("ecaccount", command.getPrimaryLabel());
        assertArrayEquals(new String[0], command.getExtraLabels(), "no extra alias, in particular not the generic 'account'");
    }

    // ------------------------------------------------------------------
    // CMDECStorage - one command, two subcommands, each with its own permission node
    // ------------------------------------------------------------------

    @Test
    void ecStorageRegistersStatusAndTransferWithTheirOwnPermissions() {
        FinalCMDPluginCommand command = newHarness().register(new CMDECStorage());

        assertNotNull(command);
        assertEquals("ecstorage", command.getPrimaryLabel());
        assertEquals(2, command.getSubCommands().size());

        CMDMethodInterpreter status = command.getSubCommand("status");
        CMDMethodInterpreter transfer = command.getSubCommand("transfer");

        assertNotNull(status);
        assertNotNull(transfer);
        assertEquals(PermissionNodes.EVERNIFECORE_COMMAND_STORAGE_STATUS, status.getCmdData().getPermission());
        assertEquals(PermissionNodes.EVERNIFECORE_COMMAND_STORAGE_TRANSFER, transfer.getCmdData().getPermission());
    }

    @Test
    void ecStorageTransferRequiresTwoArgsAndSendsItsHelpLineWhenEmpty() {
        FinalCMDPluginCommand command = newHarness().register(new CMDECStorage());
        TestCommandSender console = new TestCommandSender("console")
                .grant(PermissionNodes.EVERNIFECORE_COMMAND_STORAGE_TRANSFER);

        harness.dispatch(command, console, "transfer");

        //the required @Arg is missing -> CMDMethodInterpreter.invoke sends the subcommand's own
        //help line, built from the declared @Arg names, instead of running the command body
        console.assertAnyMessageContains("<plugin:section>");
        console.assertAnyMessageContains("<backend>");
    }

    @Test
    void ecStorageFirstArgTabHidesTransferFromAConsoleWithoutItsPermission() {
        FinalCMDPluginCommand command = newHarness().register(new CMDECStorage());

        TestCommandSender consoleWithoutTransfer = new TestCommandSender("console")
                .grant(PermissionNodes.EVERNIFECORE_COMMAND_STORAGE_STATUS);
        assertEquals(List.of("status"), harness.tab(command, consoleWithoutTransfer, ""));

        TestCommandSender consoleWithBoth = new TestCommandSender("console")
                .grant(PermissionNodes.EVERNIFECORE_COMMAND_STORAGE_STATUS)
                .grant(PermissionNodes.EVERNIFECORE_COMMAND_STORAGE_TRANSFER);
        assertEquals(List.of("status", "transfer"), harness.tab(command, consoleWithBoth, ""));
    }
}
