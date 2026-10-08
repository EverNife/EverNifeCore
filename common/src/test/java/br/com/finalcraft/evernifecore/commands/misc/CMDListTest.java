package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The core's player list answers to {@code /playerlist} always, and to {@code /list} unless the config turns that off. */
class CMDListTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("CMDList", tempDir);
    }

    @AfterEach
    void teardown() {
        ECSettings.COMMAND_LIST_ALIAS = true;
        if (harness != null) harness.close();
    }

    @Test
    void byDefaultTheCoreAnswersList() {
        FinalCMDPluginCommand command = harness.register(CMDList.class);

        assertEquals("playerlist", command.getPrimaryLabel());
        assertArrayEquals(new String[]{"list"}, command.getExtraLabels());
    }

    @Test
    void theConfigCanLeaveTheServersOwnListAlone() {
        ECSettings.COMMAND_LIST_ALIAS = false;

        FinalCMDPluginCommand command = harness.register(CMDList.class);

        assertEquals("playerlist", command.getPrimaryLabel());
        assertArrayEquals(new String[0], command.getExtraLabels(), "no /list once the config turns it off");
    }
}
