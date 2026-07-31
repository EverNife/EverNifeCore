package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * What every parse call answers, positional or contextual: who asked, about which argument, and what
 * the invocation had already produced by then. One vocabulary for both families.
 * <p>
 * The help line, the help context and the raw tokens are NOT here: the engine also serves a config
 * file, where none of the three exists.
 */
public interface IParseCall {

    @Nonnull FCommandSender getSender();

    /** How this argument is named in a message or a log - {@code "<player>"}, {@code "ItemStack"}. */
    @Nonnull String describeArgument();

    /** What the framework knows about the argument being resolved. */
    @Nonnull ArgInfo getArgInfo();

    /** The concrete path this dispatch walked, or the bare root label outside a dispatch. */
    @Nonnull CommandPath getPath();

    /** The most recent value of that type this invocation already resolved, whichever family produced it. */
    <T> @Nullable T previouslyParsed(@Nonnull Class<T> type);

    /**
     * The value of the parameter that declared exactly this name - the form to reach for when a method
     * resolves more than one value of the same type.
     */
    <T> @Nullable T previouslyParsed(@Nonnull String declaredName, @Nonnull Class<T> type);

    /**
     * What an ancestor node captured, by node path - the same key {@code @Arg.NodeCaptured} uses. A
     * path may capture the same type twice, so the node path is the identity, never the class. Null
     * outside a dispatch.
     */
    <T> @Nullable T captured(@Nonnull String nodePath, @Nonnull Class<T> type);

}
