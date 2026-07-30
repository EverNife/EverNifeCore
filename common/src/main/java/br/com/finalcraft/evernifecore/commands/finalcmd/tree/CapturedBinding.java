package br.com.finalcraft.evernifecore.commands.finalcmd.tree;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * A {@code @Arg.NodeCaptured} parameter, resolved at registration to the exact ancestor node whose
 * capture feeds it - and, when the parameter asks for one, to the single {@code @Arg} of that capture
 * it reads. Resolving here (instead of at dispatch) is what turns the only ambiguous case - two
 * compatible captures on the same path - into a registration error that names the paths to pick from,
 * and what makes renaming a capture's {@code @Arg} break at boot instead of at the far leaf that read it.
 */
public final class CapturedBinding {

    /** What separates the node path from the argument name in {@code "user:<server>"}. */
    public static final String ARG_SEPARATOR = ":";

    private final int paramIndex;
    private final String nodePath;
    private final @Nullable String argName;
    private final Class<?> type;

    public CapturedBinding(int paramIndex, @Nonnull String nodePath, @Nullable String argName, @Nonnull Class<?> type) {
        this.paramIndex = paramIndex;
        this.nodePath = nodePath;
        this.argName = argName;
        this.type = type;
    }

    public int getParamIndex() {
        return paramIndex;
    }

    /** The ancestor whose capture this parameter reads, as a dot path of primary labels. */
    public String getNodePath() {
        return nodePath;
    }

    /**
     * The declared name of the single capture argument this parameter reads ({@code "<server>"}), or
     * null when it reads the capture's own return value - the common case.
     */
    public @Nullable String getArgName() {
        return argName;
    }

    public Class<?> getType() {
        return type;
    }
}
