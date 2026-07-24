package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.storage.config.PDSectionAdminConfig;
import br.com.finalcraft.evernifecore.storage.config.ParsedStorageConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the admin-facing report of a boot that could not reach its storage. Pure: it only builds
 * lines - logging and the stop decision belong to {@link StorageBootGuard} - which is what makes the
 * exact wording assertable in a unit test.
 */
public final class StorageBootReport {

    private static final String RULE = "############################################################";

    private StorageBootReport() {
    }

    /**
     * @param failure   the enriched exception (failures + usages + storage.yml path)
     * @param stopping  whether the server is being stopped (changes the closing paragraph)
     * @param reloading true when this is a reload, not a boot (the live storage survives)
     */
    public static List<String> render(StorageUnavailableException failure, boolean stopping, boolean reloading) {
        String source = failure.getStorageYmlFile() != null
                ? failure.getStorageYmlFile().getPath()
                : "storage.yml";

        List<String> lines = new ArrayList<>();
        lines.add(RULE);
        lines.add("   EVERNIFECORE - STORAGE UNAVAILABLE" + (stopping ? " - SERVER STOPPED" : ""));
        lines.add(RULE);
        lines.add("");
        lines.add(" " + failure.getFailures().size() + " storage backend(s) declared as 'enabled: true' in");
        lines.add(" " + source + " could NOT be contacted:");
        lines.add("");

        int index = 1;
        for (StorageInitFailure each : failure.getFailures()) {
            List<String> usedBy = failure.getUsages().get(each.getBackendName());
            String usage = (usedBy == null || usedBy.isEmpty())
                    ? "no explicit reference in storage.yml (enabled anyway)"
                    : String.join(", ", usedBy);
            String typeSuffix = each.getType() != null ? "  (type: " + each.getType().getId() + ")" : "";
            lines.add("  [" + index + "] backend '" + each.getBackendName() + "'" + typeSuffix);
            lines.add("      target  : " + each.getTarget());
            lines.add("      used by : " + usage);
            lines.add("      cause   : " + each.getRootCauseSummary());
            lines.add("");
            index++;
        }

        if (stopping) {
            lines.add(" WHY THE SERVER IS BEING STOPPED");
            lines.add("   Booting without the database that holds player data would let every");
            lines.add("   player join with EMPTY data, and the first save would overwrite the");
            lines.add("   real rows with those empty ones. Stopping now loses nothing.");
            lines.add("");
        }

        lines.add(" HOW TO FIX");
        lines.add("   1. Start the database(s) above, then start the server again; or");
        lines.add("   2. Fix the url/user/pass in " + source + "; or");
        lines.add("   3. If a backend is not used any more, set 'enabled: false' on it.");
        lines.add("");
        lines.add(" The full stack trace of each failure is printed above this banner.");
        lines.add("");

        if (reloading) {
            lines.add(" This was a RELOAD: the previously loaded storage is still live and serving, so");
            lines.add(" nothing was stopped and no data was lost. Fix the config/database and reload again.");
        } else if (stopping) {
            lines.add(" To boot anyway (NOT RECOMMENDED - the server runs with EverNifeCore disabled and");
            lines.add(" data WILL diverge), set in plugins/EverNifeCore/config.yml:");
            lines.add("   Settings.Storage.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE: false");
        } else {
            lines.add(" The server is NOT being stopped"
                    + " (Settings.Storage.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE: false).");
            lines.add(" EverNifeCore stays DISABLED and every plugin that depends on it will fail.");
        }
        lines.add(RULE);

        return Collections.unmodifiableList(lines);
    }

    /** backendName -> the storage.yml keys that explicitly route data to it, in declaration order. */
    static Map<String, List<String>> usagesByBackend(ParsedStorageConfig config) {
        Map<String, List<String>> usages = new LinkedHashMap<>();
        addUsage(usages, config.getDefaultBackendName(), "default-backend");
        addUsage(usages, config.getPlayerData().getBackendName(), "playerdata.storage-backend-id");
        if (config.isMultiplatformAccountsEnabled()) {
            addUsage(usages, config.getAccountBackendName(), "multi-platform-accounts.storage-backend-id");
        }
        for (Map.Entry<String, Map<String, PDSectionAdminConfig>> ofPlugin : config.getPDSections().entrySet()) {
            for (PDSectionAdminConfig section : ofPlugin.getValue().values()) {
                addUsage(usages, section.getBackendName(),
                        "pdsections." + section.getPluginName() + "." + section.getSectionName());
            }
        }
        return usages;
    }

    private static void addUsage(Map<String, List<String>> usages, String backendName, String usedBy) {
        if (backendName != null && !backendName.isEmpty()) {
            usages.computeIfAbsent(backendName, key -> new ArrayList<>()).add(usedBy);
        }
    }

    /**
     * Copies {@code failure} attaching the storage.yml context - the usages index and the file path -
     * which only the PlayerController constructor has in scope. The report TEXT is rendered later, by
     * the guard: whether the server stops depends on boot-vs-reload and on the setting, neither of
     * which is known here.
     */
    public static StorageUnavailableException enrich(StorageUnavailableException failure,
                                                      ParsedStorageConfig config, File storageYmlFile) {
        return new StorageUnavailableException(failure.getMessage(), failure.getFailures(),
                usagesByBackend(config), storageYmlFile);
    }
}
