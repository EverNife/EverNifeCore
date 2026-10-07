package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation.AccessContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A node with no label of its own ({@code @FinalCMD.Node(subcmd = "")}): its verbs answer right under the
 * parent, while its permission, validations and nested labeled nodes still apply.
 */
class HoistedNodeSystemTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("Hoisted", tempDir);
        return harness;
    }

    private static String describeChildren(CommandNode node) {
        StringBuilder builder = new StringBuilder();
        for (CommandNode child : node.getChildren()) {
            describe(child, builder, 0);
        }
        return builder.toString();
    }

    private static void describe(CommandNode node, StringBuilder builder, int depth) {
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
        builder.append("[").append(node.getPrimaryLabel()).append("]")
                .append(node.getExecutable() == null ? "" : "*")
                .append(" path=").append(node.getNodePath())
                .append("\n");
        for (CommandNode child : node.getChildren()) {
            describe(child, builder, depth + 1);
        }
    }

    public static class Deny extends CMDAccessValidation {
        static int commandCalls;

        @Override
        public boolean onPreCommandValidation(AccessContext accessContext) {
            commandCalls++;
            return false;
        }

        @Override
        public boolean onPreTabValidation(AccessContext accessContext) {
            return false;
        }
    }

    public static class ArenaVerbs {
        static String lastVerb;

        @FinalCMD.SubCMD(subcmd = "enable")
        public void enable(FCommandSender sender) {
            lastVerb = "enable";
        }

        @FinalCMD.SubCMD(subcmd = "disable")
        public void disable(FCommandSender sender) {
            lastVerb = "disable";
        }

        @FinalCMD.Node(subcmd = "group")
        public static class GroupNode {
            @FinalCMD.SubCMD(subcmd = "add")
            public void add(FCommandSender sender, @Arg("<name>") String name) {
                lastVerb = "group add " + name;
            }
        }
    }

    @FinalCMD(aliases = "arenacmd")
    public static class Arena_Cmd {
        static boolean statusRan;

        @FinalCMD.SubCMD(subcmd = "status")
        public void status(FCommandSender sender) {
            statusRan = true;
        }

        @FinalCMD.Node(subcmd = "", permission = "arena.admin")
        private final ArenaVerbs verbs = new ArenaVerbs();
    }

    private static TestCommandSender admin() {
        return new TestCommandSender("console").grant("arena.admin");
    }

    @Test
    void verbsOfAnUnlabeledNodeAnswerUnderTheParent() {
        FinalCMDPluginCommand command = newHarness().register(new Arena_Cmd());

        ArenaVerbs.lastVerb = null;
        harness.dispatch(command, admin(), "enable");
        assertEquals("enable", ArenaVerbs.lastVerb);

        harness.dispatch(command, admin(), "disable");
        assertEquals("disable", ArenaVerbs.lastVerb);
    }

    @Test
    void theNodePermissionGatesEveryHoistedVerbAndTheParentStillListsTheOthers() {
        FinalCMDPluginCommand command = newHarness().register(new Arena_Cmd());
        TestCommandSender nobody = new TestCommandSender("console");

        ArenaVerbs.lastVerb = null;
        harness.dispatch(command, nobody, "enable");
        assertNull(ArenaVerbs.lastVerb, "the hoisted node's permission applies to the verb it lifted");

        List<String> tab = harness.tab(command, nobody, "");
        assertTrue(tab.contains("status"), "tab: " + tab);
        assertFalse(tab.contains("enable"), "tab: " + tab);

        List<String> adminTab = harness.tab(command, admin(), "");
        assertTrue(adminTab.contains("enable") && adminTab.contains("status"), "tab: " + adminTab);

        harness.dispatch(command, nobody, "status");
        assertTrue(Arena_Cmd.statusRan);
    }

    @FinalCMD(aliases = "validatedcmd")
    public static class Validated_Cmd {
        @FinalCMD.SubCMD(subcmd = "status")
        public void status(FCommandSender sender) {}

        @FinalCMD.Node(subcmd = "", validation = Deny.class)
        private final ArenaVerbs verbs = new ArenaVerbs();
    }

    @Test
    void theNodeValidationRunsOnTheHoistedVerbOnce() {
        FinalCMDPluginCommand command = newHarness().register(new Validated_Cmd());

        ArenaVerbs.lastVerb = null;
        Deny.commandCalls = 0;
        harness.dispatch(command, new TestCommandSender("console"), "enable");

        assertNull(ArenaVerbs.lastVerb);
        assertEquals(1, Deny.commandCalls, "the lent validation is asked once, not once per walk step");
    }

    @FinalCMD(aliases = "clashcmd")
    public static class Clash_Cmd {
        @FinalCMD.SubCMD(subcmd = "enable")
        public void enable(FCommandSender sender) {}

        @FinalCMD.Node(subcmd = "")
        private final ArenaVerbs verbs = new ArenaVerbs();
    }

    @Test
    void aHoistedVerbClashingWithASiblingOfTheParentIsRefused() {
        ArgMountException error = newHarness().registerExpectingError(new Clash_Cmd());

        assertTrue(error.getMessage().contains("ArenaVerbs#enable"), error.getMessage());
        assertTrue(error.getMessage().contains("already taken by [enable]"), error.getMessage());
    }

    @FinalCMD(aliases = "twicecmd")
    public static class TwiceHoisted_Cmd {
        @FinalCMD.Node(subcmd = "")
        private final ArenaVerbs first = new ArenaVerbs();

        @FinalCMD.Node(subcmd = "")
        private final ArenaVerbs second = new ArenaVerbs();
    }

    @Test
    void theSameClassHoistedTwiceClashesWithItself() {
        ArgMountException error = newHarness().registerExpectingError(new TwiceHoisted_Cmd());

        assertTrue(error.getMessage().contains("already taken by"), error.getMessage());
    }

    @FinalCMD(aliases = "emptysubcmd")
    public static class EmptySubCmd_Cmd {
        @FinalCMD.SubCMD(subcmd = "")
        public void nothing(FCommandSender sender) {}
    }

    @Test
    void anEmptySubCmdLabelIsStillRefused() {
        ArgMountException error = newHarness().registerExpectingError(new EmptySubCmd_Cmd());

        assertTrue(error.getMessage().contains("declares an empty label"), error.getMessage());
    }

    @FinalCMD(aliases = "mixedcmd")
    public static class MixedEmpty_Cmd {
        @FinalCMD.Node(subcmd = {"", "x"})
        private final ArenaVerbs verbs = new ArenaVerbs();
    }

    @Test
    void anEmptyLabelMixedWithAnotherIsRefused() {
        ArgMountException error = newHarness().registerExpectingError(new MixedEmpty_Cmd());

        assertTrue(error.getMessage().contains("declares an empty label"), error.getMessage());
    }

    public static class CapturingVerbs {
        @FinalCMD.Capture
        public String capture(@Arg("<arena>") String arena) {
            return arena;
        }

        @FinalCMD.SubCMD(subcmd = "enable")
        public void enable(FCommandSender sender) {}
    }

    @FinalCMD(aliases = "capturecmd")
    public static class HoistedCapture_Cmd {
        @FinalCMD.Node(subcmd = "")
        private final CapturingVerbs verbs = new CapturingVerbs();
    }

    @Test
    void aHoistedNodeWithACaptureIsRefused() {
        ArgMountException error = newHarness().registerExpectingError(new HoistedCapture_Cmd());

        assertTrue(error.getMessage().contains("@FinalCMD.Capture [capture]"), error.getMessage());
        assertTrue(error.getMessage().contains("give the node a label, or move the method to the parent"), error.getMessage());
    }

    @Test
    void aLabeledNodeInsideTheHoistedOneKeepsItsSegment() {
        FinalCMDPluginCommand command = newHarness().register(new Arena_Cmd());

        ArenaVerbs.lastVerb = null;
        harness.dispatch(command, admin(), "group add red");
        assertEquals("group add red", ArenaVerbs.lastVerb);

        CommandNode group = command.getRoot().getChild("group");
        assertEquals("group", group.getNodePath());
        assertEquals("arenacmd.group", group.getLocaleKeyPrefix());
    }

    @Test
    void theParentHelpListsTheHoistedVerbs() {
        FinalCMDPluginCommand command = newHarness().register(new Arena_Cmd());
        TestCommandSender sender = admin();

        harness.dispatch(command, sender, "");

        sender.assertAnyMessageContains("arenacmd enable");
        sender.assertAnyMessageContains("arenacmd group");
        sender.assertAnyMessageContains("arenacmd status");
        assertFalse(sender.anyMessageContains("arenacmd  "), "no empty segment on a help line: " + sender.getMessages());
    }

    @Test
    void theTreeShowsNoEmptySegment() {
        FinalCMDPluginCommand command = newHarness().register(new Arena_Cmd());

        assertEquals("[disable]* path=disable\n"
                        + "[enable]* path=enable\n"
                        + "[group] path=group\n"
                        + "  [add]* path=group.add\n"
                        + "[status]* path=status\n",
                describeChildren(command.getRoot()));
    }
}
