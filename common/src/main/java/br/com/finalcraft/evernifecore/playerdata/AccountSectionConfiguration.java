package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchema;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrationMode;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaMigrations;
import br.com.finalcraft.everydatabase.manager.entityschema.EntitySchemaStep;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Developer-side configuration of an {@link AccountSection}. Deliberately smaller than a
 * PDSection's: the whole account family lives on the ONE backend configured under
 * {@code multiplatform-accounts} in storage.yml (no per-section backend routing), the cache
 * lifecycle is fixed (resident while any member is online, refreshed on login, released after the
 * last member quits) and hot-load always happens on login.
 *
 * <p>Always built through {@link #builder(ECPluginData, Class)}.</p>
 */
@Getter
public class AccountSectionConfiguration<T extends AccountSection<T>> {

    private final ECPluginData pluginData;
    private final Class<T> sectionClass;
    /** Nullable - the default collection name ({@code acs_<plugin>_<section>}) is derived. */
    private final String collection;
    /**
     * The schema-migration chain for this account section (never null; may be empty). Ordered: entry i
     * upgrades version {@code (i + 1)} to {@code (i + 2)}. Registered with the framework (before the
     * section binds) by {@code PlayerController.registerAccountSectionCfg}.
     */
    private final List<EntitySchemaMigrations.Step> migrations;

    private AccountSectionConfiguration(ECPluginData pluginData, Class<T> sectionClass, String collection,
                                        List<EntitySchemaMigrations.Step> migrations) {
        this.pluginData = pluginData;
        this.sectionClass = sectionClass;
        this.collection = collection;
        this.migrations = Collections.unmodifiableList(migrations);
    }

    public static <T extends AccountSection<T>> Builder<T> builder(ECPluginData pluginData, Class<T> sectionClass) {
        return new Builder<>(pluginData, sectionClass);
    }

    // ---------------------------------------------------------------------

    public static class Builder<T extends AccountSection<T>> {

        private final ECPluginData pluginData;
        private final Class<T> sectionClass;
        private String collection;
        private final List<EntitySchemaMigrations.Step> migrations = new ArrayList<>();

        private Builder(ECPluginData pluginData, Class<T> sectionClass) {
            this.pluginData = pluginData;
            this.sectionClass = sectionClass;
        }

        public Builder<T> collection(String collection) {
            this.collection = collection;
            return this;
        }

        /** Appends a {@link EntitySchemaMigrationMode#LAZY} schema-migration step. See {@link #migration(int, EntitySchemaMigrationMode, EntitySchemaStep)}. */
        public Builder<T> migration(int fromVersion, EntitySchemaStep step) {
            return migration(fromVersion, EntitySchemaMigrationMode.LAZY, step);
        }

        /**
         * Appends the schema-migration step that upgrades a stored payload FROM {@code fromVersion} to
         * {@code fromVersion + 1}. Steps form a contiguous chain starting at
         * {@link EntitySchema#INITIAL_SCHEMA_VERSION}. The step edits the raw JSON tree before binding.
         * An {@link EntitySchemaMigrationMode#EAGER} step additionally drives a boot-time full-collection sweep - note
         * account sections are written across the network, so read the cascade/eager notes on
         * {@link EntitySchemaMigrationMode} before using it.
         */
        public Builder<T> migration(int fromVersion, EntitySchemaMigrationMode mode, EntitySchemaStep step) {
            EntitySchemaMigrations.checkContiguous(sectionClass,
                    EntitySchema.INITIAL_SCHEMA_VERSION + migrations.size(), fromVersion);
            migrations.add(new EntitySchemaMigrations.Step(step, mode));
            return this;
        }

        public AccountSectionConfiguration<T> build() {
            return new AccountSectionConfiguration<>(pluginData, sectionClass, collection, migrations);
        }
    }
}
