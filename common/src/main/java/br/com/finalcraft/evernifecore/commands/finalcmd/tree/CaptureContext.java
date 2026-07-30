package br.com.finalcraft.evernifecore.commands.finalcmd.tree;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What every capture of the current path produced, keyed by the node path that produced it: the
 * context object each capture returned, and - addressed by {@code "path:<argName>"} - the individual
 * tokens each one parsed on the way there.
 * <p>
 * Keyed by path and not by class on purpose: one line can capture the same type twice
 * ({@code /lp user Steve compare Alex diff}), and a map by class would overwrite the first silently
 * and hand the second one out twice.
 */
public final class CaptureContext {

    private final Map<String, Object> capturedByNodePath = new LinkedHashMap<>();
    private final Map<String, Object> argsByNodePath = new LinkedHashMap<>();

    public void put(@Nonnull String nodePath, @Nullable Object value) {
        capturedByNodePath.put(nodePath, value);
    }

    /** One parsed token of a capture, so a leaf can read it without the capture wrapping it in an object. */
    public void putArg(@Nonnull String nodePath, @Nonnull String argName, @Nullable Object value) {
        argsByNodePath.put(argKey(nodePath, argName), value);
    }

    public <T> @Nullable T captured(@Nonnull String nodePath, @Nonnull Class<T> type) {
        Object value = capturedByNodePath.get(nodePath);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    public <T> @Nullable T capturedArg(@Nonnull String nodePath, @Nonnull String argName, @Nonnull Class<T> type) {
        Object value = argsByNodePath.get(argKey(nodePath, argName));
        return type.isInstance(value) ? type.cast(value) : null;
    }

    public boolean isEmpty() {
        return capturedByNodePath.isEmpty() && argsByNodePath.isEmpty();
    }

    private static String argKey(String nodePath, String argName) {
        return nodePath + CapturedBinding.ARG_SEPARATOR + argName;
    }
}
