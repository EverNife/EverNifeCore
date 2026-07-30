package br.com.finalcraft.evernifecore.commands.finalcmd.tree;

import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Everything one dispatch shares between the captures of the path and the executable at its end: the
 * line that was typed, the tokens left after the path (with the declared flags already pulled out of
 * them) and what each capture produced.
 * <p>
 * The flag source is separate from the positional window because they are not the same tokens: flags
 * are extracted once, from the tail, with the bindings of the whole path - while each capture reads
 * its own {@code k} tokens from inside the path.
 */
public final class DispatchContext {

    private final String label;
    private final CommandPath path;
    private final MultiArgumentos flagSource;
    private final CaptureContext captures = new CaptureContext();

    public DispatchContext(@Nonnull String label, @Nonnull CommandPath path, @Nonnull MultiArgumentos flagSource) {
        this.label = label;
        this.path = path;
        this.flagSource = flagSource;
    }

    public String getLabel() {
        return label;
    }

    public CommandPath getPath() {
        return path;
    }

    /** The tail of the line, with every declared flag of the path already extracted out of it. */
    public MultiArgumentos getFlagSource() {
        return flagSource;
    }

    public CaptureContext getCaptures() {
        return captures;
    }

    public <T> @Nullable T captured(@Nonnull String nodePath, @Nonnull Class<T> type) {
        return captures.captured(nodePath, type);
    }

    /** A single token an ancestor's capture parsed, for a parameter that wants it instead of the context object. */
    public <T> @Nullable T capturedArg(@Nonnull String nodePath, @Nonnull String argName, @Nonnull Class<T> type) {
        return captures.capturedArg(nodePath, argName, type);
    }
}
