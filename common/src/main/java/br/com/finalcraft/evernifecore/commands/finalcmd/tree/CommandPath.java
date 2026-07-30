package br.com.finalcraft.evernifecore.commands.finalcmd.tree;

import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The concrete line a sender typed down to the node that answered it: the root alias plus every
 * token the traversal consumed, split into what was written ({@link #getSegments()}) and which nodes
 * were walked ({@link #nodePath()}).
 * <p>
 * Immutable, and built fresh per dispatch - two players running the same command at the same time
 * each hold their own, so a rendered line never shows the other one's target.
 */
public final class CommandPath {

    private final String label;
    private final List<String> segments;
    private final List<String> literals;
    private final int lastLiteralIndex;

    /**
     * @param label            the root alias the sender actually typed
     * @param segments         every token the path consumed, as typed - matched labels AND capture tokens
     * @param literals         the PRIMARY label of each node walked below the root, so an alias never
     *                         changes the identity of the node (locale keys, registry entries)
     * @param lastLiteralIndex where in {@code segments} the deepest node's own label sits, or -1 at the
     *                         root. Everything after it belongs to that node's capture, which is what
     *                         tells {@link #parentJoined()} where the parent's line ends
     */
    public CommandPath(@Nonnull String label, @Nonnull List<String> segments, @Nonnull List<String> literals, int lastLiteralIndex) {
        this.label = label;
        this.segments = ImmutableList.copyOf(segments);
        this.literals = ImmutableList.copyOf(literals);
        this.lastLiteralIndex = lastLiteralIndex;
    }

    public static CommandPath ofRoot(@Nonnull String label) {
        return new CommandPath(label, ImmutableList.<String>of(), ImmutableList.<String>of(), -1);
    }

    /** A label plus one word below it, for a caller that names a command without having walked it. */
    public static CommandPath ofSingleSegment(@Nonnull String label, @Nonnull String segment) {
        return new CommandPath(label, ImmutableList.of(segment), ImmutableList.of(segment), 0);
    }

    public String getLabel() {
        return label;
    }

    public List<String> getSegments() {
        return segments;
    }

    public List<String> getLiterals() {
        return literals;
    }

    /** The typed tokens below the root, space joined - {@code "user Steve permission set"}. */
    public String joined() {
        return String.join(" ", segments);
    }

    /**
     * The same tokens minus everything the deepest node owns - {@code "user Steve permission"} for
     * {@code "user Steve permission set"}. A node's captured tokens go with it, so the parent line of
     * {@code "user Steve"} is the root's, which is empty.
     */
    public String parentJoined() {
        return lastLiteralIndex <= 0 ? "" : String.join(" ", segments.subList(0, lastLiteralIndex));
    }

    /** The whole line as typed, ready to be shown or click-suggested - {@code "/lp user Steve permission set"}. */
    public String full() {
        String joined = joined();
        return joined.isEmpty() ? "/" + label : "/" + label + " " + joined;
    }

    /** The last literal walked, primary spelling - feeds {@code ${subcmd}}; null at the root. */
    public @Nullable String lastLiteral() {
        return literals.isEmpty() ? null : literals.get(literals.size() - 1);
    }

    /**
     * The identity of the node this path reached: primary labels only, dot joined
     * ({@code "user.permission.set"}), with capture tokens left out. Empty at the root.
     */
    public String nodePath() {
        return String.join(".", literals);
    }

    /** How many nodes below the root this path walked. */
    public int depth() {
        return literals.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommandPath other = (CommandPath) o;
        return label.equals(other.label) && segments.equals(other.segments) && literals.equals(other.literals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, segments, literals);
    }

    @Override
    public String toString() {
        return full();
    }
}
