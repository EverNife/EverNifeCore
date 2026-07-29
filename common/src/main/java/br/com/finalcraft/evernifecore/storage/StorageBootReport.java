package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.storage.config.FileBackendDirectoryGuard;
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

    /**
     * Renders the report of two or more enabled file backends sharing a directory - the same shape as
     * {@link #render}, since it is the same kind of event: a boot that cannot proceed, explained in a
     * banner the admin will still see once the stack traces have scrolled past.
     *
     * @param overlaps       the colliding pairs, one numbered block each
     * @param storageYmlFile the file to name, or {@code null} for the generic name
     */
    public static List<String> renderOverlappingDirectories(List<FileBackendDirectoryGuard.Overlap> overlaps,
                                                            File storageYmlFile) {
        String source = sourceOf(storageYmlFile);

        List<String> body = new ArrayList<>();
        body.add(" " + overlaps.size() + " pair(s) of enabled file backends in");
        body.add(" " + source + " point at the same data directory:");
        body.add("");

        int index = 1;
        for (FileBackendDirectoryGuard.Overlap overlap : overlaps) {
            body.add("  [" + index + "] '" + overlap.getFirst().getName() + "' and '"
                    + overlap.getSecond().getName() + "'");
            body.add("      " + pad(overlap.getFirst().getName()) + ": " + overlap.getFirstDirectory());
            body.add("      " + pad(overlap.getSecond().getName()) + ": " + overlap.getSecondDirectory());
            if (overlap.isSameDirectory()) {
                body.add("      cause   : the SAME directory");
            } else {
                body.add("      cause   : '" + overlap.getInner().getName() + "' sits INSIDE the directory of '"
                        + overlap.getOuter().getName() + "'");
            }
            body.add("");
            index++;
        }

        body.add(" WHY THIS CANCELS THE BOOT");
        body.add("   Each backend locks its files through a map of its own, so two of them over one");
        body.add("   directory have no mutual exclusion at all: two writes to the same key overwrite");
        body.add("   each other and nothing reports it. Each also lists every file with its format's");
        body.add("   extension, so each would read the other's files as if they were its own.");
        body.add("   Nothing here is recoverable after the fact, which is why it stops now.");
        body.add("");
        body.add(" HOW TO FIX");
        body.add("   1. Give each backend a 'path' of its own in " + source + " - not one inside");
        body.add("      another's; or");
        body.add("   2. If one of them is not used any more, set 'enabled: false' on it.");

        return banner("STORAGE MISCONFIGURED - OVERLAPPING BACKEND DIRECTORIES", body);
    }

    /**
     * Renders the report of a {@code network.storage-backend-id} that names nothing usable. It gets the
     * same banner as an unreachable backend because it costs the admin the same thing: a server that
     * will not come up until one line of storage.yml is corrected.
     *
     * @param declaredValue  what the key says today, or {@code null} when the key is missing entirely
     * @param cause          the one-line reason this value cannot be used
     * @param storageYmlFile the file to name, or {@code null} for the generic name
     */
    public static List<String> renderUnusableNetworkBackend(String declaredValue, String cause,
                                                            File storageYmlFile) {
        String source = sourceOf(storageYmlFile);

        List<String> body = new ArrayList<>();
        body.add(" 'network.storage-backend-id' in");
        body.add(" " + source + " does not name a usable backend:");
        body.add("");
        body.add("  [1] network.storage-backend-id");
        body.add("      value   : " + (declaredValue == null ? "(the key is not there)" : "'" + declaredValue + "'"));
        body.add("      cause   : " + cause);
        body.add("      holds   : the account registry, every account-wide section,");
        body.add("                and the network-wide server cooldowns");
        body.add("");
        body.add(" WHY THIS CANCELS THE BOOT");
        body.add("   This one backend is what every server of your network has to agree on. There is");
        body.add("   deliberately no fallback to 'default-backend': inheriting it in silence would");
        body.add("   move the whole account family the day someone edits an unrelated key, and an");
        body.add("   upgrade is exactly when nobody is watching.");
        body.add("");
        body.add(" HOW TO FIX");
        body.add("   In " + source + ", name an enabled backend id from 'storage-backends':");
        body.add("");
        body.add("     network:");
        body.add("       storage-backend-id: networkdata");
        body.add("");
        body.add("   On a single server any local backend is correct - a network of one is still a");
        body.add("   network. Point every server at one shared database once a second one joins.");

        return banner("STORAGE MISCONFIGURED - NO USABLE NETWORK BACKEND", body);
    }

    /** The ruler/title/ruler skeleton every boot-cancelling report shares, so they stay one shape. */
    private static List<String> banner(String title, List<String> body) {
        List<String> lines = new ArrayList<>();
        lines.add(RULE);
        lines.add("   EVERNIFECORE - " + title);
        lines.add(RULE);
        lines.add("");
        lines.addAll(body);
        lines.add(RULE);
        return Collections.unmodifiableList(lines);
    }

    private static String sourceOf(File storageYmlFile) {
        return storageYmlFile != null ? storageYmlFile.getPath() : "storage.yml";
    }

    /** Right-pads a backend name so the two directory lines of one block line up under each other. */
    private static String pad(String backendName) {
        StringBuilder padded = new StringBuilder(backendName);
        while (padded.length() < "cause  ".length()) {
            padded.append(' ');
        }
        return padded.toString();
    }

    /** backendName -> the storage.yml keys that explicitly route data to it, in declaration order. */
    static Map<String, List<String>> usagesByBackend(ParsedStorageConfig config) {
        Map<String, List<String>> usages = new LinkedHashMap<>();
        addUsage(usages, config.getDefaultBackendName(), "default-backend");
        addUsage(usages, config.getPlayerData().getBackendName(), "playerdata.storage-backend-id");
        addUsage(usages, config.getNetworkBackendName(), "network.storage-backend-id");
        //the cooldowns have no backend key of their own - they mount on the network one, and the report
        //has to say so, or the admin reads "network.storage-backend-id" and misses what rides along
        addUsage(usages, config.getNetworkBackendName(), "network.server-cooldowns");
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
