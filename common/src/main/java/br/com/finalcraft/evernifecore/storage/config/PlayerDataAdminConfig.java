package br.com.finalcraft.evernifecore.storage.config;

/**
 * The {@code playerdata:} block of storage.yml: where the base PlayerData entity
 * persists and how it is loaded at startup.
 */
public final class PlayerDataAdminConfig {

    public enum LoadMode {
        /** Loads every PlayerData into memory at startup (current behavior). */
        ALL,
        /** Loads only players seen in the last {@code recentDays}; the rest load on demand at login. */
        RECENT
    }

    public static final String DEFAULT_COLLECTION = "evernifecore_playerdata";

    /** Default cadence of the orphan reaper when it is enabled (6 hours). */
    public static final int DEFAULT_ORPHAN_REAPER_INTERVAL_MINUTES = 360;

    /** Default bound on login-time storage resolution before the login is denied. */
    public static final int DEFAULT_LOGIN_TIMEOUT_SECONDS = 5;

    private final String backendName;       // nullable -> default-backend
    private final String collection;
    private final LoadMode loadMode;
    private final int recentDays;
    private final boolean orphanReaperEnabled;     // playerdata.orphan-reaper.enabled (default false)
    private final int orphanReaperIntervalMinutes; // playerdata.orphan-reaper.interval-minutes
    private final int loginTimeoutSeconds;         // playerdata.login-timeout-seconds

    PlayerDataAdminConfig(String backendName, String collection, LoadMode loadMode,
                          int recentDays,
                          boolean orphanReaperEnabled, int orphanReaperIntervalMinutes,
                          int loginTimeoutSeconds) {
        this.backendName = backendName;
        this.collection = collection != null ? collection : DEFAULT_COLLECTION;
        this.loadMode = loadMode != null ? loadMode : LoadMode.ALL;
        this.recentDays = recentDays;
        this.orphanReaperEnabled = orphanReaperEnabled;
        this.orphanReaperIntervalMinutes = orphanReaperIntervalMinutes > 0
                ? orphanReaperIntervalMinutes : DEFAULT_ORPHAN_REAPER_INTERVAL_MINUTES;
        this.loginTimeoutSeconds = loginTimeoutSeconds > 0
                ? loginTimeoutSeconds : DEFAULT_LOGIN_TIMEOUT_SECONDS;
    }

    /** Nullable - resolves against {@code default-backend} when absent. */
    public String getBackendName() {
        return backendName;
    }

    public String getCollection() {
        return collection;
    }

    public LoadMode getLoadMode() {
        return loadMode;
    }

    public int getRecentDays() {
        return recentDays;
    }

    /** Whether the periodic orphan-section reaper is enabled (OFF by default). */
    public boolean isOrphanReaperEnabled() {
        return orphanReaperEnabled;
    }

    /** How often the orphan reaper runs, in minutes (only relevant when it is enabled). */
    public int getOrphanReaperIntervalMinutes() {
        return orphanReaperIntervalMinutes;
    }

    /** How long login-time storage resolution may take before the login is denied. */
    public int getLoginTimeoutSeconds() {
        return loginTimeoutSeconds;
    }
}
