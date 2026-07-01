package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.manager.sync.SyncBindGuard;

import java.util.List;

/**
 * Adapts the library's bind-time optimistic-lock guard to storage.yml: it sources the
 * multi-instance signal from the parsed config and turns a would-be rejection into a WARNING that
 * points at the knob that caused it, plus the softer account-family warning the library has no
 * notion of.
 *
 * <p>This never fails the boot. The library's {@link SyncBindGuard} throws on the one fatal
 * combination (versioned entity + non-enforcing backend + multi-instance intent); here that intent
 * only exists when an enabled redis block is present, so the sole real-world case it catches is a
 * versioned entity on a shared, non-enforcing backend (h2 over TCP). Warning instead of aborting is
 * a deliberate choice: the far more common non-enforcing backends (groupedfile, localfile) are
 * single-application by nature, so a hard boot failure would punish a harmless default.</p>
 *
 * <p>The account family carries a weaker signal still: it exists for linked identities that MAY
 * span servers, but a single-server setup is legitimate - so without declared sync intent it only
 * warns. Absent both signals the guard is a no-op: the factory default (groupedfile) is
 * non-enforcing and every entity is versioned, so warning unconditionally would be pure noise.</p>
 */
public final class PdSyncBindGuard {

    private PdSyncBindGuard() {
    }

    /**
     * Warns when a versioned entity is routed to a backend that cannot enforce the lock WHILE the
     * config declares multi-instance intent (an enabled redis block); also soft-warns when the only
     * signal is the entity belonging to the account family. Never throws - always binds.
     *
     * @param what          a human-readable id of the entity being bound (for the message)
     * @param descriptor    the resolved descriptor (source of the versioned signal)
     * @param storage       the backend the entity resolved to
     * @param parsed        the storage config (source of the multi-instance-intent signal)
     * @param accountFamily whether the entity keys by accountId (linked identities may span servers)
     * @param warnings      the resolution warning sink
     */
    public static void check(String what, EntityDescriptor<?, ?> descriptor, Storage storage,
                             ParsedStorageConfig parsed, boolean accountFamily, List<String> warnings) {
        try {
            SyncBindGuard.check(what, descriptor, storage, parsed.isMultiInstanceIntent());
        } catch (IllegalStateException wouldReject) {
            warnings.add(wouldReject.getMessage()
                    + " Here that intent comes from an enabled redis block in storage.yml"
                    + " (multi-server-cache-sync.redis): route the entity to a backend of type"
                    + " sql | postgresql | mongo, or disable the redis block.");
        }
        if (accountFamily && descriptor.isVersioned() && !storage.enforcesOptimisticLock()) {
            warnings.add(what + " belongs to the account family (linked identities may write from several"
                    + " servers) but its backend does not enforce the optimistic lock: concurrent"
                    + " cross-server writes would silently drop one side. Fine on a single server; for a"
                    + " network, route it to sql | postgresql | mongo.");
        }
    }
}
