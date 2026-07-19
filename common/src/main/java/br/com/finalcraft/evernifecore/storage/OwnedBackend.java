package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.codec.Codec;

import java.util.concurrent.CompletableFuture;

/**
 * The handle {@link ECStorage#openBackend} hands back for a plugin-OWNED inline backend: the live
 * {@link Storage} paired with the {@link BackendDefinition} it was opened from, so the owner can derive
 * a config-driven default {@link Codec} ({@link #defaultCodec(Class)}) and later {@link #close()} it.
 *
 * <p>It deliberately does NOT implement {@link Storage}: a delegating wrapper would hide the concrete
 * storage's own interfaces (e.g. {@code SchemaAwareStorage}/{@code TransactionalStorage}) from an
 * {@code instanceof} check on the returned object. Callers that need the raw storage take it from
 * {@link #storage()}.</p>
 */
public final class OwnedBackend {

    private final Storage storage;
    private final BackendDefinition definition;

    OwnedBackend(Storage storage, BackendDefinition definition) {
        this.storage = storage;
        this.definition = definition;
    }

    /** The live, initialized storage - use it to resolve repositories. */
    public Storage storage() {
        return storage;
    }

    /** The backend definition this storage was opened from (its type and, for file backends, format). */
    public BackendDefinition definition() {
        return definition;
    }

    /**
     * The default codec for {@code type} on this backend, so a plugin's entity honours the configured
     * format exactly as PlayerData does: a file backend picks YAML or (pretty) JSON from its
     * {@code format}, every other backend uses compact JSON. See {@link BackendDefinition#defaultCodec(Class)}.
     */
    public <V> Codec<V> defaultCodec(Class<V> type) {
        return definition.defaultCodec(type);
    }

    /** Closes the owned storage; call it when your plugin disables. */
    public CompletableFuture<Void> close() {
        return storage.close();
    }
}
