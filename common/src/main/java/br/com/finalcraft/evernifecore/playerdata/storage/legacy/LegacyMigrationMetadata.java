package br.com.finalcraft.evernifecore.playerdata.storage.legacy;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everyconfig.config.Config;
import lombok.Getter;

import java.io.File;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Progress file of the one-time legacy YAML import, written NEXT TO {@code storage.yml} - never
 * inside {@code PlayerData/}, which the import itself drains.
 *
 * <p>This file is the single source of truth of the import trigger: {@code isComplete()} answers
 * "is there anything left to migrate?" without ever touching the storage backend. Counting rows on
 * the backend cannot answer it, because a second run legitimately happens over an
 * already-populated collection (a plugin installed later brings its own adapter along).</p>
 *
 * <p>Deleting the file forces a full re-scan; that is safe by construction, since the import skips
 * every entity whose UUID already reached the backend.</p>
 */
@Getter
public final class LegacyMigrationMetadata {

    private static final String FILE_NAME = "playerdata-storage-migration-metadata.yml";

    private static final String[] HEADER = {
            "============================================================",
            " Legacy PlayerData migration - progress",
            "",
            " DELETE THIS FILE to force a full re-scan from scratch.",
            " The original .yml files are NEVER edited nor deleted.",
            "",
            " ROLLBACK / DOWNGRADE: an older version reads ONLY PlayerData/.",
            " While migrating, archived files sit in PlayerData-Imported/;",
            " once complete they are consolidated into __LegacyData_V2/PlayerData/",
            " (beside this file). Move them back into PlayerData/ before",
            " going back to an older version.",
            "============================================================"
    };

    /** How a root key found in the legacy files ended up. */
    public enum SectionStatus {
        /** Covered by a registered adapter (or by the importer itself, for the base block). */
        DONE,
        /** Holds data, but no plugin registered an adapter for it - this is what blocks completion. */
        PENDING_NO_ADAPTER,
        /** Present but empty in every file of the run - nothing to migrate, never blocks. */
        EMPTY,
        /** Had an adapter, but its data could not be saved. */
        FAILED
    }

    /** What happened to one root key across a whole run. */
    @Getter
    public static final class SectionProgress {
        private final SectionStatus status;
        /** Files containing the root key, empty ones included. */
        private final int found;
        /** Entities this run actually wrote (already-present UUIDs are skipped, so a re-run reports 0). */
        private final int imported;
        /** Plugin owning the adapter; empty when nobody claims the key. */
        private final String owner;
        /** PDSection the adapter feeds; empty when nobody claims the key. */
        private final String pdSection;

        public SectionProgress(SectionStatus status, int found, int imported, String owner, String pdSection) {
            this.status = status;
            this.found = found;
            this.imported = imported;
            this.owner = owner == null ? "" : owner;
            this.pdSection = pdSection == null ? "" : pdSection;
        }
    }

    private boolean complete;
    private String startedAt = "";
    private String lastRunAt = "";
    private int runs;
    private int filesTotalFound;
    private int filesFullyImported;
    private int filesPending;
    private int filesFailed;
    private Map<String, SectionProgress> sections = Collections.emptyMap();

    /** The progress file of a given legacy folder: a sibling of {@code storage.yml}, one level up. */
    public static File fileOf(File legacyFolder) {
        return new File(legacyFolder.getParentFile(), FILE_NAME);
    }

    /**
     * Reads the progress file - the whole cost of the boot trigger's fast path. An absent file means
     * "never migrated" (or an admin asking for a re-scan) and costs no read at all.
     */
    public static LegacyMigrationMetadata load(File file) {
        LegacyMigrationMetadata metadata = new LegacyMigrationMetadata();
        if (!file.isFile()) {
            return metadata;
        }
        Config config = ConfigFactory.open(file);
        metadata.complete = config.getBoolean("complete", false);
        metadata.startedAt = config.getString("started-at", "");
        metadata.lastRunAt = config.getString("last-run-at", "");
        metadata.runs = config.getInt("runs", 0);
        metadata.filesTotalFound = config.getInt("files.total-found", 0);
        metadata.filesFullyImported = config.getInt("files.fully-imported", 0);
        metadata.filesPending = config.getInt("files.pending", 0);
        metadata.filesFailed = config.getInt("files.failed", 0);

        Map<String, SectionProgress> sections = new LinkedHashMap<>();
        for (String rootKey : config.getKeys("sections")) {
            String path = "sections." + rootKey + ".";
            sections.put(rootKey, new SectionProgress(
                    parseStatus(config.getString(path + "status", "")),
                    config.getInt(path + "found", 0),
                    config.getInt(path + "imported", 0),
                    config.getString(path + "owner", ""),
                    config.getString(path + "pdsection", "")));
        }
        metadata.sections = sections;
        return metadata;
    }

