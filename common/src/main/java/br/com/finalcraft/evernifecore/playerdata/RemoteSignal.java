package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.modules.groupedfile.GroupedFileStorage;
import br.com.finalcraft.everydatabase.modules.localfile.LocalFileStorage;
import br.com.finalcraft.everydatabase.modules.memory.InMemoryStorage;
import br.com.finalcraft.everydatabase.modules.sql.h2.H2SqlStorage;

/**
 * How THIS server learns that another server of the network wrote a section's row - what a plugin running
 * on several servers reads to decide how careful it must be ({@link PlayerController#getRemoteSignal(Class)}).
 */
public enum RemoteSignal {

    /** A backend no other server shares (files, H2, memory): there is nobody to hear from. */
    NONE,

    /**
     * A shared network backend that no cache-sync transport covers: another server's write is only noticed
     * when this server's next flush of that row conflicts ({@link StoredSection#onRemoteChange} never fires).
     */
    CONFLICT_ONLY,

    /** A cache-sync transport (Redis or the backend's native feed) covers the collection: remote writes arrive live. */
    LIVE;

    /** {@code covered}: a running cache-sync is bound to the collection kept in {@code storage}. */
    static RemoteSignal of(boolean covered, Storage storage) {
        if (covered) return LIVE;
        boolean local = storage instanceof LocalFileStorage
                || storage instanceof GroupedFileStorage
                || storage instanceof InMemoryStorage
                || storage instanceof H2SqlStorage;
        return local ? NONE : CONFLICT_ONLY;
    }
}
