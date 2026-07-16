package br.com.finalcraft.evernifecore.playerdata.storage.legacy;

import br.com.finalcraft.evernifecore.playerdata.storage.legacy.LegacyMigrationMetadata.SectionProgress;
import br.com.finalcraft.evernifecore.playerdata.storage.legacy.LegacyMigrationMetadata.SectionStatus;
import lombok.Getter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one legacy import run has to tell the admin: which root keys the files hold, who owns each of
 * them, how much data each carries, how many files could actually be archived - and, when something
 * stayed behind, WHICH key is holding it.
 *
 * <p>Carries the very numbers the progress file records, so the console and the file never disagree;
 * nothing here is discovered on its own.</p>
 */
@Getter
public final class LegacyImportReport {

    private String legacyFolder = "";
    private String importedFolder = "";
    private String failedFolder = "";
    private String metadataFile = "";

    private int filesTotalFound;
    private int filesFullyImported;
    private int filesPending;
    private int filesFailed;

    /** Root key -> what happened to it across the whole run, alphabetically (this is read by humans). */
    private Map<String, SectionProgress> sections = Collections.emptyMap();

    private boolean migrationComplete;
    /** True only for the run that DRAINED the folder - the one that owes the admin a downgrade warning. */
    private boolean becameCompleteThisRun;

    private final List<String> failedFiles = new ArrayList<>();
    private long durationMillis;

    void setPaths(File legacyFolder, File importedFolder, File failedFolder, File metadataFile) {
        this.legacyFolder = legacyFolder.getPath();
        this.importedFolder = importedFolder.getPath();
        this.failedFolder = failedFolder.getPath();
        this.metadataFile = metadataFile.getPath();
    }

    void setFiles(int totalFound, int fullyImported, int pending, int failed) {
        this.filesTotalFound = totalFound;
        this.filesFullyImported = fullyImported;
        this.filesPending = pending;
        this.filesFailed = failed;
    }

    void setSections(Map<String, SectionProgress> sections) {
        this.sections = new LinkedHashMap<>(sections);
    }

    /**
     * Records how the run moved the completion flag. The downgrade warning belongs to the run that
     * emptied the legacy folder, never to a later boot that merely finds it already empty - so the
     * previous value is what decides it, not the current one.
     */
    void setCompletion(boolean wasComplete, boolean isCompleteNow) {
        this.migrationComplete = isCompleteNow;
        this.becameCompleteThisRun = !wasComplete && isCompleteNow;
    }

    void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    void addFailedFile(String fileName, String reason) {
        failedFiles.add(fileName + " (" + reason + ")");
    }

    public boolean hasFailures() {
        return !failedFiles.isEmpty();
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("Legacy PlayerData migration - run finished in ").append(durationMillis).append("ms");
        sb.append("\n  Scanned ").append(filesTotalFound).append(" file(s) in ").append(legacyFolder);
        appendDiscovery(sb);
        appendOutcome(sb);
        appendFailedFiles(sb);
        if (becameCompleteThisRun) {
            sb.append('\n').append(rollbackWarning(legacyFolder, importedFolder));
        } else if (!migrationComplete) {
            sb.append("\n  INCOMPLETE - the next boot tries again; the details survive in ").append(metadataFile);
        }
        return sb.toString();
    }

