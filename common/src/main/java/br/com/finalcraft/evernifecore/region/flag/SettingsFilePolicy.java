package br.com.finalcraft.evernifecore.region.flag;

/** Whether a flag's settings file ({@code regions/flags/<name>.yml}) is written and read. */
public enum SettingsFilePolicy {
    /** As {@code Regions.generate-global-settings-configuration-files} in EverNifeCore's config says. */
    FOLLOW_SERVER,
    /** Always written and read, whatever the server key says. */
    ALWAYS,
    /** Never written nor read: the flag runs on its code defaults and the per-region overrides. */
    NEVER
}
