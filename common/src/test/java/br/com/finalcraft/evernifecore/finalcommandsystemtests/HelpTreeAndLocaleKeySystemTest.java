package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLineTemplate;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the help of a TREE: what one node lists, who is allowed to see it, which placeholders a line
 * four levels down resolves, which entry of the language file each of them is filed under, and how a
 * flag chooses the spelling it shows.
 */
class HelpTreeAndLocaleKeySystemTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("HelpTree", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // one template, two paths, two threads: neither render sees the other's tokens
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "concurrenthelpline")
    public static class ConcurrentHelpLineCmd {
        @FinalCMD.SubCMD(subcmd = "info")
        public void info(FCommandSender sender) {}
    }

    @Test
    void concurrentRendersOfTheSameHelpLineDoNotContaminateEachOther() throws Exception {
        FinalCMDPluginCommand command = newHarness().register(new ConcurrentHelpLineCmd());
        HelpLineTemplate shared = command.getRoot().getChild("info").getHelpLineTemplate();
        assertNotNull(shared);

        CommandPath alice = pathOf("concurrenthelpline", ImmutableList.of("user", "Alice"), ImmutableList.of("user"), 0);
        CommandPath bob = pathOf("concurrenthelpline", ImmutableList.of("user", "Bob"), ImmutableList.of("user"), 0);

        TestCommandSender aliceSender = Senders.console("alice");
        TestCommandSender bobSender = Senders.console("bob");

        CountDownLatch startTogether = new CountDownLatch(1);
        Thread aliceThread = renderRepeatedly(shared, alice, aliceSender, startTogether);
        Thread bobThread = renderRepeatedly(shared, bob, bobSender, startTogether);

        startTogether.countDown();
        aliceThread.join(TimeUnit.SECONDS.toMillis(30));
        bobThread.join(TimeUnit.SECONDS.toMillis(30));

        assertEquals(200, aliceSender.getMessages().size());
        assertEquals(200, bobSender.getMessages().size());
        for (String message : aliceSender.getMessages()) {
            assertTrue(message.contains("user Alice"), message);
            assertFalse(message.contains("Bob"), message);
        }
        for (String message : bobSender.getMessages()) {
            assertTrue(message.contains("user Bob"), message);
            assertFalse(message.contains("Alice"), message);
        }
    }

    private static Thread renderRepeatedly(HelpLineTemplate template, CommandPath path, TestCommandSender sender, CountDownLatch startTogether) {
        Thread thread = new Thread(() -> {
            try {
                startTogether.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            for (int i = 0; i < 200; i++) {
                template.render(path).sendTo(sender);
            }
        });
        thread.start();
        return thread;
    }

    private static CommandPath pathOf(String label, List<String> segments, List<String> literals, int lastLiteralIndex) {
        return new CommandPath(label, segments, literals, lastLiteralIndex);
    }

    // ------------------------------------------------------------------
    // a node lists its own children and nobody else's, and every gate above it counts
    // ------------------------------------------------------------------

    public static class ToggleableNodeValidation extends CMDAccessValidation {
        static boolean allow = true;

        public ToggleableNodeValidation() {
        }

        @Override
        public boolean onPreCommandValidation(AccessContext accessContext) {
            return allow;
        }

        @Override
        public boolean onPreTabValidation(AccessContext accessContext) {
            //A branch with no @Execute has no method to name, and that is exactly the case this covers
            assertNotNull(accessContext.getCmdData());
            return allow;
        }
    }

    @FinalCMD(aliases = "nodehelp")
    public static class NodeHelpCmd {
        @FinalCMD.Node(subcmd = "user", permission = "nodehelp.user", validation = ToggleableNodeValidation.class)
        public static class UserNode {
            @FinalCMD.SubCMD(subcmd = "info")
            public void info(FCommandSender sender) {}

            @FinalCMD.SubCMD(subcmd = "secret", permission = "nodehelp.secret")
            public void secret(FCommandSender sender) {}

            @FinalCMD.SubCMD(subcmd = "here")
            public void here(FPlayer player) {}

            @FinalCMD.Node(subcmd = "permission")
            public static class PermissionNode {
                @FinalCMD.SubCMD(subcmd = "set")
                public void set(FCommandSender sender, @Arg("<node>") String node) {}
            }
        }
    }

    @Test
    void nodeHelpListsOnlyItsOwnChildrenAndFiltersEachOfThem() {
        FinalCMDPluginCommand command = newHarness().register(new NodeHelpCmd());
        ToggleableNodeValidation.allow = true;
        TestCommandSender sender = Senders.console().grant("nodehelp.user");

        harness.dispatch(command, sender, "user");

        sender.assertAnyMessageContains("user info");
        sender.assertAnyMessageContains("user permission");
        assertFalse(sender.anyMessageContains("secret"), "a child the sender has no permission for must not be listed");
        assertFalse(sender.anyMessageContains("here"), "a playerOnly child must not be listed for the console");
        assertFalse(sender.anyMessageContains("permission set"), "a grandchild belongs to its own node's help");
    }

    @Test
    void aNodeTheSenderCannotReachListsNothingAtAll() {
        FinalCMDPluginCommand command = newHarness().register(new NodeHelpCmd());
        ToggleableNodeValidation.allow = true;
        TestCommandSender sender = Senders.console(); //no nodehelp.user

        harness.dispatch(command, sender, "user");

        assertFalse(sender.anyMessageContains("user info"));
        sender.assertAnyMessageContains("permission");
    }

    @Test
    void theNodesOwnValidationGatesItsHelpEvenWithoutAnExecutable() {
        FinalCMDPluginCommand command = newHarness().register(new NodeHelpCmd());
        ToggleableNodeValidation.allow = false;
        TestCommandSender sender = Senders.console().grant("nodehelp.user");

        try {
            harness.dispatch(command, sender, "user");
            assertFalse(sender.anyMessageContains("user info"), "the node's own validation refused, so it has no help to show");
        } finally {
            ToggleableNodeValidation.allow = true;
        }
    }

    @Test
    void rootHelpListsLevelOneAndLeaksNoGrandchild() {
        FinalCMDPluginCommand command = newHarness().register(new NodeHelpCmd());
        ToggleableNodeValidation.allow = true;
        TestFPlayerSender sender = Senders.player("steve");
        sender.grant("nodehelp.user");

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("nodehelp user");
        assertFalse(sender.anyMessageContains("user info"), "level 2 belongs to the node's own help");
        assertFalse(sender.anyMessageContains("user permission"), "level 2 belongs to the node's own help");
    }

    // ------------------------------------------------------------------
    // the four path placeholders, resolved four levels down
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "pathplaceholders")
    public static class PathPlaceholdersCmd {
        @FCLocale(lang = LocaleType.EN_US, text = "L=[${label}] P=[${path}] PP=[${parentpath}] S=[${subcmd}]")
        static LocaleMessage PLACEHOLDERS;

        @FinalCMD.Node(subcmd = "user")
        public static class UserNode {
            @FinalCMD.Capture
            public String capture(@Arg("<user>") String user) {
                return user;
            }

            @FinalCMD.Node(subcmd = "permission")
            public static class PermissionNode {
                @FinalCMD.Node(subcmd = "group")
                public static class GroupNode {
                    @FinalCMD.SubCMD(subcmd = "set")
                    public void set(FCommandSender sender) {
                        PLACEHOLDERS.send(sender);
                    }
                }
            }
        }
    }

    @Test
    void pathPlaceholdersResolveAtDepthFour() {
        FinalCMDPluginCommand command = newHarness().register(new PathPlaceholdersCmd());
        TestCommandSender sender = Senders.console();

        harness.dispatch(command, sender, "user Steve permission group set");

        sender.assertAnyMessageContains("L=[pathplaceholders]");
        sender.assertAnyMessageContains("P=[user Steve permission group set]");
        sender.assertAnyMessageContains("PP=[user Steve permission group]");
        sender.assertAnyMessageContains("S=[set]");
    }

    // ------------------------------------------------------------------
    // two inner classes with the same simple name are two entries, not one
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "samesimplename")
    public static class SameSimpleNameNodesCmd {
        @FinalCMD.Node(subcmd = "alpha")
        public static class Alpha {
            @FinalCMD.Node(subcmd = "shared")
            public static class Shared {
                @FCLocale(lang = LocaleType.EN_US, text = "ALPHA SIDE")
                static LocaleMessage NOT_FOUND;

                @FinalCMD.SubCMD(subcmd = "go")
                public void go(FCommandSender sender) {}
            }
        }

        @FinalCMD.Node(subcmd = "beta")
        public static class Beta {
            @FinalCMD.Node(subcmd = "shared")
            public static class Shared {
                @FCLocale(lang = LocaleType.EN_US, text = "BETA SIDE")
                static LocaleMessage NOT_FOUND;

                @FinalCMD.SubCMD(subcmd = "go")
                public void go(FCommandSender sender) {}
            }
        }
    }

    @Test
    void twoInnerNodesWithTheSameSimpleNameGetTwoLanguageFileEntries() {
        FinalCMDPluginCommand command = newHarness().register(new SameSimpleNameNodesCmd());
        List<String> keys = new ArrayList<>(harness.ecPluginData.getLocalizedMessages().keySet());

        //The static field of each node: keyed by the nesting chain, so the second one no longer
        //inherits the first one's text
        assertTrue(keys.contains("HelpTreeAndLocaleKeySystemTest.SameSimpleNameNodesCmd.Alpha.Shared.NOT_FOUND"), keys.toString());
        assertTrue(keys.contains("HelpTreeAndLocaleKeySystemTest.SameSimpleNameNodesCmd.Beta.Shared.NOT_FOUND"), keys.toString());

        //The help line of each node's leaf: keyed by the command path (a line with no locales() of
        //its own is never filed in the language file, so the key is read off the message itself)
        assertEquals("samesimplename.alpha.shared.GO", helpLineKeyOf(command, "alpha", "shared", "go"));
        assertEquals("samesimplename.beta.shared.GO", helpLineKeyOf(command, "beta", "shared", "go"));

        TestCommandSender sender = Senders.console();
        SameSimpleNameNodesCmd.Alpha.Shared.NOT_FOUND.send(sender);
        SameSimpleNameNodesCmd.Beta.Shared.NOT_FOUND.send(sender);
        sender.assertAnyMessageContains("ALPHA SIDE");
        sender.assertAnyMessageContains("BETA SIDE");
    }

    private static String helpLineKeyOf(FinalCMDPluginCommand command, String... labels) {
        CommandNode node = command.getRoot();
        for (String label : labels) {
            node = node.getChild(label);
            assertNotNull(node, label);
        }
        return ((LocaleMessageImp) node.getHelpLineTemplate().getLocaleMessage()).getKey();
    }

    // ------------------------------------------------------------------
    // usageName shortens the line only, and only when it names a real spelling
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "usagename")
    public static class UsageNameCmd {
        @FinalCMD.SubCMD(subcmd = "run")
        public void run(FCommandSender sender,
                        @Arg.Flag(value = "--network", aliases = "-n", usageName = "-n", def = "false") Boolean network,
                        @Arg.Flag(value = "--hidden", aliases = "-h", usageName = "-h", showOnUsage = false) String hidden) {}
    }

    @FinalCMD(aliases = "usagenamebad")
    public static class UnknownUsageNameCmd {
        @FinalCMD.SubCMD(subcmd = "run")
        public void run(FCommandSender sender,
                        @Arg.Flag(value = "--network", aliases = "-n", usageName = "-x") Boolean network) {}
    }

    @Test
    void usageNameShowsTheShortSpellingOnTheLineAndKeepsBothOnTheHover() {
        FinalCMDPluginCommand command = newHarness().register(new UsageNameCmd());
        TestCommandSender sender = Senders.console();

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("[-n]");
        assertFalse(sender.anyMessageContains("[--network]"), "the line asked for the short spelling");

        String hover = sender.hoverTextOfMessageContaining("usagename run");
        assertNotNull(hover);
        assertTrue(hover.contains("--network | -n"), hover);

        assertFalse(sender.anyMessageContains("hidden"), "showOnUsage = false wins over usageName");
    }

    @Test
    void aUsageNameThatNamesNoDeclaredSpellingIsRefusedAtRegistration() {
        newHarness();

        ArgMountException error = harness.registerExpectingError(new UnknownUsageNameCmd());

        assertTrue(error.getMessage().contains("-x"), error.getMessage());
        assertTrue(error.getMessage().contains("--network"), error.getMessage());
        assertTrue(error.getMessage().contains("-n"), error.getMessage());
    }
}
