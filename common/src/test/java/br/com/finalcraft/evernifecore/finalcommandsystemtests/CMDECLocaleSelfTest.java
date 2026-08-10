package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.misc.CMDECLocale;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.locale.LocalePDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins '/eclocale self': while PER_PLAYER_LOCALE is off the subcommand is hidden
 * and inert; while it is on, it stores the executor's chosen (normalized) language.
 */
@ECoreTest
class CMDECLocaleSelfTest {


    @TempDirNobodyCleans
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
        PlayerDataWorld.tearDown();
    }

    @Test
    void settingOffHidesSelfFromTabAndDoesNothingWhenInvoked() throws IOException {
        ECSettings.PER_PLAYER_LOCALE = false;
        harness = new FinalCmdTestHarness("LocaleSelfOff", tempDir);
        FinalCMDPluginCommand command = harness.register(new CMDECLocale());
        PlayerController.initialize(Storages.h2("locale_self_off").writeTo(tempDir));

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
        PlayerController.initialize(Storages.h2("locale_self_on").writeTo(tempDir));

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

}
