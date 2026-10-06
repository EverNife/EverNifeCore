package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.storage.config.BackendDefinition;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.manager.RefRegistry;

/**
 * Test engine: an {@link ECStorage} handle over a backend the test built itself - the one way to put a
 * {@code ScriptedStorage} under code that takes an {@code ECStorage}. Lives in this package because the handle's
 * constructor is package-private on purpose: production opens handles through {@code ECStorage.open}.
 */
public final class ECStorageHandles {

    private ECStorageHandles() {
    }

    /** A plugin-less handle over {@code backend}, which must already be {@code init()}ed; codecs are the in-memory definition's. */
    public static ECStorage over(Storage backend) {
        return new ECStorage(null, backend, BackendDefinition.memory(), new RefRegistry());
    }
}
