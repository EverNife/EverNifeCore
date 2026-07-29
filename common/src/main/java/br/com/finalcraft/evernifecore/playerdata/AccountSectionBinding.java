package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * The RESOLVED storage wiring of an {@link AccountSection}: the network backend, the section's
 * collection and its {@link CachingManager}. Simpler than a PDSection's binding by design - the
 * whole account family shares the one backend configured under {@code network} and the cache
 * lifecycle is fixed. Produced by {@link AccountSectionEngine}; immutable.
 */
@Getter
@RequiredArgsConstructor
final class AccountSectionBinding<S extends AccountSection<S>> {

    private final AccountSectionConfiguration<S> configuration;
    private final String backendName;
    private final EntityDescriptor<UUID, S> descriptor;
    private final CachingManager<UUID, S> manager;

    Class<S> getSectionClass() {
        return configuration.getSectionClass();
    }

    ECPluginData getPluginData() {
        return configuration.getPluginData();
    }

    String getCollection() {
        return descriptor.collection();
    }

    Storage getStorage() {
        return manager.storage();
    }

    Repository<UUID, S> getRepository() {
        return manager.repository();
    }
}
