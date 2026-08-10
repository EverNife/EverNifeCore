package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A smoke test proving the harness itself works before it's reused across the whole
 * finalcommandsystemtests suite: register a trivial subcommand and dispatch it.
 */
class FinalCmdTestHarnessSmokeTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    public static class GreetCommand {

        static final AtomicReference<String> LAST_GREETED = new AtomicReference<>();

        @FinalCMD(aliases = "smokegreet")
        @FinalCMD.SubCMD(subcmd = "hello")
        public void hello(FCommandSender sender, @Arg("<name>") String name) {
            LAST_GREETED.set(name);
        }
    }

    @Test
    void registersAndDispatchesATrivialSubCommand() {
        harness = new FinalCmdTestHarness("SmokeTest", tempDir);

        FinalCMDPluginCommand command = harness.register(new GreetCommand());
        assertNotNull(command, "registration should have produced a captured command");
        assertEquals("smokegreet", command.getPrimaryLabel());

        TestCommandSender sender = new TestCommandSender("console");
        harness.dispatch(command, sender, "hello World");

        assertEquals("World", GreetCommand.LAST_GREETED.get());
        sender.assertNoMessageSent();
    }

    @Test
    void missingRequiredArgSendsHelpInsteadOfInvoking() {
        harness = new FinalCmdTestHarness("SmokeTest", tempDir);
        GreetCommand.LAST_GREETED.set(null);

        FinalCMDPluginCommand command = harness.register(new GreetCommand());
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "hello");

        assertTrue(GreetCommand.LAST_GREETED.get() == null);
        assertTrue(!sender.getMessages().isEmpty(), "a help line should have been sent");
    }
}