    private static SectionStatus parseStatus(String raw) {
        for (SectionStatus status : SectionStatus.values()) {
            if (status.name().equalsIgnoreCase(raw)) {
                return status;
            }
        }
        return SectionStatus.PENDING_NO_ADAPTER; //an unreadable status must never pass as done
    }

    /** Stamps the timestamps of a run about to be recorded ({@code started-at} survives the first one). */
    public void beginRun() {
        String now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
        if (startedAt.isEmpty()) {
            startedAt = now;
        }
        lastRunAt = now;
        runs++;
    }

    public void setFiles(int totalFound, int fullyImported, int pending, int failed) {
        this.filesTotalFound = totalFound;
        this.filesFullyImported = fullyImported;
        this.filesPending = pending;
        this.filesFailed = failed;
    }

    /** Replaces the whole section map: the file is a snapshot of the last run, not a running tally. */
    public void replaceSections(Map<String, SectionProgress> sections) {
        this.sections = new TreeMap<>(sections); //alphabetical: this file is read by humans
    }

    /**
     * The migration is complete once the legacy folder has nothing left to give: every file was fully
     * imported and archived away. A file kept back - because a root key of it still waits for an
     * adapter, or because it failed - is exactly what must bring the import back on a later boot,
     * since the plugin owning that key may only be installed then.
     *
     * <p>Reads the file tally, not the section statuses: a file left behind by a FAILURE is just as
     * pending as one left behind by a missing adapter, and only the tally sees both.</p>
     */
    public void recomputeComplete() {
        complete = filesPending == 0 && filesFailed == 0;
    }

    /** Writes the whole file in a single save. */
    public void save(File file) {
        Config config = ConfigFactory.open(file);
        config.setHeader(bannerPlusRollbackHeader());
        config.setValue("complete", complete);
        config.setValue("started-at", startedAt);
        config.setValue("last-run-at", lastRunAt);
        config.setValue("runs", runs);
        config.setValue("files.total-found", filesTotalFound);
        config.setValue("files.fully-imported", filesFullyImported);
        config.setValue("files.pending", filesPending);
        config.setValue("files.failed", filesFailed);

        config.removeValue("sections"); //drop what a previous run discovered: this is a snapshot
        for (Map.Entry<String, SectionProgress> entry : sections.entrySet()) {
            String path = "sections." + entry.getKey() + ".";
            SectionProgress progress = entry.getValue();
            config.setValue(path + "status", progress.getStatus().name());
            config.setValue(path + "found", progress.getFound());
            config.setValue(path + "imported", progress.getImported());
            config.setValue(path + "owner", progress.getOwner());
            config.setValue(path + "pdsection", progress.getPdSection());
        }
        config.save();
    }

    /**
     * The standard EverNifeCore banner followed by this file's own rollback instructions. The banner
     * makes the file consistent with every other config; the {@link #HEADER} lines below it are the
     * admin's only instruction once the server is down and the legacy folder has been drained. Falls
     * back to just the rollback lines in a headless runtime that has no {@link ECPluginData} (tests).
     */
    private static String[] bannerPlusRollbackHeader() {
        ECPluginData plugin = EverNifeCore.getEcPluginData();
        if (plugin == null) {
            return HEADER;
        }
        String[] banner = ConfigFactory.standardHeader(plugin);
        String[] combined = Arrays.copyOf(banner, banner.length + 1 + HEADER.length);
        combined[banner.length] = ""; //a blank line separating the banner from the rollback notes
        System.arraycopy(HEADER, 0, combined, banner.length + 1, HEADER.length);
        return combined;
    }
}
