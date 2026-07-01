package br.com.finalcraft.evernifecore.playerdata.storage.legacy;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Final report of a legacy import run:
 * players, entities per collection, sections without an adapter, and failures.
 */
@Getter
public final class LegacyImportReport {

    private int totalFiles;
    private int importedPlayers;
    private int skippedPlayers;                                                  //UUID already on the backend
    private final Map<String, Integer> importedSections = new LinkedHashMap<>(); //section -> count
    private final Map<String, Integer> skippedSections = new LinkedHashMap<>();  //already on the backend
    /** Root keys present in the files but not covered by any registered legacyYaml adapter. */
    private final Map<String, Integer> unmappedRootKeys = new LinkedHashMap<>();
    private final List<String> failedFiles = new ArrayList<>();
    private long durationMillis;

    void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    void addImportedPlayer() {
        importedPlayers++;
    }

    void addSkippedPlayer() {
        skippedPlayers++;
    }

    void addImportedSection(String sectionName) {
        importedSections.merge(sectionName, 1, Integer::sum);
    }

    void addSkippedSection(String sectionName) {
        skippedSections.merge(sectionName, 1, Integer::sum);
    }

    void addUnmappedRootKey(String rootKey) {
        unmappedRootKeys.merge(rootKey, 1, Integer::sum);
    }

    void addFailedFile(String fileName, String reason) {
        failedFiles.add(fileName + " (" + reason + ")");
    }

    public boolean hasFailures() {
        return !failedFiles.isEmpty();
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("Legacy PlayerData import finished in ").append(durationMillis).append("ms:");
        sb.append("\n  Files processed: ").append(totalFiles);
        sb.append("\n  Players imported: ").append(importedPlayers);
        if (skippedPlayers > 0) {
            sb.append(" (skipped ").append(skippedPlayers).append(" already present on the backend)");
        }
        for (Map.Entry<String, Integer> entry : importedSections.entrySet()) {
            sb.append("\n  Section {").append(entry.getKey()).append("}: ").append(entry.getValue()).append(" imported");
            Integer skipped = skippedSections.get(entry.getKey());
            if (skipped != null) {
                sb.append(" (skipped ").append(skipped).append(" already present)");
            }
        }
        for (Map.Entry<String, Integer> entry : skippedSections.entrySet()) {
            if (!importedSections.containsKey(entry.getKey())) {
                sb.append("\n  Section {").append(entry.getKey()).append("}: 0 imported (skipped ")
                        .append(entry.getValue()).append(" already present)");
            }
        }
        if (!unmappedRootKeys.isEmpty()) {
            sb.append("\n  Sections WITHOUT a legacyYaml adapter (left untouched in the archived files):");
            for (Map.Entry<String, Integer> entry : unmappedRootKeys.entrySet()) {
                sb.append("\n    - '").append(entry.getKey()).append("' in ").append(entry.getValue()).append(" file(s)");
            }
        }
        if (!failedFiles.isEmpty()) {
            sb.append("\n  FAILED files (moved to the '-Failed' folder):");
            for (String failedFile : failedFiles) {
                sb.append("\n    - ").append(failedFile);
            }
        }
        return sb.toString();
    }
}
