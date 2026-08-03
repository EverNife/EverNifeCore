package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.misc.CMDECAccount;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command is available on a plain boot. It used to answer "enable it in storage.yml" for a flag
 * that never gated anything a player could see, so the one refusal left has to be the honest one:
 * the storage boot has not finished, or it failed.
 */
@ECoreTest
class CMDECAccountAvailabilityTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
        PlayerDataWorld.tearDown();
    }

    @Test
    void linkWorksWithNothingButTheNetworkBackendInTheYml() throws IOException {
        harness = new FinalCmdTestHarness("AccountAvailable", tempDir);
        FinalCMDPluginCommand command = harness.register(new CMDECAccount());
        PlayerController.initialize(Storages.h2("cmd_account_link").writeTo(tempDir));

        UUID owner = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        PlayerController.handleLogin(owner, "Owner").join();
        PlayerController.handleLogin(alt, "Alt").join();

        TestFPlayerSender admin = new TestFPlayerSender("Admin", UUID.randomUUID())
                .grant(PermissionNodes.EVERNIFECORE_COMMAND_ACCOUNT)
                .grant(PermissionNodes.EVERNIFECORE_COMMAND_ACCOUNT_LINK);
        harness.dispatch(command, admin, "link Owner Alt");

        //the readiness guard is the synchronous part, so this is what dispatch can be held to; the
        //link's own outcome lands on a later callback and is pinned by NetworkBlockBootTest instead
        assertFalse(admin.anyMessageContains("storage.yml"),
                "there is no setting left to send the admin to: " + admin.getMessages());
        assertFalse(admin.anyMessageContains("not ready"),
                "a booted controller means a live account layer: " + admin.getMessages());
        assertFalse(admin.anyMessageContains("permission"),
                "the granted nodes must be the only ones the subcommand asks for: " + admin.getMessages());
    }

    @Test
    void withoutABootedControllerItSaysSoInsteadOfBlamingASetting() throws IOException {
        harness = new FinalCmdTestHarness("AccountNotReady", tempDir);
        FinalCMDPluginCommand command = harness.register(new CMDECAccount());

        //no PlayerController.initialize: the only state that can still refuse the command
        TestFPlayerSender admin = new TestFPlayerSender("Admin", UUID.randomUUID())
                .grant(PermissionNodes.EVERNIFECORE_COMMAND_ACCOUNT);
        harness.dispatch(command, admin, "info Someone");

        assertTrue(admin.anyMessageContains("not ready"), admin.getMessages().toString());
    }
}
