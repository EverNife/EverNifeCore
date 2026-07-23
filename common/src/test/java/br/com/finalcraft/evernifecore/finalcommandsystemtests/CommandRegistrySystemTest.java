package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.IECPluginBootstrap;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the central command registry (matrix RG): every command registered through
 * {@link FinalCMDManager} is tracked on its owning {@link ECPluginData}, is individually and
 * bulk-unregisterable, survives a reload without duplicating, and is gated by the central
 * {@code commands.yml} (enabled flag + aliases override).
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
    void rg1_registeredCommandIsTrackedAndFoundByAnyLabelCaseInsensitive() {
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
    void rg1_getRegisteredCommandsIsACopyExternalMutationDoesNotLeak() {
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
    void rg2_unregisterTellsThePlatformRemovesFromTheListAndIsIdempotent() {
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
    void rg3_unregisterAllCommandsClearsTheWholeList() {
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
    void rg4_reregisteringSamePrimaryLabelReplacesInsteadOfDuplicating() {
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
    void rg5_multiFinalCMDClassRegistersAndTracksEveryCommand() {
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
    void rg6_defaultShutdownPreUnregistersCommandsAndListeners() {
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
    // RG7 - commands.yml: first registration seeds the entry; a pre-existing 'enabled: false' skips
    // registration (with a log); a pre-existing 'aliases' override replaces the extra labels
    // ------------------------------------------------------------------

    public static class RG7_Cmd {
        @FinalCMD(aliases = {"rg7cmd", "rg7alias"})
        public void run(FCommandSender sender) {}
    }

    @Test
    void rg7_commandsYamlSeedsThenHonorsEnabledFalseAndAliasesOverride() {
        FinalCmdTestHarness h = newHarness();
        String pluginName = h.ecPluginData.getMetaInfo().getName();
        String path = "Commands." + pluginName + ".rg7cmd";

        //first registration: the entry does not exist yet - it gets seeded from the annotation
        FinalCMDPluginCommand first = h.register(new RG7_Cmd());
        assertNotNull(first);

        Config commandsConfig = ConfigFactory.open(h.ecPluginData, "commands.yml");
        assertTrue(commandsConfig.getBoolean(path + ".enabled", false), "a fresh entry must seed enabled=true");
        assertEquals(Collections.singletonList("rg7alias"), commandsConfig.getStringList(path + ".aliases", Collections.emptyList()));

        //an admin disables the command directly in commands.yml
        commandsConfig.setValue(path + ".enabled", false);
        commandsConfig.save();
        h.platform.reset();

        boolean secondRegistered = h.registerExpectingFailure(new RG7_Cmd());

        assertFalse(secondRegistered, "registerCommand() must report failure when commands.yml disables the command");
        assertNull(h.platform.getCaptured("rg7cmd"), "the platform must never receive a register call for a disabled command");
        assertTrue(
                h.platform.getInfoMessages().stream().anyMatch(m -> m.contains("rg7cmd") && m.contains("disabled by commands.yml")),
                "a disabled command must log why it was skipped"
        );

        //an admin re-enables it and overrides its extra labels
        commandsConfig = ConfigFactory.open(h.ecPluginData, "commands.yml");
        commandsConfig.setValue(path + ".enabled", true);
        commandsConfig.setValue(path + ".aliases", Collections.singletonList("rg7renamed"));
        commandsConfig.save();
        h.platform.reset();

        FinalCMDPluginCommand third = h.register(new RG7_Cmd());

        assertNotNull(third, "registration must succeed again once re-enabled");
        assertEquals("rg7cmd", third.getPrimaryLabel(), "the primary label is never overridden by commands.yml");
        assertArrayEquals(new String[]{"rg7renamed"}, third.getExtraLabels(), "the aliases override replaces the annotation's extra labels");
        assertNotNull(h.platform.getCaptured("rg7renamed"), "the platform must see the overridden alias, not the annotation's original one");
    }

    // ------------------------------------------------------------------
    // RG8 - a platform-level registration failure keeps the command out of the tracked list
    // ------------------------------------------------------------------

    public static class RG8_Cmd {
        @FinalCMD(aliases = "rg8cmd")
        public void run(FCommandSender sender) {}
    }

    @Test
    void rg8_platformRegistrationFailureKeepsTheCommandOutOfTheList() {
        FinalCmdTestHarness h = newHarness();
        h.platform.setForceRegisterFailure(true);

        FinalCMDPluginCommand command = h.register(new RG8_Cmd());

        assertNull(command, "register() must report no command when the platform rejects registration");
        assertTrue(h.ecPluginData.getRegisteredCommands().isEmpty());
    }
}
