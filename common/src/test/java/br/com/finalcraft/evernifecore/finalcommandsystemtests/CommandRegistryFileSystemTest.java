package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.ReferenceCommandTree;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code plugins/EverNifeCore/commands/<PluginName>.yml} may do to a command tree: nothing at all
 * while the feature is off, and - once on - seed every path, prune a branch, or rename its aliases.
 * <p>
 * Everything is asserted over the engine's own reference tree, so what is pinned here is the behaviour
 * a plugin author would get, not a fixture shaped to make the file look good.
 */
class CommandRegistryFileSystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;
    private boolean previousFeatureState;
    private boolean previousDebugModuleState;

    @AfterEach
    void teardown() {
        ECSettings.COMMAND_REGISTRY_FILES_ENABLED = previousFeatureState;
        ECDebugModule.COMMAND_REGISTRY.setEnabled(previousDebugModuleState);
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness(boolean featureEnabled) {
        previousFeatureState = ECSettings.COMMAND_REGISTRY_FILES_ENABLED;
        previousDebugModuleState = ECDebugModule.COMMAND_REGISTRY.isEnabled();
        ECSettings.COMMAND_REGISTRY_FILES_ENABLED = featureEnabled;
        harness = Commands.harness("CommandRegistryFile", tempDir);
        return harness;
    }

    private TestCommandSender admin() {
        return new TestCommandSender("admin")
                .grant(ReferenceCommandTree.PERMISSION)
                .grant(ReferenceCommandTree.USER_PERMISSION);
    }

    private Path registryFile() {
        return tempDir.resolve("commands").resolve(harness.pluginName + ".yml");
    }

    /** Writes an admin's edit straight into the registry file, as if they had opened it in an editor. */
    private void writeEntry(String path, Object value) {
        Config config = ConfigFactory.open(registryFile());
        config.setValue(path, value);
        config.save();
    }

    private Config readRegistryFile() {
        return ConfigFactory.open(registryFile());
    }

    private static byte[] bytesOf(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String textOf(Path file) {
        return new String(bytesOf(file), StandardCharsets.UTF_8);
    }

    private List<String> summaryLines() {
        return harness.platform.getInfoMessages().stream()
                .filter(line -> line.startsWith("Command registry: "))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<String> detailLines() {
        return harness.platform.getInfoMessages().stream()
                .filter(line -> line.contains("[Debug (" + ECDebugModule.COMMAND_REGISTRY.getName() + ") ]"))
                .collect(java.util.stream.Collectors.toList());
    }

    // ------------------------------------------------------------------
    // The feature off: no file, no gate
    // ------------------------------------------------------------------

    @Test
    void withTheFeatureOffNoRegistryFolderIsEverCreated() {
        FinalCmdTestHarness h = newHarness(false);

        FinalCMDPluginCommand command = h.register(Commands.referenceTree());

        assertNotNull(command);
        assertNotNull(h.platform.getCaptured("reftree"), "the annotation alone must register the command");
        assertFalse(Files.exists(tempDir.resolve("commands")), "nothing may be written while the feature is off");
    }

    @Test
    void withTheFeatureOffAPreExistingFileChangesNothingAndIsNotTouched() {
        FinalCmdTestHarness h = newHarness(false);
        //what an admin would have left behind after turning the feature off again
        writeEntry("commands.reftree.enabled", false);
        writeEntry("commands.reftree.nodes.user.enabled", false);
        byte[] before = bytesOf(registryFile());

        FinalCMDPluginCommand command = h.register(Commands.referenceTree());

        assertNotNull(command, "a file nobody reads cannot disable a command");
        h.tree(command).at("").hasChildren("ping", "user");
        assertArrayEquals(before, bytesOf(registryFile()), "the file must not even be rewritten");
    }

    // ------------------------------------------------------------------
    // The feature on: the whole tree is seeded, nested
    // ------------------------------------------------------------------

    @Test
    void theWholeTreeIsSeededIncludingLeavesAndNestedNodes() {
        FinalCmdTestHarness h = newHarness(true);

        h.register(Commands.referenceTree());

        Config file = readRegistryFile();
        assertTrue(file.getBoolean("commands.reftree.enabled", false), "the root is seeded");
        assertTrue(file.getBoolean("commands.reftree.nodes.ping.enabled", false), "a leaf is seeded too");
        assertEquals(Collections.singletonList("u"),
                file.getStringList("commands.reftree.nodes.user.aliases", Collections.emptyList()),
                "a node seeds the aliases its annotation declares");
        assertTrue(file.getBoolean("commands.reftree.nodes.user.nodes.server.nodes.show.enabled", false),
                "four levels down is still seeded");
        assertTrue(textOf(registryFile()).contains("nodes:"), "the file nests exactly like the tree");
    }

    // ------------------------------------------------------------------
    // Pruning
    // ------------------------------------------------------------------

    @Test
    void aDisabledNodeLosesItsWholeSubtreeInDispatchTabAndHelp() {
        FinalCmdTestHarness h = newHarness(true);
        writeEntry("commands.reftree.nodes.user.nodes.server.enabled", false);
        ReferenceCommandTree tree = Commands.referenceTree();
        TestCommandSender admin = admin();

        FinalCMDPluginCommand command = h.register(tree);

        h.tree(command).at("user").hasChildren("info");

        h.dispatch(command, admin, "user Steve server survival world show");
        assertTrue(tree.calls().stream().noneMatch(call -> call.startsWith("user.server")),
                "a pruned branch must not run, but it did: " + tree.calls());

        assertEquals(Collections.singletonList("info"), h.tab(command, admin, "user", "Steve", ""),
                "a pruned branch must not be offered");

        admin.clearMessages();
        h.dispatch(command, admin, "user Steve help");
        assertTrue(admin.anyMessageContains("info"), "the surviving child is still in the help");
        assertFalse(admin.anyMessageContains("server"), "a pruned branch must not have a help line");
    }

    @Test
    void aDisabledRootNeverReachesThePlatform() {
        FinalCmdTestHarness h = newHarness(true);
        writeEntry("commands.reftree.enabled", false);

        FinalCMDPluginCommand command = h.register(Commands.referenceTree());

        assertNull(command, "registration must report failure for a disabled command");
        assertNull(h.platform.getCaptured("reftree"), "the platform must never see it");
        assertTrue(h.ecPluginData.getRegisteredCommands().isEmpty());
    }

    @Test
    void aDisabledParentWinsOverAnExplicitlyEnabledChild() {
        FinalCmdTestHarness h = newHarness(true);
        h.ecPluginData.setDebugEnabled(true);
        ECDebugModule.COMMAND_REGISTRY.setEnabled(true);
        writeEntry("commands.reftree.nodes.user.nodes.server.enabled", false);
        writeEntry("commands.reftree.nodes.user.nodes.server.nodes.show.enabled", true);

        FinalCMDPluginCommand command = h.register(Commands.referenceTree());

        h.tree(command).at("user").hasChildren("info");
        assertTrue(detailLines().stream().anyMatch(line -> line.contains("show") && line.contains("the parent wins")),
                "the ignored child entry has to say why it was ignored: " + detailLines());
    }

    // ------------------------------------------------------------------
    // Aliases
    // ------------------------------------------------------------------

    @Test
    void anAliasOverrideOnANodeReplacesTheAnnotatedOneAndKeepsThePrimaryLabel() {
        FinalCmdTestHarness h = newHarness(true);
        writeEntry("commands.reftree.nodes.user.nodes.server.aliases", Collections.singletonList("box"));
        ReferenceCommandTree tree = Commands.referenceTree();
        TestCommandSender admin = admin();

        FinalCMDPluginCommand command = h.register(tree);

        assertEquals("server", h.tree(command).at("user.server").node().getPrimaryLabel(),
                "the primary label is the entry's identity and is never overridden");

        h.dispatch(command, admin, "user Steve box survival world show");
        assertEquals("user.server.show(Steve, survival/world, world)", tree.lastCall(), "the new alias must answer");

        tree.clearCalls();
        h.dispatch(command, admin, "user Steve sv survival world show");
        assertTrue(tree.calls().stream().noneMatch(call -> call.startsWith("user.server")),
                "the annotated alias was replaced, so it must not answer any more: " + tree.calls());

        tree.clearCalls();
        h.dispatch(command, admin, "user Steve server survival world show");
        assertEquals("user.server.show(Steve, survival/world, world)", tree.lastCall(),
                "the primary label always answers");
    }

    // ------------------------------------------------------------------
    // The two logging levels
    // ------------------------------------------------------------------

    @Test
    void aFileThatChangesNothingSaysNothing() {
        FinalCmdTestHarness h = newHarness(true);

        h.register(Commands.referenceTree());

        assertTrue(summaryLines().isEmpty(), "a file with no override at all must not log a summary: " + summaryLines());
    }

    @Test
    void oneOverrideLogsOneSummaryAndKeepsTheDetailBehindTheDebugModule() {
        FinalCmdTestHarness h = newHarness(true);
        writeEntry("commands.reftree.nodes.ping.enabled", false);
        h.platform.reset();

        h.register(Commands.referenceTree());

        assertEquals(1, summaryLines().size(), "exactly one summary line: " + summaryLines());
        assertTrue(summaryLines().get(0).contains(ECDebugModule.COMMAND_REGISTRY.getName()),
                "the summary has to name the flag that shows the detail");
        assertTrue(detailLines().isEmpty(), "no detail may leak with debug off: " + detailLines());
    }

    @Test
    void theDetailLinesNameEveryPathRemovedOnceTheDebugModuleIsOn() {
        FinalCmdTestHarness h = newHarness(true);
        h.ecPluginData.setDebugEnabled(true);
        ECDebugModule.COMMAND_REGISTRY.setEnabled(true);
        writeEntry("commands.reftree.nodes.ping.enabled", false);
        writeEntry("commands.reftree.nodes.user.aliases", Collections.singletonList("usr"));
        h.platform.reset();

        h.register(Commands.referenceTree());

        assertEquals(1, summaryLines().size(), "the summary stays a single line however many changes there were");
        assertTrue(detailLines().stream().anyMatch(line -> line.contains("/reftree ping") && line.contains("Removed")),
                "the removed path has to be named: " + detailLines());
        assertTrue(detailLines().stream().anyMatch(line -> line.contains("/reftree user") && line.contains("usr")),
                "the overridden alias has to be named: " + detailLines());
    }

    // ------------------------------------------------------------------
    // The legacy file
    // ------------------------------------------------------------------

    @Test
    void theOldCommandsYamlIsNeitherReadNorTouchedAndSaysSoOnce() {
        FinalCmdTestHarness h = newHarness(true);
        Config legacy = ConfigFactory.open(tempDir.resolve("commands.yml"));
        legacy.setValue("Commands." + h.pluginName + ".reftree.enabled", false);
        legacy.save();
        byte[] before = bytesOf(tempDir.resolve("commands.yml"));

        FinalCMDPluginCommand command = h.register(Commands.referenceTree());

        assertNotNull(command, "the old file is not read, so its 'enabled: false' cannot disable anything");
        assertArrayEquals(before, bytesOf(tempDir.resolve("commands.yml")), "the old file must be left untouched");

        List<String> notices = h.platform.getInfoMessages().stream()
                .filter(line -> line.contains("commands.yml") && line.contains("no longer read"))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(1, notices.size(), "exactly one line about the old file: " + notices);
    }

    // ------------------------------------------------------------------
    // Reading the outcome back
    // ------------------------------------------------------------------

    @Test
    void isCommandPathEnabledAnswersForAPrunedPathAnActiveOneAndAFeatureThatIsOff() {
        FinalCmdTestHarness h = newHarness(true);
        writeEntry("commands.reftree.nodes.user.nodes.server.enabled", false);

        h.register(Commands.referenceTree());

        assertFalse(h.ecPluginData.isCommandPathEnabled(pathOf("user", "server")), "the pruned path is gone");
        assertTrue(h.ecPluginData.isCommandPathEnabled(pathOf("user", "info")), "the surviving path is active");

        ECSettings.COMMAND_REGISTRY_FILES_ENABLED = false;
        assertTrue(h.ecPluginData.isCommandPathEnabled(pathOf("user", "server")),
                "with the feature off nothing was ever pruned, so everything answers true");
    }

    private static CommandPath pathOf(String... literals) {
        List<String> segments = Arrays.asList(literals);
        return new CommandPath("reftree", segments, segments, segments.size() - 1);
    }
}
