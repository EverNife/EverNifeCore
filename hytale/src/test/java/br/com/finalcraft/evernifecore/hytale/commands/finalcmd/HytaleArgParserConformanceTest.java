package br.com.finalcraft.evernifecore.hytale.commands.finalcmd;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFCommandSender;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers.ArgParserContextualHytaleFCommandSender;
import br.com.finalcraft.evernifecore.hytale.commands.finalcmd.argument.contextualparsers.ArgParserContextualHytaleFPlayer;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.PlatformConformance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Hytale half of the same conformance the Bukkit side runs: a parser this platform registers for
 * its own sender types is registered after the builtins that already answer for their supertypes, so
 * only a lookup that asks for the type itself first ever reaches it.
 */
class HytaleArgParserConformanceTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    @Test
    void thePlatformsOwnContextualParsersAreTheOnesResolved() {
        //the harness registers the builtins; the platform's own registrations come after, as on a server
        harness = new FinalCmdTestHarness("HyArgParsers", tempDir);
        ArgParserManager.addGlobalContextualParser(HytaleFCommandSender.class, ArgParserContextualHytaleFCommandSender.class);
        ArgParserManager.addGlobalContextualParser(HytaleFPlayer.class, ArgParserContextualHytaleFPlayer.class);

        Map<Class<?>, Class<?>> expected = new LinkedHashMap<>();
        expected.put(HytaleFCommandSender.class, ArgParserContextualHytaleFCommandSender.class);
        expected.put(HytaleFPlayer.class, ArgParserContextualHytaleFPlayer.class);

        List<String> violations = PlatformConformance.checkArgParsers(harness.ecPluginData, expected);

        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }
}
