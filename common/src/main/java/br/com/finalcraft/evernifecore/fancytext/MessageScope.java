package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import jakarta.annotation.Nullable;

/**
 * The command scope of the current thread: which command path is executing right now, if any.
 * Opened around a synchronous command invocation and read automatically by
 * {@link RenderContext#of}. A task spawned onto another thread from inside a command loses the
 * scope by definition - pass a {@link CommandMessageContext} explicitly in that case.
 */
public final class MessageScope implements AutoCloseable {

    private static final ThreadLocal<CommandMessageContext> CURRENT = new ThreadLocal<>();

    // A nested open() on the same thread must not close what the outer one still needs, so only the
    // scope that actually set the context clears it.
    private final boolean owner;

    private MessageScope(boolean owner) {
        this.owner = owner;
    }

    public static MessageScope open(@Nullable CommandPath path) {
        boolean owner = CURRENT.get() == null;
        if (owner) {
            CURRENT.set(new CommandMessageContext(path));
        }
        return new MessageScope(owner);
    }

    public static CommandMessageContext currentOrEmpty() {
        CommandMessageContext context = CURRENT.get();
        return context == null ? CommandMessageContext.EMPTY : context;
    }

    @Override
    public void close() {
        if (owner) {
            CURRENT.remove();
        }
    }
}
