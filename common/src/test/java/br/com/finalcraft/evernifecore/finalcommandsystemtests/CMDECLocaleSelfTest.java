package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.misc.CMDECLocale;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.TestFPlayerSender;
import br.com.finalcraft.evernifecore.locale.LocalePDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins '/eclocale self' (C17 command side): while PER_PLAYER_LOCALE is off the subcommand is hidden
 * and inert; while it is on, it stores the executor's chosen (normalized) language.
 */
class CMDECLocaleSelfTest {

    @BeforeAll
    static void installTestPlatform() {
        TestPlatformFixture.ensureInstalled();
    }

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;
    private boolean originalPerPlayerLocale;

    @BeforeEach
    void rememberSetting() {
        originalPerPlayerLocale = ECSettings.PER_PLAYER_LOCALE;
    }

    @AfterEach
    void teardown() {
        ECSettings.PER_PLAYER_LOCALE = originalPerPlayerLocale;
        if (harness != null) harness.close();
        PlayerController.shutdown();
        PlayerController.getConfiguredPDSections().clear();
        PlayerController.getConfiguredAccountSections().clear();
        EntitySchemaMigrations.clear();
    }

    @Test
    void settingOffHidesSelfFromTabAndDoesNothingWhenInvoked() throws IOException {
        ECSettings.PER_PLAYER_LOCALE = false;
        harness = new FinalCmdTestHarness("LocaleSelfOff", tempDir);
        FinalCMDPluginCommand command = harness.register(new CMDECLocale());
        PlayerController.initialize(writeH2StorageYml("locale_self_off"));

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Alice").join();
        //granted the SELF node, so only the validation gate can be what hides it
        TestFPlayerSender player = new TestFPlayerSender("Alice", uuid)
                .grant(PermissionNodes.EVERNIFECORE_COMMAND_FCLOCALE_SELF);

        assertFalse(harness.tab(command, player, "").contains("self"),
                "off: 'self' must not appear in tab completion");

        harness.dispatch(command, player, "self pt_br");
        assertFalse(player.anyMessageContains("language has been set"),
                "off: invoking 'self' directly must produce no effect");
    }

    @Test
    void settingOnStoresTheExecutorsNormalizedLanguage() throws IOException {
        ECSettings.PER_PLAYER_LOCALE = true;
        harness = new FinalCmdTestHarness("LocaleSelfOn", tempDir);
        FinalCMDPluginCommand command = harness.register(new CMDECLocale());
        PlayerController.initialize(writeH2StorageYml("locale_self_on"));

        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Alice").join();
        TestFPlayerSender player = new TestFPlayerSender("Alice", uuid)
                .grant(PermissionNodes.EVERNIFECORE_COMMAND_FCLOCALE_SELF);

        assertTrue(harness.tab(command, player, "").contains("self"),
                "on: 'self' must appear in tab completion");

        harness.dispatch(command, player, "self pt_br");

        LocalePDSection section = PlayerController.getLoadedSection(uuid, LocalePDSection.class);
        assertNotNull(section, "on: the executor's LocalePDSection must be loaded");
        assertEquals("PT_BR", section.getLang(),
                "on: 'self pt_br' must store the normalized language");
    }

    private File writeH2StorageYml(String dbName) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "");
        File file = tempDir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
