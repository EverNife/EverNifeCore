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

/** The core's player list answers to {@code /playerlist} always, and to {@code /list} only when the config asks for it. */
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
        ECSettings.COMMAND_LIST_ALIAS = false;
        if (harness != null) harness.close();
    }

    @Test
    void byDefaultTheServersOwnListIsLeftAlone() {
        FinalCMDPluginCommand command = harness.register(CMDList.class);

        assertEquals("playerlist", command.getPrimaryLabel());
        assertArrayEquals(new String[0], command.getExtraLabels(), "no /list unless the config asks for it");
    }

    @Test
    void theConfigCanHandListToTheCore() {
        ECSettings.COMMAND_LIST_ALIAS = true;

        FinalCMDPluginCommand command = harness.register(CMDList.class);

        assertEquals("playerlist", command.getPrimaryLabel());
        assertArrayEquals(new String[]{"list"}, command.getExtraLabels());
    }
}
