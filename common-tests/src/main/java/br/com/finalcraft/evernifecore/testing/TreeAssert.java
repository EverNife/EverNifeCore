package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Structural assertions over a registered command tree: that a path exists, whether it is a branch or
 * an executable, how many tokens it eats, which children it declares and which of them a given sender
 * can actually see.
 * <p>
 * Everything here reads the tree the framework built, so a test states the SHAPE it expects instead of
 * a sample of dispatch outputs that happens to imply it. Navigation is cursor-style: {@link #at(String)}
 * moves, every other method asserts where the cursor stands and returns {@code this}.
 * <p>
 * Visibility is the one thing it does not re-implement: {@link #offersTo} asks the real tab-complete,
 * because a second copy of "can this sender see this node" is a copy that eventually disagrees with the
 * one that ships.
 */
public final class TreeAssert {

    private final FinalCmdTestHarness harness;
    private final FinalCMDPluginCommand command;
    private final CommandNode root;
    private CommandNode cursor;

    TreeAssert(FinalCmdTestHarness harness, FinalCMDPluginCommand command) {
        this.harness = harness;
        this.command = command;
        this.root = command.getRoot();
        this.cursor = this.root;
    }

    /**
     * Moves the cursor to {@code nodePath}, a dot path of labels below the root ({@code "user.permission"});
     * the empty string is the root itself. Any alias works, not only the primary label.
     *
     * @throws AssertionError when a segment does not exist, naming what does
     */
    public TreeAssert at(String nodePath) {
        CommandNode node = root;
        if (!nodePath.isEmpty()) {
            for (String segment : nodePath.split("\\.")) {
                CommandNode child = node.getChild(segment);
                if (child == null) {
                    throw new AssertionError("There is no [" + segment + "] under [" + describe(node) + "]. Its children are " + childLabels(node) + ".");
                }
                node = child;
            }
        }
        this.cursor = node;
        return this;
    }

    /** The node the cursor stands on, for an assertion this class has no vocabulary for. */
    public CommandNode node() {
        return cursor;
    }

    /** Asserts this node runs a method of its own - a leaf, or a node with a {@code @FinalCMD.Execute}. */
    public TreeAssert isExecutable() {
        if (cursor.getExecutable() == null) {
            throw new AssertionError("[" + describe(cursor) + "] has no executable of its own. Give it a @FinalCMD.Execute, or assert isBranch().");
        }
        return this;
    }

    /** Asserts this node only routes: typing it prints its help, its children are what runs. */
    public TreeAssert isBranch() {
        if (cursor.getExecutable() != null) {
            throw new AssertionError("[" + describe(cursor) + "] does have an executable of its own - it is not a pure branch.");
        }
        if (!cursor.hasChildren()) {
            throw new AssertionError("[" + describe(cursor) + "] has no children at all, so it is neither a branch nor runnable.");
        }
        return this;
    }

    /**
     * Asserts this node's {@code @FinalCMD.Capture} eats exactly these tokens, spelled as the capture
     * declared them. No argument at all asserts the node eats nothing.
     */
    public TreeAssert eats(String... captureArgNames) {
        List<String> declared = cursor.getCapture() == null ? new ArrayList<String>() : cursor.getCapture().getArgNames();
        List<String> expected = Arrays.asList(captureArgNames);
        if (!declared.equals(expected)) {
            throw new AssertionError("[" + describe(cursor) + "] eats " + declared + ", expected " + expected + ".");
        }
        return this;
    }

    /** Asserts these are this node's children, in the order help and tab list them. */
    public TreeAssert hasChildren(String... primaryLabels) {
        List<String> declared = childLabels(cursor);
        List<String> expected = Arrays.asList(primaryLabels);
        if (!declared.equals(expected)) {
            throw new AssertionError("[" + describe(cursor) + "] has children " + declared + ", expected " + expected + ".");
        }
        return this;
    }

    /**
     * Asserts which flags are recognized once the path reaches this node - the ones every ancestor
     * capture declares plus this node's own, which is the set a sender may write after the path.
     */
    public TreeAssert recognizesFlags(String... rawNames) {
        List<String> declared = new ArrayList<String>();
        for (CMDMethodInterpreter.FlagBinding binding : cursor.getAccumulatedFlagBindings()) {
            declared.add(binding.getRawName());
        }
        List<String> expected = Arrays.asList(rawNames);
        if (!declared.equals(expected)) {
            throw new AssertionError("[" + describe(cursor) + "] recognizes flags " + declared + ", expected " + expected + ".");
        }
        return this;
    }

    /**
     * The tokens a sender types to stand exactly on this node: every literal down the path, plus one
     * token per ancestor capture. The capture tokens are the argument NAMES, which is enough because
     * the traversal counts tokens and never resolves them.
     */
    public List<String> typedPrefix() {
        return cursor.toUsagePath(command.getPrimaryLabel(), true).getSegments();
    }

    /**
     * Asserts what {@code sender} is offered when they stand on this node and press tab - the children
     * their permissions, {@code playerOnly} and validations let them reach.
     */
    public TreeAssert offersTo(FCommandSender sender, String... labels) {
        List<String> args = new ArrayList<String>(typedPrefix());
        args.add("");
        List<String> offered = harness.tab(command, sender, args.toArray(new String[0]));
        List<String> expected = Arrays.asList(labels);
        if (!offered.equals(expected)) {
            throw new AssertionError("Standing on [" + describe(cursor) + "], " + sender.getName() + " is offered " + offered + ", expected " + expected + ".");
        }
        return this;
    }

    private static List<String> childLabels(CommandNode node) {
        List<String> labels = new ArrayList<String>();
        for (CommandNode child : node.getChildren()) {
            labels.add(child.getPrimaryLabel());
        }
        return labels;
    }

    private static String describe(CommandNode node) {
        return node.getNodePath().isEmpty() ? "/" + node.getPrimaryLabel() : node.getNodePath();
    }
}
