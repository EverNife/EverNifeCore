package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.everyconfig.config.section.ConfigSection;

/**
 * One payload-schema upgrade step expressed over a file-less, type-aware {@link ConfigSection} - the
 * same rich surface {@code legacyYaml} uses - instead of a raw Jackson node. Mutate the section in
 * place to upgrade a stored payload one schema version; the framework owns the {@code schemaVersion}
 * field and protects the identity/lock fields around the step (see {@code EntitySchemaMigrations}), so a
 * step cannot re-key or unlock a row.
 *
 * <p>The section is IN-MEMORY and has NO file behind it - there is no file at a migration step (unlike
 * {@code legacyYaml}, which reads a real legacy YAML subtree). It resolves {@code getValue(path, T)} /
 * {@code setValue} through the same registered type adapters a real config does, so a step can coerce
 * platform types instead of hand-parsing raw nodes; the raw tree is still reachable via
 * {@code section.getConfig().getRoot()} when a step needs it.
 *
 * <p><b>Steps must be pure tree transforms</b> - the same contract the underlying raw-node primitive
 * carries: no I/O, no host-platform API, no shared mutable state. A step runs on decode threads, on any
 * flush/conflict-resolution thread, on cross-backend transfers, and - for an eager step - on a
 * background boot sweep over the whole collection.
 */
@FunctionalInterface
public interface SectionSchemaStep {

    /**
     * Mutates {@code section} in place, upgrading it one schema version. May throw; a throwing step
     * fails the decode of that one row and leaves the stored row untouched, so the next read retries.
     */
    void upgrade(ConfigSection section) throws Exception;
}
