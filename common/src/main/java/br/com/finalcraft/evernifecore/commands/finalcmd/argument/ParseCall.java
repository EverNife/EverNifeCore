package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.DispatchContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * One call into an {@link ArgParser}: who typed it, what token (if any) it is about, and everything
 * around it the parser may want.
 * <p>
 * It is one object instead of three parameters so that giving parsers more context later is an
 * addition, not a signature break in every third-party parser in existence.
 */
public final class ParseCall implements IParseCall {

    private final FCommandSender sender;
    private final Argumento argumento;
    private final ArgInfo argInfo;
    private final @Nullable DispatchContext dispatch;
    private final ResolvedArguments resolved;
    private final boolean flagValue;

    /**
     * @param argumento the token this call is about; EMPTY when the parser is being asked to answer
     *                  without one ({@link ArgParser#absent} / {@link ArgParser#fromSender})
     * @param dispatch  the running dispatch, for {@link #getPath()} and {@link #captured}; null when the
     *                  parser is exercised outside one
     */
    public ParseCall(@Nonnull FCommandSender sender,
                     @Nonnull Argumento argumento,
                     @Nonnull ArgInfo argInfo,
                     @Nullable DispatchContext dispatch,
                     @Nonnull ResolvedArguments resolved,
                     boolean flagValue) {
        this.sender = sender;
        this.argumento = argumento;
        this.argInfo = argInfo;
        this.dispatch = dispatch;
        this.resolved = resolved;
        this.flagValue = flagValue;
    }

    /** The same call about a different token - what a parser that delegates to another one needs. */
    public ParseCall withArgumento(@Nonnull Argumento other) {
        return new ParseCall(sender, other, argInfo, dispatch, resolved, flagValue);
    }

    @Override
    public @Nonnull FCommandSender getSender() {
        return sender;
    }

    /** The {@code @Arg} name as declared - the only handle a message has on which argument failed. */
    @Override
    public @Nonnull String describeArgument() {
        return argInfo.getArgData().getName();
    }

    /** The token being converted. Empty means there is none - never a reason to message the sender. */
    public @Nonnull Argumento getArgumento() {
        return argumento;
    }

    @Override
    public @Nonnull ArgInfo getArgInfo() {
        return argInfo;
    }

    @Override
    public @Nonnull CommandPath getPath() {
        return dispatch == null ? CommandPath.ofRoot("") : dispatch.getPath();
    }

    @Override
    public <T> @Nullable T captured(@Nonnull String nodePath, @Nonnull Class<T> type) {
        return dispatch == null ? null : dispatch.captured(nodePath, type);
    }

    @Override
    public <T> @Nullable T previouslyParsed(@Nonnull Class<T> type) {
        return resolved.get(type);
    }

    @Override
    public <T> @Nullable T previouslyParsed(@Nonnull String declaredName, @Nonnull Class<T> type) {
        return resolved.get(declaredName, type);
    }

    /** Whether this token is a flag's value (or its {@code def()}) rather than a positional. */
    public boolean isFlagValue() {
        return flagValue;
    }
}
