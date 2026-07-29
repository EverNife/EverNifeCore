package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.manager.sync.SyncBindGuard;

import java.util.List;

/**
 * Adapts the library's bind-time optimistic-lock guard to storage.yml: it sources the
 * multi-instance signal from the parsed config and turns a would-be rejection into a WARNING that
 * points at the knob that caused it, plus the softer network-family warning the library has no
 * notion of.
 *
 * <p>This never fails the boot. The library's {@link SyncBindGuard} throws on versioned entity +
 * non-enforcing backend + multi-instance intent; here that intent only exists with an enabled redis
 * block, so the real case it catches is a versioned entity on a shared non-enforcing backend (h2 over
 * TCP). It warns rather than aborts because the common non-enforcing backends (groupedfile,
 * localfile) are single-application by nature.
 *
 * <p>The network family warns on a weaker signal still - its rows are written by every server, but a
 * network of one is legitimate. Absent both signals the guard is a no-op.
 */
public final class PdSyncBindGuard {

    private PdSyncBindGuard() {
    }

    /**
     * Warns when a versioned entity is routed to a backend that cannot enforce the lock WHILE the
     * config declares multi-instance intent (an enabled redis block); also soft-warns when the only
     * signal is the entity belonging to the network family. Never throws - always binds.
     *
     * @param what          a human-readable id of the entity being bound (for the message)
     * @param descriptor    the resolved descriptor (source of the versioned signal)
     * @param storage       the backend the entity resolved to
     * @param parsed        the storage config (source of the multi-instance-intent signal)
     * @param networkFamily whether the entity lives on the network backend, and so is written by every
     *                      server of the network - not whether it keys by accountId
     * @param warnings      the resolution warning sink
     */
    public static void check(String what, EntityDescriptor<?, ?> descriptor, Storage storage,
                             ParsedStorageConfig parsed, boolean networkFamily, List<String> warnings) {
        try {
            SyncBindGuard.check(what, descriptor, storage, parsed.isMultiInstanceIntent());
        } catch (IllegalStateException wouldReject) {
            warnings.add(wouldReject.getMessage()
                    + " Here that intent comes from an enabled redis block in storage.yml"
                    + " (multi-server-cache-sync.redis): route the entity to a backend of type"
                    + " sql | postgresql | mongo, or disable the redis block.");
        }
        if (networkFamily && descriptor.isVersioned() && !storage.enforcesOptimisticLock()) {
            warnings.add(what + " lives on the network backend (every server of the network writes it)"
                    + " but that backend does not enforce the optimistic lock: concurrent cross-server"
                    + " writes would silently drop one side. Fine while the network is one server; for a"
                    + " real one, point 'network.storage-backend-id' at sql | postgresql | mongo.");
        }
    }
}
