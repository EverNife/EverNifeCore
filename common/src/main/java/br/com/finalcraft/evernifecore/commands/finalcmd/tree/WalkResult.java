package br.com.finalcraft.evernifecore.commands.finalcmd.tree;

import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Where a traversal stopped, and how much of the line it ate to get there. It carries counts and raw
 * tokens only - no value was resolved, no parser ran, no message was sent.
 */
public final class WalkResult {

    public enum Outcome {
        /** The node at the end of the path runs; everything after {@link #getConsumed()} is its arguments. */
        EXECUTABLE,
        /** The path ended on a branch that has no executable of its own: print its help. */
        NODE_HELP,
        /** A reserved word ({@code help}, {@code ?}, {@code ajuda}) asked for the node's help explicitly. */
        RESERVED_HELP,
        /** A token had to be a child's label and matched none. */
        NO_MATCH,
        /** A flag token appeared before the path ended, which is the one place a flag may not be. */
        FLAG_TOO_EARLY,
    }

    private final CommandNode node;
    private final int consumed;
    private final List<String> captureTokens;
    private final List<CommandNode> pathNodes;
    private final CommandPath path;
    private final Outcome outcome;
    private final @Nullable CaptureBinding pendingCapture;
    private final @Nullable String offendingToken;

    public WalkResult(@Nonnull CommandNode node,
                      int consumed,
                      @Nonnull List<String> captureTokens,
                      @Nonnull List<CommandNode> pathNodes,
                      @Nonnull CommandPath path,
                      @Nonnull Outcome outcome,
                      @Nullable CaptureBinding pendingCapture,
                      @Nullable String offendingToken) {
        this.node = node;
        this.consumed = consumed;
        this.captureTokens = ImmutableList.copyOf(captureTokens);
        this.pathNodes = ImmutableList.copyOf(pathNodes);
        this.path = path;
        this.outcome = outcome;
        this.pendingCapture = pendingCapture;
        this.offendingToken = offendingToken;
    }

    public CommandNode getNode() {
        return node;
    }

    /** How many tokens the path ate - literals plus every capture token. */
    public int getConsumed() {
        return consumed;
    }

    /** The raw token of every capture on the path, in order, flattened across multi-token captures. */
    public List<String> getCaptureTokens() {
        return captureTokens;
    }

    /** Every node walked below the root, in order - what a permission or validation check iterates. */
    public List<CommandNode> getPathNodes() {
        return pathNodes;
    }

    public CommandPath getPath() {
        return path;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    /**
     * The capture the traversal ran out of tokens inside, if any. Tab-complete needs it to know the
     * word being typed is a captured value and not the next label.
     */
    public @Nullable CaptureBinding getPendingCapture() {
        return pendingCapture;
    }

    /** The token that could not be matched, or the flag token found too early. */
    public @Nullable String getOffendingToken() {
        return offendingToken;
    }
}
