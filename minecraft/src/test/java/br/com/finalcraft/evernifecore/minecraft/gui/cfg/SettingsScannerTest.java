package br.com.finalcraft.evernifecore.minecraft.gui.cfg;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgRequirementType;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserNumber;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same parsers a command line uses, reading a config file. A value nobody can read is an error in
 * the file, so it is reported where a file error belongs - the owning plugin's log - and never as a
 * chat message aimed at whoever happens to be holding the console.
 */
class SettingsScannerTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;
    private TestCommandSender console;
    private Config config;

    @BeforeEach
    void setup() {
        harness = new FinalCmdTestHarness("SettingsScanner", tempDir);
        console = new TestCommandSender("console");
        config = ConfigFactory.open((ECPluginData) null, tempDir.resolve("settings.yml").toFile());
    }

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private static ArgInfo integerSetting(String key) {
        return ArgInfo.standalone(Integer.class, new ArgData().setName(key).setContext(""));
    }

    @Test
    void anUnreadableValueIsLoggedAndTheDefaultIsKept() {
        ArgInfo argInfo = integerSetting("gui.rows");
        ArgParser parser = new ArgParserNumber(argInfo);

        List<String> logged = Logs.capture(() -> {
            Object value = SettingsScanner.parsedOrDefault(harness.ecPluginData, console, parser, argInfo,
                    "notanumber", 3, config, "gui.rows");

            assertEquals(3, value, "a value the parser cannot read falls back to the declared default");
        });

        assertTrue(console.getMessages().isEmpty(),
                "a broken config line is not chat, and the console sender got: " + console.getMessages());
        assertTrue(anyContains(logged, "needs to be an integer"),
                "the parser's own reason belongs in the log: " + logged);
        assertTrue(anyContains(logged, "Fix your Config!"),
                "and so does which setting fell back: " + logged);
    }

    @Test
    void aValueOutOfTheDeclaredRangeIsLoggedAndTheDefaultIsKept() {
        ArgInfo argInfo = ArgInfo.standalone(Integer.class, new ArgData().setName("gui.rows").setContext("[1:5]"));
        ArgParser parser = new ArgParserNumber(argInfo);

        List<String> logged = Logs.capture(() -> {
            Object value = SettingsScanner.parsedOrDefault(harness.ecPluginData, console, parser, argInfo,
                    "9", 3, config, "gui.rows");

            assertEquals(3, value);
        });

        assertTrue(console.getMessages().isEmpty(),
                "a refusal reports the same way a miss does: " + console.getMessages());
        assertTrue(anyContains(logged, "Fix your Config!"), logged.toString());
    }

    @Test
    void aReadableValueIsParsedWithoutSayingAnythingToAnybody() {
        ArgInfo argInfo = integerSetting("gui.rows");
        ArgParser parser = new ArgParserNumber(argInfo);

        List<String> logged = Logs.capture(() -> {
            Object value = SettingsScanner.parsedOrDefault(harness.ecPluginData, console, parser, argInfo,
                    "4", 3, config, "gui.rows");

            assertEquals(4, ((Number) value).intValue());
        });

        assertTrue(console.getMessages().isEmpty());
        assertTrue(!anyContains(logged, "Fix your Config!"), "nothing fell back: " + logged);
    }

    private static boolean anyContains(List<String> lines, String fragment) {
        for (String line : lines) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
