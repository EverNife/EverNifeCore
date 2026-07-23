package br.com.finalcraft.evernifecore.storage;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Raised when one or more ENABLED storage backends could not be contacted. Unlike the other
 * {@link StorageConfigException}s - which mean "storage.yml is wrong" - this one means "storage.yml
 * is right, the database is not answering", so it carries every failure at once (an admin fixing
 * them one reboot at a time is the failure mode this replaces).
 *
 * <p>The MESSAGE stays one short line, so the platform's own "error while enabling" log does not bury
 * it; the admin-facing banner is rendered from this data by {@link StorageBootReport}.</p>
 *
 * <p>{@link #getUsages()} and {@link #getStorageYmlFile()} are empty/{@code null} until
 * {@link StorageBootReport#enrich} attaches the storage.yml context - only the {@code PlayerController}
 * constructor has that context in scope.</p>
 */
public class StorageUnavailableException extends StorageConfigException {

    private final List<StorageInitFailure> failures;
    private final Map<String, List<String>> usages;   // empty until enrich(): the registry has no config
    private final File storageYmlFile;                // null until enrich()

    public StorageUnavailableException(String message, List<StorageInitFailure> failures,
                                       Map<String, List<String>> usages, File storageYmlFile) {
        super(message, failures.isEmpty() ? null : failures.get(0).getCause());
        this.failures = Collections.unmodifiableList(failures);
        this.usages = Collections.unmodifiableMap(usages);
        this.storageYmlFile = storageYmlFile;
    }

    public List<StorageInitFailure> getFailures() {
        return failures;
    }

    /** backendName -> the storage.yml keys routing data to it; empty when the context is unknown. */
    public Map<String, List<String>> getUsages() {
        return usages;
    }

    /** The storage.yml to point the admin at, or {@code null} outside the PlayerController path. */
    public File getStorageYmlFile() {
        return storageYmlFile;
    }
}
