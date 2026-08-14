package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text the logging call site does not control - a name, a path, whatever was typed - belongs in a
 * parameter, never pasted into the format string. Pasted, a {@code {}} inside it consumes the
 * trailing {@link Throwable} as if it were a parameter, {@link ECLogFormat}'s trailing-throwable
 * rule stops firing, and the stack trace vanishes from the very line that exists to explain the
 * failure.
 *
 * <p>What is pinned here is the call site. The formatter is doing exactly what it promises, so no
 * assertion about it would catch this.</p>
 */
class ExternalTextInLogMessagesTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    @FinalCMD(aliases = "typedplaceholder")
    public static class FailingCmd {
        @FinalCMD.SubCMD(subcmd = "run")
        public void run(FCommandSender sender, @Arg("<text>") String text) {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void aCommandFailureKeepsItsStackWhenTheSenderTypedAPlaceholder() {
        harness = new FinalCmdTestHarness("ExternalText", tempDir);
        FinalCMDPluginCommand command = harness.register(new FailingCmd());
        TestCommandSender sender = new TestCommandSender("console");

        List<String> logged = Logs.capture(() -> harness.dispatch(command, sender, "run {}"));

        //a stack frame is the only evidence the trailing-throwable rule fired: the exception's own
        //toString reaches the line either way, so asserting on "IllegalStateException: boom" passes
        //just as happily with the trace gone
        String frame = "\tat " + FailingCmd.class.getName() + ".run";
        assertTrue(logged.stream().anyMatch(line -> line.contains(frame)),
                "what the sender typed ate the exception; the line carries no stack trace: " + logged);
    }
}
