package br.com.finalcraft.evernifecore.storage.config;

/**
 * The kinds of section an admin can configure, and where each one lives in storage.yml. Parsing an
 * entry, generating one and reporting an orphan are the same job for every family - only the block
 * name, the collection prefix and the wording differ, which is what this holds.
 */
public enum SectionFamily {

    /** Per-player sections, keyed by the player uuid. */
    PLAYER("pdsections", "pd", "PDSection"),
    /** Per-account rows shared by every identity linked to one account. */
    ACCOUNT("accountsections", "acs", "AccountSection");

    private final String yamlBlock;
    private final String collectionPrefix;
    private final String label;

    SectionFamily(String yamlBlock, String collectionPrefix, String label) {
        this.yamlBlock = yamlBlock;
        this.collectionPrefix = collectionPrefix;
        this.label = label;
    }

    /** The top-level storage.yml key holding this family's entries. */
    public String getYamlBlock() {
        return yamlBlock;
    }

    /** The prefix of a derived collection name ({@code <prefix>_<plugin>_<sectionId>}). */
    public String getCollectionPrefix() {
        return collectionPrefix;
    }

    /** How this family is named in messages the admin reads. */
    public String getLabel() {
        return label;
    }
}
