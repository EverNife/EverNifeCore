package br.com.finalcraft.evernifecore.storage;

/**
 * Thrown on hard storage configuration errors: reference to an unknown backend,
 * a disabled backend in use, an invalid collection name, a collection clash, a
 * malformed storage.yml, etc.
 *
 * <p>These are fail-fast errors: they should abort the bootstrap (or the PDSection
 * registration) with a message telling the admin exactly what to fix in storage.yml.</p>
 */
public class StorageConfigException extends RuntimeException {

    public StorageConfigException(String message) {
        super(message);
    }

    public StorageConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
