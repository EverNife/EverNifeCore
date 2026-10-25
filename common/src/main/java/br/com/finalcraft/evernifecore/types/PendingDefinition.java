package br.com.finalcraft.evernifecore.types;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A definition whose type was not registered when it was read ({@link TypeRegistry#parseLater}). The raw node is kept
 * as written; when a type with that name registers, the registry reads it again and, if it reads, every
 * {@link #whenResolved} callback runs once with the value. One that still does not read stays pending with the cause.
 * Already-resolved definitions come back as an instance whose {@link #isResolved()} is true from the start.
 */
public final class PendingDefinition<T> {

    private final ECPluginData caller;
    private final JsonNode raw;
    private final ZoneId zone;
    private @Nullable String missingType;
    private String cause;
    private @Nullable T value;
    private final List<Consumer<T>> callbacks = new ArrayList<>();

    PendingDefinition(ECPluginData caller, JsonNode raw, ZoneId zone, @Nullable String missingType, String cause, @Nullable T value) {
        this.caller = caller;
        this.raw = raw;
        this.zone = zone;
        this.missingType = missingType;
        this.cause = cause;
        this.value = value;
    }

    /** The node as it was written. */
    public JsonNode getRaw() {
        return raw;
    }

    /** The plugin whose definition this is; its {@code unregisterAll} drops the pending entry. */
    public ECPluginData getCaller() {
        return caller;
    }

    /** The type name the node wrote and nobody registered; null once resolved. */
    public synchronized @Nullable String getMissingType() {
        return missingType;
    }

    /** Why this definition is still pending - the absent type, or why it did not read when the type arrived; null once resolved. */
    public synchronized @Nullable String getCause() {
        return value == null ? cause : null;
    }

    public synchronized boolean isResolved() {
        return value != null;
    }

    /** The value, once the type arrived and the node read. */
    public synchronized Optional<T> get() {
        return Optional.ofNullable(value);
    }

    /** Runs {@code callback} with the value: now when already resolved, else once when it resolves. */
    public void whenResolved(Consumer<T> callback) {
        T resolved;
        synchronized (this) {
            if (value == null) {
                callbacks.add(callback);
                return;
            }
            resolved = value;
        }
        callback.accept(resolved);
    }

    ZoneId getZone() {
        return zone;
    }

    synchronized void keepPending(String why) {
        this.cause = why;
    }

    void resolve(T resolved) {
        List<Consumer<T>> toRun;
        synchronized (this) {
            if (value != null) {
                return;
            }
            value = resolved;
            missingType = null;
            toRun = new ArrayList<>(callbacks);
            callbacks.clear();
        }
        for (Consumer<T> callback : toRun) {
            callback.accept(resolved);
        }
    }

    @Override
    public synchronized String toString() {
        return value != null ? "resolved " + value : "pending (" + missingType + "): " + raw;
    }
}
