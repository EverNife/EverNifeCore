package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.misc.CMDECCooldown;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The framework eating its own cooking: {@link CMDECCooldown}'s former local/network 2x2 subcommand matrix collapsed into
 * {@code set}/{@code setplayer} plus a {@code --network} {@code @Arg.Flag}. Pins the registered shape
 * (the two now-deleted labels gone) and both the local and network dispatch paths, up to what the
 * headless harness can reach.
 */
@ECoreTest
class CMDECCooldownDogfoodTest {

    //Must run before ANY PlayerController.initialize() call in this class: ConfigFactory's static
    //init (triggered by PlayerController's constructor) reads EverNifeCore.getPlatform() exactly
    //once per JVM, and a class-init failure sticks (NoClassDefFoundError) for every later test.

    private static final AtomicInteger UNIQUE_SUFFIX = new AtomicInteger();

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
        PlayerDataWorld.tearDown();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("Dogfood", tempDir);
        return harness;
    }

    //The full key of the harness plugin's cooldown id, as /eccooldown names it.
    private String key(String id) {
        return harness.ecPluginData.getMetaInfo().getName() + "#" + id;
    }

    private static TestCommandSender consoleWithPermission() {
        return new TestCommandSender("console").grant(PermissionNodes.EVERNIFECORE_COMMAND_COOLDOWN);
    }

    /**
     * {@code set}'s confirmation formats the remaining time through {@link FCTimeFrame}, whose own
     * {@code @FCLocale} static fields FinalCmdTestHarness deliberately never touches, staying command-only
     * (see its own javadoc): they are loaded here the same way the real {@code ConfigManager.initialize} does.
     */
    private FinalCMDPluginCommand registerCooldownCommand() {
        FinalCMDPluginCommand command = newHarness().register(new CMDECCooldown());
        FCLocaleManager.loadLocale(harness.ecPluginData, FCTimeFrame.class);
        return command;
    }

    // ------------------------------------------------------------------
    // Shape: the 2x2 matrix collapsed, the deleted labels are truly gone
    // ------------------------------------------------------------------

    @Test
    void theCollapsedMatrixKeepsOnlySetAndSetplayer() {
        FinalCMDPluginCommand command = newHarness().register(new CMDECCooldown());

        //an exact-set comparison of every "set*"-prefixed label proves BOTH survivors exist AND
        //nothing else does (in particular, neither of the two now-deleted "*network" labels) without
        //spelling the deleted names out anywhere in this file
        Set<String> setPrefixedLabels = command.getRoot().getChildren().stream()
                .map(sub -> sub.getPrimaryLabel())
                .filter(label -> label.startsWith("set"))
                .collect(Collectors.toSet());

        assertEquals(Set.of("set", "setplayer"), setPrefixedLabels);
    }

    // ------------------------------------------------------------------
    // set / set --network - server-owned cooldown, no PlayerController needed at all: Cooldown.network()
    // gracefully collapses to the local map when no network storage is bootstrapped (headless default)
    // ------------------------------------------------------------------

    @Test
    void setWithoutTheFlagStartsALocalServerCooldown() {
        FinalCMDPluginCommand command = registerCooldownCommand();
        TestCommandSender sender = consoleWithPermission();
        String cooldownId = "dogfood_local_" + UNIQUE_SUFFIX.incrementAndGet();

        harness.dispatch(command, sender, "set " + key(cooldownId) + " 300");

        sender.assertAnyMessageContains("SERVER");
        sender.assertAnyMessageContains("LOCAL");
        assertTrue(Cooldown.of(harness.ecPluginData, cooldownId).isInCooldown(), "set must have actually started the cooldown");
    }

    @Test
    void setWithTheNetworkFlagReportsTheNetworkReach() {
        FinalCMDPluginCommand command = registerCooldownCommand();
        TestCommandSender sender = consoleWithPermission();
        String cooldownId = "dogfood_net_" + UNIQUE_SUFFIX.incrementAndGet();

        harness.dispatch(command, sender, "set " + key(cooldownId) + " 300 --network");

        sender.assertAnyMessageContains("SERVER");
        sender.assertAnyMessageContains("NETWORK");
        assertTrue(Cooldown.network(harness.ecPluginData, cooldownId).isInCooldown());
    }

    @Test
    void setTheNetworkFlagsShortAliasWorksTheSameAsTheLongName() {
        FinalCMDPluginCommand command = registerCooldownCommand();
        TestCommandSender sender = consoleWithPermission();
        String cooldownId = "dogfood_alias_" + UNIQUE_SUFFIX.incrementAndGet();

        harness.dispatch(command, sender, "set " + key(cooldownId) + " 300 -n");

        sender.assertAnyMessageContains("NETWORK");
    }

    @Test
    void aCooldownNamedWithoutALoadedPluginIsRefusedWithTheFormToUse() {
        FinalCMDPluginCommand command = registerCooldownCommand();
        TestCommandSender sender = consoleWithPermission();

        harness.dispatch(command, sender, "set daily 300");
        sender.assertAnyMessageContains("<Plugin>#<id>");
        assertFalse(sender.anyMessageContains("LOCAL"), "a bare id starts nothing");

        TestCommandSender other = consoleWithPermission();
        harness.dispatch(command, other, "set NoSuchPlugin#daily 300");
        other.assertAnyMessageContains(key(""));
    }

    @Test
    void setAnInvalidDurationSendsTheInvalidDurationMessageAndNeverStartsTheCooldown() {
        FinalCMDPluginCommand command = newHarness().register(new CMDECCooldown());
        TestCommandSender sender = consoleWithPermission();
        String cooldownId = "dogfood_invalid_" + UNIQUE_SUFFIX.incrementAndGet();

        harness.dispatch(command, sender, "set " + key(cooldownId) + " notADuration");

        sender.assertAnyMessageContains("Invalid duration");
        assertFalse(Cooldown.of(harness.ecPluginData, cooldownId).isInCooldown());
    }

    // ------------------------------------------------------------------
    // setplayer / setplayer --network - the target's own PlayerData: needs a real (headless, H2
    // in-memory) PlayerController backend, mirroring CooldownSectionsTest's fixture
    // ------------------------------------------------------------------

    @Test
    void setplayerWithoutTheFlagStartsTheTargetsLocalCooldown() throws IOException {
        //the harness must exist BEFORE PlayerController.initialize(): it is what points
        //EverNifeCore.getEcPluginData() at a real (fake-but-valid) plugin, which the builtin
        //cooldown-section registration reads from
        FinalCMDPluginCommand command = registerCooldownCommand();
        PlayerController.initialize(writeStorageYml("dogfood_setplayer_local"));
        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Vip").join();

        TestCommandSender sender = consoleWithPermission();
        String cooldownId = "kit";

        harness.dispatch(command, sender, "setplayer Vip " + key(cooldownId) + " 300");
        waitUntil(() -> sender.anyMessageContains("PLAYER"));

        sender.assertAnyMessageContains("LOCAL");
        PlayerData playerData = PlayerController.getLoaded(uuid);
        assertTrue(playerData.getCooldown(harness.ecPluginData, cooldownId).join().isInCooldown());
    }

    @Test
    void setplayerWithTheNetworkFlagStartsTheTargetsNetworkCooldown() throws IOException {
        FinalCMDPluginCommand command = registerCooldownCommand();
        PlayerController.initialize(writeStorageYml("dogfood_setplayer_network"));
        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Vip").join();

        TestCommandSender sender = consoleWithPermission();
        String cooldownId = "vip";

        harness.dispatch(command, sender, "setplayer Vip " + key(cooldownId) + " 300 --network");
        waitUntil(() -> sender.anyMessageContains("PLAYER"));

        sender.assertAnyMessageContains("NETWORK");
        PlayerData playerData = PlayerController.getLoaded(uuid);
        assertTrue(playerData.getNetworkCooldown(harness.ecPluginData, cooldownId).join().isInCooldown());
    }

    @Test
    void setplayerWithAnUnknownPlayerSendsPlayerNotFoundAndNeverDispatches() throws IOException {
        FinalCMDPluginCommand command = newHarness().register(new CMDECCooldown());
        PlayerController.initialize(writeStorageYml("dogfood_setplayer_unknown"));
        TestCommandSender sender = consoleWithPermission();

        harness.dispatch(command, sender, "setplayer GhostPlayer " + key("kit") + " 300");

        //synchronous: an unresolved <player> aborts inside the @Arg parser itself, before any
        //PlayerController-async branch is ever reached - no waitUntil needed here
        sender.assertAnyMessageContains("GhostPlayer");
        assertFalse(sender.anyMessageContains("PLAYER"), "the set confirmation must never be sent");
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    /**
     * {@code getCooldown}/{@code getNetworkCooldown} resolve through PlayerController's caching layer,
     * which is not guaranteed to complete synchronously with {@code harness.dispatch} (a fire-and-forget
     * call): poll a bounded number of times instead of a single blind sleep, and fail loudly if the
     * condition never becomes true - a stuck condition is a real bug, not something to silently pass.
     */
    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 2000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Condition not met within 2s");
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for the async dispatch to complete");
            }
        }
    }

    private File writeStorageYml(String dbName) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "network:",
                "  storage-backend-id: test_h2",
                "");
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
