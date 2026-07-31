package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.IECPluginBootstrap;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the command bookkeeping: every command registered through {@link FinalCMDManager} is tracked
 * on its owning {@link ECPluginData}, is individually and bulk-unregisterable, and survives a reload
 * without duplicating. What the per-plugin registry file may change about it lives in
 * {@code CommandRegistryFileSystemTest}.
 */
class CommandRegistrySystemTest {

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
        harness = new FinalCmdTestHarness("CommandRegistry", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // RG1 - registration tracks the command on its owning plugin; findRegisteredCommand finds it
    // by primary label or alias, case-insensitive
    // ------------------------------------------------------------------

    public static class RG1_Cmd {
        @FinalCMD(aliases = {"rg1cmd", "rg1alias"})
        public void run(FCommandSender sender) {}
    }

    @Test
    void registeredCommandIsTrackedAndFoundByAnyLabelCaseInsensitive() {
        FinalCmdTestHarness h = newHarness();
        FinalCMDPluginCommand command = h.register(new RG1_Cmd());

        assertNotNull(command);
        assertTrue(h.ecPluginData.getRegisteredCommands().contains(command));
        assertSame(command, h.ecPluginData.findRegisteredCommand("rg1cmd").orElse(null));
        assertSame(command, h.ecPluginData.findRegisteredCommand("RG1CMD").orElse(null));
        assertSame(command, h.ecPluginData.findRegisteredCommand("rg1alias").orElse(null));
        assertSame(command, h.ecPluginData.findRegisteredCommand("RG1ALIAS").orElse(null));
        assertFalse(h.ecPluginData.findRegisteredCommand("notregistered").isPresent());
    }

    @Test
    void getRegisteredCommandsIsACopyExternalMutationDoesNotLeak() {
        FinalCmdTestHarness h = newHarness();
        h.register(new RG1_Cmd());

        List<FinalCMDPluginCommand> snapshot = h.ecPluginData.getRegisteredCommands();
        assertThrowsOnMutation(snapshot);
        assertEquals(1, h.ecPluginData.getRegisteredCommands().size(), "mutating one snapshot must not affect the plugin's tracked state");
    }

    private static void assertThrowsOnMutation(List<FinalCMDPluginCommand> snapshot) {
        try {
            snapshot.clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("getRegisteredCommands() must return an immutable view");
    }

    // ------------------------------------------------------------------
    // RG2 - unregister() tells the platform (primary + aliases), removes from the list, and is
    // idempotent
    // ------------------------------------------------------------------

    public static class RG2_Cmd {
        @FinalCMD(aliases = {"rg2cmd", "rg2alias"})
        public void run(FCommandSender sender) {}
    }

    @Test
    void unregisterTellsThePlatformRemovesFromTheListAndIsIdempotent() {
        FinalCmdTestHarness h = newHarness();
        FinalCMDPluginCommand command = h.register(new RG2_Cmd());
        assertNotNull(command);

        command.unregister();

        assertTrue(h.platform.getUnregisteredLabels().contains("rg2cmd"));
        assertTrue(h.platform.getUnregisteredLabels().contains("rg2alias"));
        assertFalse(h.ecPluginData.getRegisteredCommands().contains(command));
        assertFalse(h.ecPluginData.findRegisteredCommand("rg2cmd").isPresent());

        assertDoesNotThrow(command::unregister, "a second unregister() must be a no-op, not throw");
        assertFalse(h.ecPluginData.getRegisteredCommands().contains(command));
    }

    // ------------------------------------------------------------------
    // RG3 - unregisterAllCommands empties the owning plugin's whole list
    // ------------------------------------------------------------------

    public static class RG3_CmdOne {
        @FinalCMD(aliases = "rg3one")
        public void run(FCommandSender sender) {}
    }

    public static class RG3_CmdTwo {
        @FinalCMD(aliases = "rg3two")
        public void run(FCommandSender sender) {}
    }

    @Test
    void unregisterAllCommandsClearsTheWholeList() {
        FinalCmdTestHarness h = newHarness();
        h.register(new RG3_CmdOne());
        h.register(new RG3_CmdTwo());
        assertEquals(2, h.ecPluginData.getRegisteredCommands().size());

        FinalCMDManager.unregisterAllCommands(h.ecPluginData);

        assertTrue(h.ecPluginData.getRegisteredCommands().isEmpty());
        assertTrue(h.platform.getUnregisteredLabels().contains("rg3one"));
        assertTrue(h.platform.getUnregisteredLabels().contains("rg3two"));
    }

    // ------------------------------------------------------------------
    // RG4 - re-registering the same primary label (reload) replaces instead of duplicating
    // ------------------------------------------------------------------

    public static class RG4_Cmd {
        @FinalCMD(aliases = "rg4cmd")
        public void run(FCommandSender sender) {}
    }

    @Test
    void reregisteringSamePrimaryLabelReplacesInsteadOfDuplicating() {
        FinalCmdTestHarness h = newHarness();
        h.register(new RG4_Cmd());
        FinalCMDPluginCommand second = h.register(new RG4_Cmd()); //simulates a reload re-registering the same command

        assertEquals(1, h.ecPluginData.getRegisteredCommands().size(), "a re-registration must replace, not duplicate");
        assertSame(second, h.ecPluginData.getRegisteredCommands().get(0));
    }

    // ------------------------------------------------------------------
    // RG5 - a class with several independent @FinalCMD methods registers (and tracks) all of them
    // ------------------------------------------------------------------

    public static class RG5_MultiCmd {
        @FinalCMD(aliases = "rg5one")
        public void one(FCommandSender sender) {}

        @FinalCMD(aliases = "rg5two")
        public void two(FCommandSender sender) {}
    }

    @Test
    void multiFinalCMDClassRegistersAndTracksEveryCommand() {
        FinalCmdTestHarness h = newHarness();
        List<FinalCMDPluginCommand> registered = h.registerAll(new RG5_MultiCmd());

        assertEquals(2, registered.size());
        assertEquals(2, h.ecPluginData.getRegisteredCommands().size());
        assertTrue(h.ecPluginData.getRegisteredCommands().containsAll(registered));
    }

    // ------------------------------------------------------------------
    // RG6 - the default onECPluginShutdownPre unregisters both commands and listeners
    // ------------------------------------------------------------------

    public static class RG6_Cmd {
        @FinalCMD(aliases = "rg6cmd")
        public void run(FCommandSender sender) {}
    }

    static final class FakeListener implements ECListener {
        @Override
        public boolean silentRegistration() {
            return true;
        }
    }

    static final class FakeBootstrap implements IECPluginBootstrap {
        private final ECPluginData data;

        FakeBootstrap(ECPluginData data) {
            this.data = data;
        }

        @Override
        public ECPluginData getPluginData() {
            return data;
        }

        @Override
        public void onECPluginEnable() {}

        @Override
        public void onECPluginShutdown() {}

        @Override
        public void onECPluginReload() {}
    }

    @Test
    void defaultShutdownPreUnregistersCommandsAndListeners() {
        FinalCmdTestHarness h = newHarness();
        FakeBootstrap boot = new FakeBootstrap(h.ecPluginData);
        h.register(new RG6_Cmd());
        ECListener.register(h.ecPluginData, new FakeListener());

        assertEquals(1, h.ecPluginData.getRegisteredCommands().size());
        assertEquals(1, ECListener.getRegistered(h.ecPluginData).size());

        boot.onECPluginShutdownPre();

        assertTrue(h.ecPluginData.getRegisteredCommands().isEmpty(), "commands must be unregistered by the default hook");
        assertTrue(ECListener.getRegistered(h.ecPluginData).isEmpty(), "listeners must be unregistered by the default hook");
    }

    // ------------------------------------------------------------------
    // RG8 - a platform-level registration failure keeps the command out of the tracked list
    // ------------------------------------------------------------------

    public static class RG8_Cmd {
        @FinalCMD(aliases = "rg8cmd")
        public void run(FCommandSender sender) {}
    }

    @Test
    void platformRegistrationFailureKeepsTheCommandOutOfTheList() {
        FinalCmdTestHarness h = newHarness();
        h.platform.setForceRegisterFailure(true);

        FinalCMDPluginCommand command = h.register(new RG8_Cmd());

        assertNull(command, "register() must report no command when the platform rejects registration");
        assertTrue(h.ecPluginData.getRegisteredCommands().isEmpty());
    }
}
