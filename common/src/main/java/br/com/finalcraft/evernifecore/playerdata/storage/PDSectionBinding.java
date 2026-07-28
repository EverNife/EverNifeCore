package br.com.finalcraft.evernifecore.playerdata.storage;

import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.manager.CachingManager;
import lombok.Getter;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The RESOLVED storage wiring of a PDSection:
 * dev configuration against the admin's storage.yml, materialized into a concrete
 * backend, collection and a per-class {@link CachingManager}
 * (the cache + repository façade replacing the old hand-rolled per-player cache).
 *
 * <p>Produced by {@link BindingResolver}; immutable.</p>
 */
@Getter
public final class PDSectionBinding<S extends PDSection> {

    private final PDSectionConfiguration<S> configuration;
    private final String backendName;
    private final Storage storage;
    private final EntityDescriptor<UUID, S> descriptor;
    /** The per-class cache + repository façade backing this section. */
    private final CachingManager<UUID, S> manager;
    /** When cells enter and leave memory - admin over developer, already resolved. */
    private final SectionLifecycle lifecycle;
    /** How long a cell survives after its owner goes offline - admin over developer, already resolved. */
    private final Duration idleGrace;
    /** Minor issues found during resolution (e.g. admin outside the suggested backends). */
    private final List<String> resolutionWarnings;

    PDSectionBinding(PDSectionConfiguration<S> configuration, String backendName, Storage storage,
                     EntityDescriptor<UUID, S> descriptor, CachingManager<UUID, S> manager,
                     SectionLifecycle lifecycle, Duration idleGrace, List<String> resolutionWarnings) {
        this.configuration = configuration;
        this.backendName = backendName;
        this.storage = storage;
        this.descriptor = descriptor;
        this.manager = manager;
        this.lifecycle = lifecycle;
        this.idleGrace = idleGrace;
        this.resolutionWarnings = Collections.unmodifiableList(resolutionWarnings);
    }

    public Class<S> getPdSectionClass() {
        return configuration.getPdSectionClass();
    }



    public String getCollection() {
        return descriptor.collection();
    }

    /** The underlying repository (uncached) - same instance the manager wraps. */
    public Repository<UUID, S> getRepository() {
        return manager.repository();
    }
}
