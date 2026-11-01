package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.action.event.CommandActionEvent;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command framework raises the command source for its own commands only where the platform has no command event
 * of its own: there a cancelled {@code Pre} keeps the command from running and the {@code Post} follows the run; where
 * the platform raises the source, the framework posts nothing - one command, one post.
 */
class CommandActionSourceTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
    private final TestFPlayerSender steve = Senders.player("Steve").at("world", 5, 5, 5);

    public static class WarpCommand {
        int runs;

        @FinalCMD(aliases = {"warp", "w"})
        public void run(FCommandSender sender) {
            runs++;
        }
    }

    @BeforeEach
    void open() {
        harness = Commands.harness("CommandSource", tempDir.resolve("cmd"));
    }

    @AfterEach
    void close() {
        subscriptions.forEach(ECEventSubscription::unsubscribe);
        ActionSupport.declare("an undeclared platform", null, null, true, null);
        harness.close();
    }

    @Test
    void withoutANativeCommandSourceThePreComesBeforeTheRunAndThePostAfterIt() {
        ActionSupport.declare("Hytale 0.7.2", null, null, false, null);
        WarpCommand warp = new WarpCommand();
        FinalCMDPluginCommand command = harness.register(warp);
        List<CommandActionEvent.Post> posts = new ArrayList<>();
        List<Integer> runsSeenByPre = new ArrayList<>();
        List<Integer> runsSeenByPost = new ArrayList<>();
        subscriptions.add(ECEventBus.global().subscribe(CommandActionEvent.Post.class, posts::add));
        subscriptions.add(ECEventBus.global().subscribe(CommandActionEvent.Post.class, post -> runsSeenByPost.add(warp.runs)));
        subscriptions.add(ECEventBus.global().subscribe(CommandActionEvent.Pre.class, pre -> runsSeenByPre.add(warp.runs)));

        harness.dispatch(command, steve, "spawn");

        assertEquals(Arrays.asList(0), runsSeenByPre, "the Pre came before the command ran");
        assertEquals(Arrays.asList(1), runsSeenByPost, "the Post came after the body returned - the body never sees it");
        assertEquals(1, warp.runs);
        assertEquals(1, posts.size());
        assertEquals(steve, posts.get(0).getPlayer());
        assertEquals(new LinkedHashSet<>(Arrays.asList("warp", "w")), posts.get(0).getContext().getNames());
        assertEquals("/warp spawn", posts.get(0).getContext().getCommandLine());
    }

    @Test
    void withoutANativeCommandSourceACancelledPreKeepsTheCommandFromRunning() {
        ActionSupport.declare("Hytale 0.7.2", null, null, false, null);
        WarpCommand warp = new WarpCommand();
        FinalCMDPluginCommand command = harness.register(warp);
        List<CommandActionEvent.Post> posts = new ArrayList<>();
        subscriptions.add(ECEventBus.global().subscribe(CommandActionEvent.Pre.class, pre -> pre.setCancelled(true)));
        subscriptions.add(ECEventBus.global().subscribe(CommandActionEvent.Post.class, posts::add));

        harness.dispatch(command, steve, "");

        assertEquals(0, warp.runs);
        assertTrue(posts.isEmpty());
    }

    @Test
    void withANativeCommandSourceTheFrameworkPostsNothing() {
        ActionSupport.declare("Paper 1.21.1", null, null, true, null);
        WarpCommand warp = new WarpCommand();
        FinalCMDPluginCommand command = harness.register(warp);
        List<CommandActionEvent> heard = new ArrayList<>();
        subscriptions.add(ECEventBus.global().subscribe(CommandActionEvent.class, heard::add));

        harness.dispatch(command, steve, "");

        assertEquals(1, warp.runs);
        assertTrue(heard.isEmpty(), "the platform's own command event raised it already");
    }
}
