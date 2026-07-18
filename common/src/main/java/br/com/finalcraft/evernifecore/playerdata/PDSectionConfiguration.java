package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchema;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrationMode;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaStep;
import br.com.finalcraft.evernifecore.playerdata.storage.SectionCachePolicy;
import br.com.finalcraft.evernifecore.storage.BackendType;
import br.com.finalcraft.everydatabase.codec.Codec;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Developer-side configuration of a PDSection.
 *
 * <p>The developer ADVISES (default/suggested backends, default cache policy,
 * legacy adapter); the admin DECIDES through storage.yml
 * (resolution chain). The one HARD constraint the developer can impose is
 * {@link Builder#allowedBackendTypes(BackendType...)} - e.g. an ephemeral section
 * that must never be persisted to a database, only an in-memory backend.</p>
 *
 * <p>Always built through {@link #builder(ECPluginData, Class)}.</p>
 */
@Getter
public class PDSectionConfiguration<S extends PDSection> {

    private final ECPluginData pluginData;
    private final Class<S> pdSectionClass;
    /** Exposed through {@link #shouldHotLoad()} (not a {@code get}-prefixed accessor). */
    @Getter(AccessLevel.NONE)
    private final boolean shouldHotLoad;

    // storage guidance (all optional - nullable means "use the default from the resolution chain")
    /** Nullable - the default collection name is derived. */
    private final String collection;
    /** Nullable - falls back to the 'default-backend' from storage.yml. */
    private final String defaultBackend;
    /** Never null (may be empty). Becomes the generated 'Recommended Backend Types' comment. */
    private final List<String> suggestedBackends;
    /**
     * Never null (may be empty). When non-empty, the resolved backend's {@link BackendType} MUST be
     * one of these - a hard developer constraint the resolver enforces (a fatal error otherwise).
     */
    private final List<BackendType> allowedBackendTypes;
    /** Nullable - developer override of the codec resolution. */
    private final Codec<S> codec;
    /**
     * The developer-declared cache lifecycle ({@link SectionCachePolicy}) - never null; defaults to
     * {@link SectionCachePolicy#resident()}. An admin {@code cache:} override in storage.yml still
     * wins over it.
     */
    private final SectionCachePolicy sectionCachePolicy;
    /** How eagerly this section's cache is warmed at bind time. Never null; defaults to {@link SectionCachePolicy.Warmup#NONE}. */
    private final SectionCachePolicy.Warmup warmup;
    /** Nullable - when absent, there is no legacy YAML migration for this section. */
    private final String legacyYamlRootKey;
    private final Function<ConfigSection, S> legacyYamlAdapter;
    /**
     * The schema-migration chain for this section (never null; may be empty). Ordered: entry i upgrades
     * version {@code (i + 1)} to {@code (i + 2)}. Registered with the framework (before the section binds)
     * by {@code PlayerController.registerPDSectionCfg}.
     */
    private final List<EntitySchemaMigrations.Step> migrations;

    private PDSectionConfiguration(ECPluginData pluginData, Class<S> pdSectionClass, boolean shouldHotLoad,
                                   String collection, String defaultBackend, List<String> suggestedBackends,
                                   List<BackendType> allowedBackendTypes, Codec<S> codec,
                                   SectionCachePolicy sectionCachePolicy, SectionCachePolicy.Warmup warmup,
                                   String legacyYamlRootKey, Function<ConfigSection, S> legacyYamlAdapter,
                                   List<EntitySchemaMigrations.Step> migrations) {
        this.pluginData = pluginData;
        this.pdSectionClass = pdSectionClass;
        this.shouldHotLoad = shouldHotLoad;
        this.collection = collection;
        this.defaultBackend = defaultBackend;
        this.suggestedBackends = Collections.unmodifiableList(suggestedBackends);
        this.allowedBackendTypes = Collections.unmodifiableList(allowedBackendTypes);
        this.codec = codec;
        this.sectionCachePolicy = sectionCachePolicy;
        this.warmup = warmup;
        this.legacyYamlRootKey = legacyYamlRootKey;
        this.legacyYamlAdapter = legacyYamlAdapter;
        this.migrations = Collections.unmodifiableList(migrations);
    }

    public static <S extends PDSection> Builder<S> builder(ECPluginData pluginData, Class<S> pdSectionClass) {
        return new Builder<>(pluginData, pdSectionClass);
    }

    public boolean shouldHotLoad() {
        return shouldHotLoad;
    }

    // ---------------------------------------------------------------------

    public static class Builder<S extends PDSection> {

        private final ECPluginData pluginData;
        private final Class<S> pdSectionClass;
        private boolean hotLoad = true;
        private String collection;
        private String defaultBackend;
        private List<String> suggestedBackends = Collections.emptyList();
        private List<BackendType> allowedBackendTypes = Collections.emptyList();
        private Codec<S> codec;
        private SectionCachePolicy sectionCachePolicy = SectionCachePolicy.resident();
        private SectionCachePolicy.Warmup warmup = SectionCachePolicy.Warmup.NONE;
        private String legacyYamlRootKey;
        private Function<ConfigSection, S> legacyYamlAdapter;
        private final List<EntitySchemaMigrations.Step> migrations = new ArrayList<>();

        private Builder(ECPluginData pluginData, Class<S> pdSectionClass) {
            this.pluginData = pluginData;
            this.pdSectionClass = pdSectionClass;
        }

        public Builder<S> hotLoad(boolean hotLoad) {
            this.hotLoad = hotLoad;
            return this;
        }

        public Builder<S> collection(String collection) {
            this.collection = collection;
            return this;
        }

        public Builder<S> defaultBackend(String defaultBackend) {
            this.defaultBackend = defaultBackend;
            return this;
        }

        public Builder<S> suggestedBackends(String... backendIds) {
            this.suggestedBackends = Arrays.asList(backendIds);
            return this;
        }

        /**
         * Hard-restricts this section to the given backend TYPES (an allowlist). When set, the
         * resolver fails fast if the admin configures the section on any other backend type - e.g.
         * {@code allowedBackendTypes(BackendType.MEMORY)} guarantees an ephemeral section is never
         * persisted to a database (it may only live on an in-memory backend).
         */
        public Builder<S> allowedBackendTypes(BackendType... types) {
            this.allowedBackendTypes = Arrays.asList(types);
            return this;
        }

        public Builder<S> codec(Codec<S> codec) {
            this.codec = codec;
            return this;
        }

        /**
         * The section's cache lifecycle (default {@link SectionCachePolicy#resident()}):
         * {@code resident()} (unbounded, stays cached), {@code lru(maxSize)} (bounded LRU),
         * {@code ttl(duration)} (freshness + scheduled purge), or {@code workingSet()}
         * (resident while online, evicted a short grace after quit). An admin {@code cache:}
         * override in storage.yml still wins over it.
         */
        public Builder<S> cache(SectionCachePolicy sectionCachePolicy) {
            this.sectionCachePolicy = sectionCachePolicy == null ? SectionCachePolicy.resident() : sectionCachePolicy;
            return this;
        }

        /**
         * How eagerly this section's cache is warmed at bind time (default
         * {@link SectionCachePolicy.Warmup#NONE} = lazy). {@code ALL} pre-loads the whole collection.
         */
        public Builder<S> warmup(SectionCachePolicy.Warmup warmup) {
            this.warmup = warmup == null ? SectionCachePolicy.Warmup.NONE : warmup;
            return this;
        }

        /**
         * Declares how to convert this section's legacy YAML subtree
         * ({@code <rootKey>:} inside the old per-player file) into the new POJO.
         * Sections without an adapter are not migrated.
         */
        public Builder<S> legacyYaml(String rootKey, Function<ConfigSection, S> adapter) {
            this.legacyYamlRootKey = rootKey;
            this.legacyYamlAdapter = adapter;
            return this;
        }

        /** Appends a {@link EntitySchemaMigrationMode#LAZY} schema-migration step. See {@link #migration(int, EntitySchemaMigrationMode, SectionSchemaStep)}. */
        public Builder<S> migration(int fromVersion, SectionSchemaStep step) {
            return migration(fromVersion, EntitySchemaMigrationMode.LAZY, step);
        }

        /**
         * Appends the schema-migration step that upgrades a stored payload FROM {@code fromVersion} to
         * {@code fromVersion + 1}. Steps form a contiguous chain starting at
         * {@link EntitySchema#INITIAL_SCHEMA_VERSION}; adding one bumps the section's current version.
         * The step mutates the payload as a file-less, type-aware {@link ConfigSection} before binding
         * (no legacy fields on the POJO), the same rich surface {@link #legacyYaml} uses. An
         * {@link EntitySchemaMigrationMode#EAGER} step additionally drives a boot-time full-collection sweep (see the
         * cascade note on {@link EntitySchemaMigrationMode}).
         */
        public Builder<S> migration(int fromVersion, EntitySchemaMigrationMode mode, SectionSchemaStep step) {
            EntitySchemaStep raw = node -> {
                // Host the raw tree in an in-memory, type-aware section so the step works the rich path
                // API; write the mutated tree back into the SAME node the migration runner reads.
                ConfigSection section = ConfigFactory.inMemorySection(node);
                step.upgrade(section);
                node.removeAll();
                node.setAll(section.getConfig().getRoot());
            };
            EntitySchemaMigrations.checkContiguous(pdSectionClass,
                    EntitySchema.INITIAL_SCHEMA_VERSION + migrations.size(), fromVersion);
            migrations.add(new EntitySchemaMigrations.Step(raw, mode));
            return this;
        }

        public PDSectionConfiguration<S> build() {
            return new PDSectionConfiguration<>(pluginData, pdSectionClass, hotLoad,
                    collection, defaultBackend, suggestedBackends, allowedBackendTypes, codec,
                    sectionCachePolicy, warmup, legacyYamlRootKey, legacyYamlAdapter, migrations);
        }
    }
}
