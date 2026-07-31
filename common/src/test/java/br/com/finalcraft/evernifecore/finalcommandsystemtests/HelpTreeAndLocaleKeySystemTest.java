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
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
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
    @TempDir(cleanup = CleanupMode.NEVER)
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
    // C1 - one template, two paths, two threads: neither render sees the other's tokens
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c1cmd")
    public static class C1_Cmd {
        @FinalCMD.SubCMD(subcmd = "info")
        public void info(FCommandSender sender) {}
    }

    @Test
    void concurrentRendersOfTheSameHelpLineDoNotContaminateEachOther() throws Exception {
        FinalCMDPluginCommand command = newHarness().register(new C1_Cmd());
        HelpLineTemplate shared = command.getRoot().getChild("info").getHelpLineTemplate();
        assertNotNull(shared);

        CommandPath alice = pathOf("c1cmd", ImmutableList.of("user", "Alice"), ImmutableList.of("user"), 0);
        CommandPath bob = pathOf("c1cmd", ImmutableList.of("user", "Bob"), ImmutableList.of("user"), 0);

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
    // C2/C3 - a node lists its own children and nobody else's, and every gate above it counts
    // ------------------------------------------------------------------

    public static class C2_NodeValidation extends CMDAccessValidation {
        static boolean allow = true;

        public C2_NodeValidation() {
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

    @FinalCMD(aliases = "c2cmd")
    public static class C2_Cmd {
        @FinalCMD.Node(subcmd = "user", permission = "c2.user", validation = C2_NodeValidation.class)
        public static class UserNode {
            @FinalCMD.SubCMD(subcmd = "info")
            public void info(FCommandSender sender) {}

            @FinalCMD.SubCMD(subcmd = "secret", permission = "c2.secret")
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
        FinalCMDPluginCommand command = newHarness().register(new C2_Cmd());
        C2_NodeValidation.allow = true;
        TestCommandSender sender = Senders.console().grant("c2.user");

        harness.dispatch(command, sender, "user");

        sender.assertAnyMessageContains("user info");
        sender.assertAnyMessageContains("user permission");
        assertFalse(sender.anyMessageContains("secret"), "a child the sender has no permission for must not be listed");
        assertFalse(sender.anyMessageContains("here"), "a playerOnly child must not be listed for the console");
        assertFalse(sender.anyMessageContains("permission set"), "a grandchild belongs to its own node's help");
    }

    @Test
    void aNodeTheSenderCannotReachListsNothingAtAll() {
        FinalCMDPluginCommand command = newHarness().register(new C2_Cmd());
        C2_NodeValidation.allow = true;
        TestCommandSender sender = Senders.console(); //no c2.user

        harness.dispatch(command, sender, "user");

        assertFalse(sender.anyMessageContains("user info"));
        sender.assertAnyMessageContains("permission");
    }

    @Test
    void theNodesOwnValidationGatesItsHelpEvenWithoutAnExecutable() {
        FinalCMDPluginCommand command = newHarness().register(new C2_Cmd());
        C2_NodeValidation.allow = false;
        TestCommandSender sender = Senders.console().grant("c2.user");

        try {
            harness.dispatch(command, sender, "user");
            assertFalse(sender.anyMessageContains("user info"), "the node's own validation refused, so it has no help to show");
        } finally {
            C2_NodeValidation.allow = true;
        }
    }

    @Test
    void rootHelpListsLevelOneAndLeaksNoGrandchild() {
        FinalCMDPluginCommand command = newHarness().register(new C2_Cmd());
        C2_NodeValidation.allow = true;
        TestFPlayerSender sender = Senders.player("steve");
        sender.grant("c2.user");

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("c2cmd user");
        assertFalse(sender.anyMessageContains("user info"), "level 2 belongs to the node's own help");
        assertFalse(sender.anyMessageContains("user permission"), "level 2 belongs to the node's own help");
    }

    // ------------------------------------------------------------------
    // C4 - the four path placeholders, resolved four levels down
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c4cmd")
    public static class C4_Cmd {
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
        FinalCMDPluginCommand command = newHarness().register(new C4_Cmd());
        TestCommandSender sender = Senders.console();

        harness.dispatch(command, sender, "user Steve permission group set");

        sender.assertAnyMessageContains("L=[c4cmd]");
        sender.assertAnyMessageContains("P=[user Steve permission group set]");
        sender.assertAnyMessageContains("PP=[user Steve permission group]");
        sender.assertAnyMessageContains("S=[set]");
    }

    // ------------------------------------------------------------------
    // C5 - two inner classes with the same simple name are two entries, not one
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c5cmd")
    public static class C5_Cmd {
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
        FinalCMDPluginCommand command = newHarness().register(new C5_Cmd());
        List<String> keys = new ArrayList<>(harness.ecPluginData.getLocalizedMessages().keySet());

        //The static field of each node: keyed by the nesting chain, so the second one no longer
        //inherits the first one's text
        assertTrue(keys.contains("HelpTreeAndLocaleKeySystemTest.C5_Cmd.Alpha.Shared.NOT_FOUND"), keys.toString());
        assertTrue(keys.contains("HelpTreeAndLocaleKeySystemTest.C5_Cmd.Beta.Shared.NOT_FOUND"), keys.toString());

        //The help line of each node's leaf: keyed by the command path (a line with no locales() of
        //its own is never filed in the language file, so the key is read off the message itself)
        assertEquals("c5cmd.alpha.shared.GO", helpLineKeyOf(command, "alpha", "shared", "go"));
        assertEquals("c5cmd.beta.shared.GO", helpLineKeyOf(command, "beta", "shared", "go"));

        TestCommandSender sender = Senders.console();
        C5_Cmd.Alpha.Shared.NOT_FOUND.send(sender);
        C5_Cmd.Beta.Shared.NOT_FOUND.send(sender);
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
    // C7 - usageName shortens the line only, and only when it names a real spelling
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "c7cmd")
    public static class C7_Cmd {
        @FinalCMD.SubCMD(subcmd = "run")
        public void run(FCommandSender sender,
                        @Arg.Flag(value = "--network", aliases = "-n", usageName = "-n", def = "false") Boolean network,
                        @Arg.Flag(value = "--hidden", aliases = "-h", usageName = "-h", showOnUsage = false) String hidden) {}
    }

    @FinalCMD(aliases = "c7badcmd")
    public static class C7_BadUsageNameCmd {
        @FinalCMD.SubCMD(subcmd = "run")
        public void run(FCommandSender sender,
                        @Arg.Flag(value = "--network", aliases = "-n", usageName = "-x") Boolean network) {}
    }

    @Test
    void usageNameShowsTheShortSpellingOnTheLineAndKeepsBothOnTheHover() {
        FinalCMDPluginCommand command = newHarness().register(new C7_Cmd());
        TestCommandSender sender = Senders.console();

        harness.dispatch(command, sender, "help");

        sender.assertAnyMessageContains("[-n]");
        assertFalse(sender.anyMessageContains("[--network]"), "the line asked for the short spelling");

        String hover = sender.hoverTextOfMessageContaining("c7cmd run");
        assertNotNull(hover);
        assertTrue(hover.contains("--network | -n"), hover);

        assertFalse(sender.anyMessageContains("hidden"), "showOnUsage = false wins over usageName");
    }

    @Test
    void aUsageNameThatNamesNoDeclaredSpellingIsRefusedAtRegistration() {
        newHarness();

        ArgMountException error = harness.registerExpectingError(new C7_BadUsageNameCmd());

        assertTrue(error.getMessage().contains("-x"), error.getMessage());
        assertTrue(error.getMessage().contains("--network"), error.getMessage());
        assertTrue(error.getMessage().contains("-n"), error.getMessage());
    }
}
