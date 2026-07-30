package br.com.finalcraft.evernifecore.commands.finalcmd.tree;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContextTemplate;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLineTemplate;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One segment of a registered command tree. The same type covers all three shapes:
 * <ul>
 *     <li>the ROOT ({@code @FinalCMD}) - no parent, its executable is the main method if there is one;</li>
 *     <li>a NODE ({@code @FinalCMD.Node}) - has children, may eat tokens through a {@link CaptureBinding},
 *     and may be executable through {@code @FinalCMD.Execute};</li>
 *     <li>a LEAF ({@code @FinalCMD.SubCMD}) - no children, always executable.</li>
 * </ul>
 * A node with no children ends the traversal, which is why a leaf needs no flag of its own.
 */
public class CommandNode {

    private final @Nullable CommandNode parent;
    private final CMDData<?> cmdData;
    private final Object nodeInstance;
    private final ECPluginData providingPlugin;
    private final int depth;
    private final String nodePath;
    private final String usagePath;

    private final Map<String, CommandNode> childrenByLabel = new LinkedHashMap<>();
    private final List<CommandNode> children = new ArrayList<>();

    private @Nullable CaptureBinding capture;
    private @Nullable CMDMethodInterpreter executable;
    private @Nullable HelpLineTemplate helpLineTemplate;
    private @Nullable HelpContextTemplate helpContext;

    public CommandNode(@Nullable CommandNode parent, @Nonnull CMDData<?> cmdData, @Nonnull Object nodeInstance, @Nonnull ECPluginData providingPlugin) {
        this.parent = parent;
        this.cmdData = cmdData;
        this.nodeInstance = nodeInstance;
        this.providingPlugin = providingPlugin;
        this.depth = parent == null ? 0 : parent.depth + 1;
        this.nodePath = parent == null ? "" : join(parent.nodePath, ".", getPrimaryLabel());
        //The parent's capture is already bound when its children are scanned, so the ancestors'
        //captured argument names are baked in here once instead of being walked on every render.
        this.usagePath = parent == null ? "" : join(parent.getUsagePath(), " ", getPrimaryLabel());
    }

    private static String join(String prefix, String separator, String segment) {
        return prefix.isEmpty() ? segment : prefix + separator + segment;
    }

    public @Nullable CommandNode getParent() {
        return parent;
    }

    public String[] getLabels() {
        return cmdData.getLabels();
    }

    public String getPrimaryLabel() {
        return cmdData.getLabels()[0];
    }

    /** Every label but the primary one - the aliases this node also answers to. */
    public String[] getExtraLabels() {
        String[] labels = getLabels();
        return Arrays.copyOfRange(labels, 1, labels.length);
    }

    /**
     * Replaces the aliases this node answers to, keeping the primary label - which is this node's
     * identity, and what its locale keys, node path and help lines are filed under. The parent's label
     * index is rebuilt, so the labels dropped here stop resolving immediately.
     */
    public void setExtraLabels(@Nonnull String[] extraLabels) {
        String[] labels = new String[1 + extraLabels.length];
        labels[0] = getPrimaryLabel();
        System.arraycopy(extraLabels, 0, labels, 1, extraLabels.length);
        cmdData.setLabels(labels);
        if (parent != null) {
            parent.indexChild(this);
        }
    }

    public CMDData<?> getCmdData() {
        return cmdData;
    }

    public Object getNodeInstance() {
        return nodeInstance;
    }

    public ECPluginData getProvidingPlugin() {
        return providingPlugin;
    }

    public int getDepth() {
        return depth;
    }

    /** Primary labels from the root (exclusive) down to this node, dot joined; empty at the root. */
    public String getNodePath() {
        return nodePath;
    }

    public CommandNode getRoot() {
        CommandNode node = this;
        while (node.parent != null) {
            node = node.parent;
        }
        return node;
    }

    /**
     * What everything this node answers for is filed under in the language file: the command's primary
     * label, then every literal down to here - {@code "lp.user.permission"}.
     * <p>
     * It is the path and not the class name because two inner classes called {@code PermissionNode}
     * under different parents are two different commands, and a simple name cannot tell them apart.
     */
    public String getLocaleKeyPrefix() {
        String rootLabel = getRoot().getPrimaryLabel();
        return nodePath.isEmpty() ? rootLabel : rootLabel + "." + nodePath;
    }

    /**
     * How this node is spelled on a usage line: primary labels plus each ancestor capture's argument
     * names, in the order a sender types them - {@code "user <user> permission"}.
     */
    public String getUsagePath() {
        return usagePath + (capture == null ? "" : " " + String.join(" ", capture.getArgNames()));
    }

