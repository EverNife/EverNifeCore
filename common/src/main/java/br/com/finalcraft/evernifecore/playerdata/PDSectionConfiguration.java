package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.everyconfig.config.section.ConfigSection;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchema;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrationMode;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaStep;
import br.com.finalcraft.evernifecore.playerdata.storage.SectionIds;
import br.com.finalcraft.evernifecore.playerdata.storage.SectionLifecycle;
import br.com.finalcraft.evernifecore.storage.BackendType;
import br.com.finalcraft.everydatabase.codec.Codec;
import lombok.Getter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Developer-side configuration of a PDSection.
 *
 * <p>The developer ADVISES (default/suggested backends, cache lifecycle, legacy adapter); the admin
 * DECIDES through storage.yml (resolution chain). The one HARD constraint the developer can impose
 * is {@link Builder#allowedBackendTypes(BackendType...)} - e.g. an ephemeral section that must never
 * be persisted to a database, only an in-memory backend.</p>
 *
 * <p>Always built through {@link #builder(ECPluginData, Class)}.</p>
 */
@Getter
public class PDSectionConfiguration<S extends PDSection> {

    private final ECPluginData pluginData;
    private final Class<S> pdSectionClass;
    /**
     * The section's stable storage identity, lowercase and validated (see {@link SectionIds}). Required
     * at registration: the collection, the storage.yml entry and the admin command id are all derived
     * from it, so the class can be renamed without moving a single row.
     */
    private final String sectionId;

    // storage guidance (all optional - nullable means "use the default from the resolution chain")
    /** Nullable - the default collection name is derived from the plugin name and the section id. */
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
    /** Nullable - a one-line human description; becomes a comment on the generated storage.yml entry. */
    private final String description;
    /**
     * When this section's cells enter and leave memory - never null; defaults to
     * {@link SectionLifecycle#LAZY}.
     */
    private final SectionLifecycle lifecycle;
    /**
     * How long a cell survives after its owner stops being online, for a lifecycle that releases
     * when idle. Never null; defaults to {@link SectionLifecycle#DEFAULT_IDLE_GRACE}.
     */
    private final Duration idleGrace;
    /** Hard ceiling of cached cells (LRU, dirty cells pinned); {@code 0} = unbounded. */
    private final int maxCached;
    /**
     * Whether a re-registration drops this section's unflushed changes instead of flushing them
     * first. Off by default: a re-register flushes, then clears (see
     * {@code PlayerController.registerPDSectionCfg}).
     */
    private final boolean discardDirtyOnReload;
    /** Nullable - when absent, there is no legacy YAML migration for this section. */
    private final String legacyYamlRootKey;
    private final Function<ConfigSection, S> legacyYamlAdapter;
    /**
     * The schema-migration chain for this section (never null; may be empty). Ordered: entry i upgrades
     * version {@code (i + 1)} to {@code (i + 2)}. Registered with the framework (before the section binds)
     * by {@code PlayerController.registerPDSectionCfg}.
     */
    private final List<EntitySchemaMigrations.Step> migrations;

    private PDSectionConfiguration(ECPluginData pluginData, Class<S> pdSectionClass, String sectionId,
                                   String collection, String defaultBackend, List<String> suggestedBackends,
                                   List<BackendType> allowedBackendTypes, Codec<S> codec, String description,
                                   SectionLifecycle lifecycle, Duration idleGrace, int maxCached,
                                   boolean discardDirtyOnReload,
                                   String legacyYamlRootKey, Function<ConfigSection, S> legacyYamlAdapter,
                                   List<EntitySchemaMigrations.Step> migrations) {
        this.pluginData = pluginData;
        this.pdSectionClass = pdSectionClass;
        this.sectionId = sectionId;
        this.collection = collection;
        this.defaultBackend = defaultBackend;
        this.suggestedBackends = Collections.unmodifiableList(suggestedBackends);
        this.allowedBackendTypes = Collections.unmodifiableList(allowedBackendTypes);
        this.codec = codec;
        this.description = description;
        this.lifecycle = lifecycle;
        this.idleGrace = idleGrace;
        this.maxCached = maxCached;
        this.discardDirtyOnReload = discardDirtyOnReload;
        this.legacyYamlRootKey = legacyYamlRootKey;
        this.legacyYamlAdapter = legacyYamlAdapter;
        this.migrations = Collections.unmodifiableList(migrations);
    }

    /**
     * @param sectionId the section's stable storage identity (see {@link #getSectionId()}); a
     *                  positional argument on purpose - it must not be forgettable
     */
    public static <S extends PDSection> Builder<S> builder(ECPluginData pluginData, Class<S> pdSectionClass,
                                                           String sectionId) {
        return new Builder<>(pluginData, pdSectionClass, sectionId);
    }

    // ---------------------------------------------------------------------

    public static class Builder<S extends PDSection> {

        private final ECPluginData pluginData;
        private final Class<S> pdSectionClass;
        private final String sectionId;
        private String collection;
        private String defaultBackend;
        private List<String> suggestedBackends = Collections.emptyList();
        private List<BackendType> allowedBackendTypes = Collections.emptyList();
        private Codec<S> codec;
        private String description;
        private SectionLifecycle lifecycle = SectionLifecycle.LAZY;
        private Duration idleGrace = SectionLifecycle.DEFAULT_IDLE_GRACE;
        private int maxCached = 0;
        private boolean discardDirtyOnReload = false;
        private String legacyYamlRootKey;
        private Function<ConfigSection, S> legacyYamlAdapter;
        private final List<EntitySchemaMigrations.Step> migrations = new ArrayList<>();

        private Builder(ECPluginData pluginData, Class<S> pdSectionClass, String sectionId) {
            this.pluginData = pluginData;
            this.pdSectionClass = pdSectionClass;
            this.sectionId = SectionIds.requireValid(sectionId, pdSectionClass);
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

        /** A one-line description of what this section holds; becomes a comment on its storage.yml entry. */
        public Builder<S> description(String description) {
            this.description = description;
            return this;
        }

        /**
         * When this section's cells enter and leave memory (default {@link SectionLifecycle#LAZY}):
         * {@code LAZY} (first access), {@code ONLINE} (at login), {@code RESIDENT} (at login, never
         * released) or {@code PRELOADED} (whole collection at bind, never released).
         */
        public Builder<S> lifecycle(SectionLifecycle lifecycle) {
            this.lifecycle = lifecycle == null ? SectionLifecycle.LAZY : lifecycle;
            return this;
        }

        /**
         * How long a cell survives after its owner stops being online (default
         * {@link SectionLifecycle#DEFAULT_IDLE_GRACE}). Ignored by a lifecycle that never releases.
         */
        public Builder<S> idleGrace(Duration idleGrace) {
            if (idleGrace != null && idleGrace.isNegative()) {
                throw new IllegalArgumentException("idleGrace must not be negative, got " + idleGrace);
            }
            this.idleGrace = idleGrace == null ? SectionLifecycle.DEFAULT_IDLE_GRACE : idleGrace;
            return this;
        }

        /**
         * A hard ceiling of cached cells (bounded LRU; a dirty cell is pinned and never dropped by
         * the bound). Off by default - the lifecycle's release rule is what normally bounds memory;
         * this is the safety net for a section that legitimately spans more players than fit.
         */
        public Builder<S> maxCached(int maxCached) {
            if (maxCached < 0) {
                throw new IllegalArgumentException("maxCached must not be negative, got " + maxCached);
            }
            this.maxCached = maxCached;
            return this;
        }

        /**
         * Makes a re-registration DROP this section's unflushed changes instead of flushing them
         * first. Only for a section whose in-memory state is derived/ephemeral and must not survive a
         * plugin reload; anything durable belongs to the default (flush, then clear).
         */
        public Builder<S> discardDirtyOnReload() {
            this.discardDirtyOnReload = true;
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
            return new PDSectionConfiguration<>(pluginData, pdSectionClass, sectionId,
                    collection, defaultBackend, suggestedBackends, allowedBackendTypes, codec, description,
                    lifecycle, idleGrace, maxCached, discardDirtyOnReload,
                    legacyYamlRootKey, legacyYamlAdapter, migrations);
        }
    }
}
