package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFCommandSender;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers.ArgParserContextualMinecraftFCommandSender;
import br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.contextualparsers.ArgParserContextualMinecraftFPlayer;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.PlatformConformance;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parsers this platform registers for its own types have to be the ones the framework resolves.
 * They are all registered AFTER the builtins, and every one of them is a subtype of a builtin type, so
 * a registry that matched by assignability alone answered with the builtin and left these unreachable.
 */
class MinecraftArgParserConformanceTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    @Test
    void thePlatformsOwnContextualParsersAreTheOnesResolved() {
        //the harness registers the builtins; the platform's own registrations come after, as on a server
        harness = new FinalCmdTestHarness("McArgParsers", tempDir);
        ArgParserManager.addGlobalContextualParser(MinecraftFCommandSender.class, ArgParserContextualMinecraftFCommandSender.class);
        ArgParserManager.addGlobalContextualParser(MinecraftFPlayer.class, ArgParserContextualMinecraftFPlayer.class);

        Map<Class<?>, Class<?>> expected = new LinkedHashMap<>();
        expected.put(MinecraftFCommandSender.class, ArgParserContextualMinecraftFCommandSender.class);
        expected.put(MinecraftFPlayer.class, ArgParserContextualMinecraftFPlayer.class);

        List<String> violations = PlatformConformance.checkArgParsers(harness.ecPluginData, expected);

        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void theBuiltinStillAnswersForEverythingElseOfThatFamily() {
        harness = new FinalCmdTestHarness("McArgParsersFallback", tempDir);

        //an unrelated FCommandSender implementation has no parser of its own, so the assignable pass
        //still answers - narrowing the lookup must not turn it into "exact or nothing"
        assertEquals("ArgParserContextualFCommandSender",
                ArgParserManager.getContextualParser(harness.ecPluginData, ForeignSender.class).getSimpleName());
    }

    /** Some other plugin's sender: an {@code FCommandSender} nobody registered a parser for. */
    private interface ForeignSender extends br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender {
    }
}
