package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code @Arg(fromSender = true)} on the parsers that name a subject: the token stays optional, and
 * leaving it out means "me".
 *
 * <p>The registration is half the point. A parser that does not override {@code fromSender} is refused
 * when the command is built, because such an argument would silently eat the NEXT argument's token
 * instead of being inferred - so a command that registers at all is already proof the override is
 * there.
 */
class FromSenderArgSystemTest {

    //NEVER a plain @TempDir: the locale bootstrap's async save can race JUnit's cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) {
            harness.close();
        }
        PlayerDataWorld.tearDown();
    }

    @Test
    void anOmittedPlayerDataIsTheSendersOwn() {
        harness = new FinalCmdTestHarness("FromSenderPlayerData", tempDir);
        PlayerDataWorld.with(Storages.h2("from_sender_playerdata")).boot(tempDir);

        UUID alice = UUID.randomUUID();
        PlayerController.handleLogin(alice, "Alice").join();

        PlayerDataCommand executor = new PlayerDataCommand();
        FinalCMDPluginCommand command = harness.register(executor);
        assertNotNull(command, "a command declaring @Arg(fromSender = true) has to register");

        harness.dispatch(command, new TestFPlayerSender("Alice", alice), "");

        assertNotNull(executor.captured, "the omitted argument was not answered from the sender");
        assertEquals(alice, executor.captured.getUniqueId());
    }

    /** A name still wins: inferring is what happens when nobody said who, not instead of what they said. */
    @Test
    void aTypedNameStillOutranksTheSender() {
        harness = new FinalCmdTestHarness("FromSenderNamed", tempDir);
        PlayerDataWorld.with(Storages.h2("from_sender_named")).boot(tempDir);

        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        PlayerController.handleLogin(alice, "Alice").join();
        PlayerController.handleLogin(bob, "Bob").join();

        PlayerDataCommand executor = new PlayerDataCommand();
        FinalCMDPluginCommand command = harness.register(executor);

        harness.dispatch(command, new TestFPlayerSender("Bob", bob), "Alice");

        assertNotNull(executor.captured);
        assertEquals(alice, executor.captured.getUniqueId(), "the typed name was ignored");
    }

    /** The console is nobody, so it hears why instead of getting somebody else's data. */
    @Test
    void theConsoleIsRefusedByName() {
        harness = new FinalCmdTestHarness("FromSenderConsole", tempDir);
        PlayerDataWorld.with(Storages.h2("from_sender_console")).boot(tempDir);

        PlayerDataCommand executor = new PlayerDataCommand();
        FinalCMDPluginCommand command = harness.register(executor);

        harness.dispatch(command, new TestCommandSender("Console"), "");

        assertNull(executor.captured, "the console has no own data, so the method must not run");
    }

    @Test
    void anOmittedSectionIsTheSendersOwnSection() {
        harness = new FinalCmdTestHarness("FromSenderSection", tempDir);
        PlayerDataWorld.with(Storages.h2("from_sender_section")).sections(NotesSection.class).boot(tempDir);

        UUID alice = UUID.randomUUID();
        PlayerController.handleLogin(alice, "Alice").join();

        SectionCommand executor = new SectionCommand();
        FinalCMDPluginCommand command = harness.register(executor);
        assertNotNull(command, "a PDSection subtype resolves through the IPlayerData parser, override included");

        harness.dispatch(command, new TestFPlayerSender("Alice", alice), "");

        assertNotNull(executor.captured, "the omitted section was not answered from the sender");
        assertEquals(alice, executor.captured.getUniqueId());
    }

    @Test
    void anOmittedFPlayerIsWhoeverTyped() {
        harness = new FinalCmdTestHarness("FromSenderFPlayer", tempDir);
        PlayerDataWorld.with(Storages.h2("from_sender_fplayer")).boot(tempDir);

        UUID alice = UUID.randomUUID();
        PlayerController.handleLogin(alice, "Alice").join();

        FPlayerCommand executor = new FPlayerCommand();
        FinalCMDPluginCommand command = harness.register(executor);
        assertNotNull(command);

        harness.dispatch(command, new TestFPlayerSender("Alice", alice), "");

        assertNotNull(executor.captured, "the omitted player was not answered from the sender");
        assertEquals(alice, executor.captured.getUniqueId());
    }

    // ------------------------------------------------------------------

    public static class NotesSection extends PDSection {

        public NotesSection() {
            //Required no-arg constructor
        }
    }

    public static class PlayerDataCommand {

        PlayerData captured;

        @FinalCMD(aliases = {"fromsenderplayerdata"})
        public void run(FCommandSender sender, @Arg(value = "[player]", fromSender = true) PlayerData target) {
            this.captured = target;
        }
    }

    public static class SectionCommand {

        NotesSection captured;

        @FinalCMD(aliases = {"fromsendersection"})
        public void run(FCommandSender sender, @Arg(value = "[player]", fromSender = true) NotesSection target) {
            this.captured = target;
        }
    }

    public static class FPlayerCommand {

        FPlayer captured;

        @FinalCMD(aliases = {"fromsenderfplayer"})
        public void run(FCommandSender sender, @Arg(value = "[player]", fromSender = true) FPlayer target) {
            this.captured = target;
        }
    }
}
