package br.com.finalcraft.evernifecore.fancytext;

import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * Command-scope data available while a command is executing: which label and sub-command the player
 * typed. {@link #EMPTY} when there is no such scope, for instance on an asynchronous send.
 */
public final class MessageContext {

    public static final MessageContext EMPTY = new MessageContext(null, null);

    private final String label;
    private final String subCommandName;

    public MessageContext(@Nullable String label, @Nullable String subCommandName) {
        this.label = label;
        this.subCommandName = subCommandName;
    }

    /**
     * A context built by hand, for the caller who has to say which command a message belongs to
     * because the thread delivering it is not the one that ran the command.
     */
    public static MessageContext of(@Nullable String label, @Nullable String subCommandName) {
        return new MessageContext(label, subCommandName);
    }

    public @Nullable String getLabel() {
        return label;
    }

    public @Nullable String getSubCommandName() {
        return subCommandName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageContext other = (MessageContext) o;
        return Objects.equals(label, other.label) && Objects.equals(subCommandName, other.subCommandName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, subCommandName);
    }
}