    /**
     * This node written as a path whose capture tokens are the argument NAMES instead of typed values -
     * what a help line shows for a command nobody has typed yet ({@code "/lp user <user> permission"}).
     *
     * @param label the root alias to render it under
     * @param withOwnCapture whether this node's own capture names are part of it. A line built by
     * {@code buildHelpLineTemplate} appends the method's own {@code @Arg} names itself, so the capture method's
     * line asks for false and everybody else for true
     */
    public CommandPath toUsagePath(@Nonnull String label, boolean withOwnCapture) {
        List<CommandNode> ancestry = new ArrayList<>();
        for (CommandNode node = this; node != null && node.parent != null; node = node.parent) {
            ancestry.add(node);
        }
        Collections.reverse(ancestry);

        List<String> segments = new ArrayList<>();
        List<String> literals = new ArrayList<>();
        int lastLiteralIndex = -1;
        for (CommandNode node : ancestry) {
            lastLiteralIndex = segments.size();
            segments.add(node.getPrimaryLabel());
            literals.add(node.getPrimaryLabel());
            if (node.capture != null && (node != this || withOwnCapture)){
                segments.addAll(node.capture.getArgNames());
            }
        }
        return new CommandPath(label, segments, literals, lastLiteralIndex);
    }

    public @Nullable CaptureBinding getCapture() {
        return capture;
    }

    public void setCapture(@Nullable CaptureBinding capture) {
        this.capture = capture;
    }

    public @Nullable CMDMethodInterpreter getExecutable() {
        return executable;
    }

    public void setExecutable(@Nullable CMDMethodInterpreter executable) {
        this.executable = executable;
    }

    public List<CommandNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /** The child claiming {@code label} (any of its aliases, case insensitive), or null. */
    public @Nullable CommandNode getChild(@Nonnull String label) {
        return childrenByLabel.get(label.toLowerCase(Locale.ROOT));
    }

    /** @return the child already claiming one of {@code child}'s labels, or null when every label is free */
    public @Nullable CommandNode findLabelClash(@Nonnull CommandNode child) {
        for (String label : child.getLabels()) {
            CommandNode clash = childrenByLabel.get(label.toLowerCase(Locale.ROOT));
            if (clash != null) {
                return clash;
            }
        }
        return null;
    }

    public void addChild(@Nonnull CommandNode child) {
        children.add(child);
        indexChild(child);
    }

    /** Drops {@code child} and everything under it: no dispatch, no tab, no help line, no lookup. */
    public void removeChild(@Nonnull CommandNode child) {
        children.remove(child);
        childrenByLabel.values().removeIf(indexed -> indexed == child);
    }

    /** (Re)files {@code child} under every label it currently declares, dropping the ones it dropped. */
    private void indexChild(@Nonnull CommandNode child) {
        childrenByLabel.values().removeIf(indexed -> indexed == child);
        for (String label : child.getLabels()) {
            childrenByLabel.put(label.toLowerCase(Locale.ROOT), child);
        }
    }

    /** Stable order for help and tab: primary label, alphabetically. */
    public void sortChildren() {
        Collections.sort(children, Comparator.comparing(CommandNode::getPrimaryLabel));
    }

    /**
     * The line that represents this node inside its parent's help. An executable node reuses its
     * executable's line; a pure branch gets one synthesized at registration.
     */
    public @Nullable HelpLineTemplate getHelpLineTemplate() {
        return executable != null ? executable.getHelpLineTemplate() : helpLineTemplate;
    }

    public void setHelpLineTemplate(@Nullable HelpLineTemplate helpLineTemplate) {
        this.helpLineTemplate = helpLineTemplate;
    }

    /** This node's own help (its children); null on a leaf. */
    public @Nullable HelpContextTemplate getHelpContext() {
        return helpContext;
    }

    public void setHelpContext(@Nullable HelpContextTemplate helpContext) {
        this.helpContext = helpContext;
    }

    /** Whether the console can reach anything under this node at all. */
    public boolean isPlayerOnly() {
        return executable != null && executable.isPlayerOnly();
    }

    /**
     * Every flag recognized once the path reaches this node: the ones each ancestor capture declares,
     * root first, then this node's own executable. They share ONE extraction pass over the tail, which
     * is why the same spelling declared twice along a path is refused at registration.
     */
    public Map<String, MultiArgumentos.FlagBinding> getAccumulatedFlagExtractionBindings() {
        Map<String, MultiArgumentos.FlagBinding> accumulated = new LinkedHashMap<>();
        for (CommandNode capturingNode : getCapturingAncestry()) {
            accumulated.putAll(capturingNode.getCapture().getInterpreter().getFlagExtractionBindings());
        }
        if (executable != null){
            accumulated.putAll(executable.getFlagExtractionBindings());
        }
        return accumulated;
    }

    /** The declared side of {@link #getAccumulatedFlagExtractionBindings()} - what help and tab show. */
    public List<CMDMethodInterpreter.FlagBinding> getAccumulatedFlagBindings() {
        List<CMDMethodInterpreter.FlagBinding> accumulated = new ArrayList<>();
        for (CommandNode capturingNode : getCapturingAncestry()) {
            accumulated.addAll(capturingNode.getCapture().getInterpreter().getFlagBindings());
        }
        if (executable != null){
            accumulated.addAll(executable.getFlagBindings());
        }
        return accumulated;
    }

    /** Every capture between the root and this node, root first, this node's own capture last. */
    public List<CommandNode> getCapturingAncestry() {
        List<CommandNode> capturing = new ArrayList<>();
        for (CommandNode node = this; node != null; node = node.parent) {
            if (node.capture != null) {
                capturing.add(node);
            }
        }
        Collections.reverse(capturing);
        return ImmutableList.copyOf(capturing);
    }

    @Override
    public String toString() {
        return nodePath.isEmpty() ? getPrimaryLabel() : nodePath;
    }
}
