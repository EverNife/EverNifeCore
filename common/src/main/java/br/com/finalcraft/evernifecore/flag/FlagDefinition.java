package br.com.finalcraft.evernifecore.flag;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * A logic definition a flag value carries - an effect, a gate - with the node it was written as: the node is what is
 * stored and shown back, and what two values are compared by. A definition whose type no plugin registered yet is
 * pending ({@link #getPendingType()}); it is kept as written and reads once that type registers.
 */
public final class FlagDefinition<T> {

    private final JsonNode raw;
    private final T definition;
    private final PendingType pendingType;

    /** Answers the type a definition still waits for, or null once it reads. */
    public interface PendingType {
        @Nullable String get();
    }

    /** @param pendingType asked each time, since a pending definition resolves by itself when its type registers */
    public FlagDefinition(JsonNode raw, T definition, PendingType pendingType) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.pendingType = Objects.requireNonNull(pendingType, "pendingType");
    }

    /** The node as it was written. */
    public JsonNode getRaw() {
        return raw;
    }

    public T get() {
        return definition;
    }

    /** The type this definition waits for while no plugin registers it; null when it reads. */
    public @Nullable String getPendingType() {
        return pendingType.get();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FlagDefinition && raw.equals(((FlagDefinition<?>) other).raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public String toString() {
        return raw.toString();
    }
}
