package br.com.finalcraft.evernifecore.flag;

/** Whether a flag's settings file ({@code regions/flags/<name>.yml}) is written and read. */
public enum SettingsFilePolicy {
    /**
     * As the flag's owner says: an EverNifeCore flag follows {@code Regions.generate-global-settings-configuration-files}
     * in EverNifeCore's config, a plugin's flag follows {@code regions.flag-settings-files} of the plugin's
     * {@code RegionManager} settings (off while the plugin has no manager).
     */
    FOLLOW_OWNER,
    /** Always written and read, whatever the owner's key says. */
    ALWAYS,
    /** Never written nor read: the flag runs on its code defaults and the per-region overrides. */
    NEVER
}