    /**
     * The loud downgrade warning of the run that finally drained the legacy folder: an older version
     * reads only that folder, so finding it empty it would greet every player as brand new. Says the
     * same as the progress file's own header, which is where an admin looks once the server is down.
     */
    static String rollbackWarning(String legacyFolder, String importedFolder) {
        return String.join("\n",
                "  ============================================================",
                "  MIGRATION COMPLETE - the legacy folder is now EMPTY:",
                "    " + legacyFolder,
                "  every .yml file of it was archived (moved, never rewritten) into:",
                "    " + importedFolder,
                "  ROLLBACK / DOWNGRADE: an older version reads ONLY the legacy",
                "  folder, so it would see EVERY player as brand new. Move the",
                "  archived *.yml back into the legacy folder BEFORE downgrading.",
                "  ============================================================");
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Formatting
    // -----------------------------------------------------------------------------------------------------------------------------//

    private void appendDiscovery(StringBuilder sb) {
        sb.append("\n  Discovered ").append(sections.size()).append(" root key(s): ")
                .append(count(SectionStatus.DONE)).append(" with adapter, ")
                .append(count(SectionStatus.PENDING_NO_ADAPTER)).append(" without adapter, ")
                .append(count(SectionStatus.EMPTY)).append(" empty");
        int failed = count(SectionStatus.FAILED);
        if (failed > 0) {
            sb.append(", ").append(failed).append(" failed");
        }
        if (sections.isEmpty()) {
            return;
        }
        sb.append("\n  (found = files holding the key; imported = entities written by THIS run -")
                .append("\n   whoever already reached the backend is skipped, so a re-run legitimately imports 0)");
        int keyWidth = 0;
        int targetWidth = 0;
        for (Map.Entry<String, SectionProgress> entry : sections.entrySet()) {
            keyWidth = Math.max(keyWidth, entry.getKey().length());
            targetWidth = Math.max(targetWidth, targetOf(entry.getValue()).length());
        }
        for (Map.Entry<String, SectionProgress> entry : sections.entrySet()) {
            SectionProgress progress = entry.getValue();
            sb.append("\n    ").append(pad(entry.getKey(), keyWidth))
                    .append(" -> ").append(pad(targetOf(progress), targetWidth))
                    .append("  ").append(progress.getFound()).append(" found");
            if (progress.getStatus() != SectionStatus.EMPTY) {
                sb.append(", ").append(progress.getImported()).append(" imported");
            }
            sb.append("  [").append(progress.getStatus().name()).append(']');
        }
    }

    private void appendOutcome(StringBuilder sb) {
        sb.append("\n  Outcome:");
        sb.append("\n    ").append(filesTotalFound).append(" file(s) found");
        //the arrows only earn their place when something actually went that way
        sb.append("\n    ").append(filesFullyImported).append(" file(s) fully imported");
        if (filesFullyImported > 0) {
            sb.append(" -> moved to ").append(importedFolder);
        }
        sb.append("\n    ").append(filesPending).append(" file(s) still pending");
        if (filesPending > 0) {
            sb.append(" -> kept in ").append(legacyFolder);
            List<String> blockers = keysWith(SectionStatus.PENDING_NO_ADAPTER);
            if (!blockers.isEmpty()) {
                //naming the key is the whole point: a count tells the admin nothing about what to install
                sb.append(" (nobody claims: ").append(String.join(", ", blockers)).append(")");
            }
        }
        sb.append("\n    ").append(filesFailed).append(" file(s) failed");
        if (filesFailed > 0) {
            sb.append(" -> copied for diagnosis to ").append(failedFolder)
                    .append(", the originals stay pending");
        }
    }

    private void appendFailedFiles(StringBuilder sb) {
        if (failedFiles.isEmpty()) {
            return;
        }
        List<String> hit = keysWith(SectionStatus.FAILED);
        if (!hit.isEmpty()) {
            sb.append("\n  Root key(s) inside the failed file(s): ").append(String.join(", ", hit));
        }
        for (String failedFile : failedFiles) {
            sb.append("\n    - ").append(failedFile);
        }
    }

    private static String targetOf(SectionProgress progress) {
        if (progress.getStatus() == SectionStatus.PENDING_NO_ADAPTER) {
            return "NO ADAPTER - no plugin claims this key";
        }
        if (progress.getStatus() == SectionStatus.EMPTY) {
            return "empty in every file - nothing to migrate";
        }
        //a section registered without an ECPluginData reports no owner at all
        String owner = progress.getOwner().isEmpty() ? "unknown plugin" : progress.getOwner();
        String pdSection = progress.getPdSection().isEmpty() ? "?" : progress.getPdSection();
        return pdSection + " (" + owner + ")";
    }

    private List<String> keysWith(SectionStatus status) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, SectionProgress> entry : sections.entrySet()) {
            if (entry.getValue().getStatus() == status) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    private int count(SectionStatus status) {
        return keysWith(status).size();
    }

    private static String pad(String text, int width) {
        StringBuilder padded = new StringBuilder(text);
        while (padded.length() < width) {
            padded.append(' ');
        }
        return padded.toString();
    }
}
