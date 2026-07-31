package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.DispatchContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * One call into an {@link ArgParserContextual}: who ran the command and everything around the
 * invocation. It is about no token at all, which is why nothing routes above it - there is a single
 * method and it is always the one asked.
 * <p>
 * Every question a {@link ParseCall} answers is answered here under the same name; the three below are
 * the ones only a command invocation has.
 */
public final class ContextualParseCall implements IParseCall {

    private final FCommandSender sender;
    private final ArgInfo argInfo;
    private final @Nullable DispatchContext dispatch;
    private final ResolvedArguments resolved;
    private final MultiArgumentos argumentos;
    private final @Nullable HelpContext helpContext;
    private final @Nullable HelpLine helpLine;

    /**
     * @param dispatch    the running dispatch, for {@link #getPath()} and {@link #captured}; null when the
     *                    parser is exercised outside one
     * @param helpContext the help of the command being run; null outside a dispatch, same as the two
     *                    above and for the same reason
     */
    public ContextualParseCall(@Nonnull FCommandSender sender,
                               @Nonnull ArgInfo argInfo,
                               @Nullable DispatchContext dispatch,
                               @Nonnull ResolvedArguments resolved,
                               @Nonnull MultiArgumentos argumentos,
                               @Nullable HelpContext helpContext,
                               @Nullable HelpLine helpLine) {
        this.sender = sender;
        this.argInfo = argInfo;
        this.dispatch = dispatch;
        this.resolved = resolved;
        this.argumentos = argumentos;
        this.helpContext = helpContext;
        this.helpLine = helpLine;
    }

    @Override
    public @Nonnull FCommandSender getSender() {
        return sender;
    }

    /**
     * The name the parameter declared, and the type when it declared none - which is the usual case,
     * since most contextual parameters carry no annotation at all.
     */
    @Override
    public @Nonnull String describeArgument() {
        String declaredName = argInfo.getArgData().getName();
        return declaredName.isEmpty() ? argInfo.getArgumentType().getSimpleName() : declaredName;
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

    /**
     * Every token of the executable's own window - the line as the method sees it, which is the line
     * AFTER the declared flags of the path were pulled out of it. A path that declares no flag at all
     * extracted nothing, so the window still holds every token as typed and
     * {@link MultiArgumentos#flagify()} on it still sniffs; either way, what is here is what the
     * method's positionals were read from.
     */
    public @Nonnull MultiArgumentos getArgumentos() {
        return argumentos;
    }

    /** The help of the command being run. Null only outside a dispatch. */
    public @Nullable HelpContext getHelpContext() {
        return helpContext;
    }

    /** The usage line of the method being run, already bound to the path. Null only outside a dispatch. */
    public @Nullable HelpLine getHelpLine() {
        return helpLine;
    }
}
