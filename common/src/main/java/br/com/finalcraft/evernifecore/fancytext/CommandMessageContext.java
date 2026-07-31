package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * Command-scope data available while a command is executing: the whole path the sender typed, which
 * is what {@code ${label}}, {@code ${path}}, {@code ${parentpath}} and {@code ${subcmd}} read.
 * {@link #EMPTY} when there is no such scope, for instance on an asynchronous send.
 */
public final class CommandMessageContext {

    public static final CommandMessageContext EMPTY = new CommandMessageContext(null);

    private final @Nullable CommandPath path;

    public CommandMessageContext(@Nullable CommandPath path) {
        this.path = path;
    }

    /**
     * A context built by hand, for the caller who has to say which command a message belongs to
     * because the thread delivering it is not the one that ran the command.
     */
    public static CommandMessageContext of(@Nullable CommandPath path) {
        return new CommandMessageContext(path);
    }

    /**
     * The same, for a caller that only knows the two words - the sub-command is taken as the single
     * segment below the label.
     */
    public static CommandMessageContext of(@Nullable String label, @Nullable String subCommandName) {
        if (label == null){
            return EMPTY;
        }
        return new CommandMessageContext(subCommandName == null || subCommandName.isEmpty()
                ? CommandPath.ofRoot(label)
                : CommandPath.ofSingleSegment(label, subCommandName));
    }

    public @Nullable CommandPath getPath() {
        return path;
    }

    public @Nullable String getLabel() {
        return path == null ? null : path.getLabel();
    }

    public @Nullable String getSubCommandName() {
        return path == null ? null : path.lastLiteral();
    }

    /** Every token below the label, space joined; null outside a command scope. */
    public @Nullable String getPathText() {
        return path == null ? null : path.joined();
    }

    /** The same minus what the deepest node owns; null outside a command scope. */
    public @Nullable String getParentPathText() {
        return path == null ? null : path.parentJoined();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommandMessageContext other = (CommandMessageContext) o;
        return Objects.equals(path, other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path);
    }
}
