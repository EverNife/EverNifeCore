package br.com.finalcraft.evernifecore.playerdata.storage.legacy;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.playerdata.storage.legacy.LegacyMigrationMetadata.SectionProgress;
import br.com.finalcraft.evernifecore.playerdata.storage.legacy.LegacyMigrationMetadata.SectionStatus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-time tidy-up that runs on the boot that finally DRAINS the legacy folder (the run whose report
 * has {@code becameCompleteThisRun}). It gathers every artifact the migration produced into a single
 * {@code __LegacyData_V2} folder, so the plugin folder is left clean and the whole migration is
 * documented in one place:
 *
 * <pre>
 * plugins/EverNifeCore/__LegacyData_V2/
 *   PlayerData/                                  (was PlayerData-Imported: the archived originals)
 *   PlayerData-Failed/                           (only if diagnostic copies were ever made)
 *   playerdata-storage-migration-metadata.yml    (moved out of the plugin root)
 *   migration-result.log                         (a human-readable summary of the whole migration)
 * </pre>
 *
 * <p>Renaming the archive back to {@code PlayerData} makes the rollback obvious: an older version reads
 * only a {@code PlayerData/} folder, so restoring is a single move. The now-empty legacy folder is
 * removed. Best-effort and non-fatal: if the critical move fails the consolidation is skipped and the
 * migration stays valid as it was (the archived files remain in {@code PlayerData-Imported}).</p>
 *
 * <p>The first completed migration lands in {@code __LegacyData_V2}. A later one - a re-migration forced
 * by restoring a legacy file after deleting the progress file - finds that folder already holding an
 * earlier archive and gets its own untouched sibling ({@code __LegacyData_V2_2}, {@code _3}, ...),
 * rather than mixing its files into the previous archive under renamed names.</p>
 */
final class LegacyMigrationConsolidator {

    static final String CONSOLIDATED_FOLDER_NAME = "__LegacyData_V2";
    static final String RESULT_LOG_NAME = "migration-result.log";

    private LegacyMigrationConsolidator() {
    }

    /**
     * Consolidates the migration artifacts. Only the critical archive move is all-or-nothing: if it
     * fails, nothing is touched and {@code null} is returned (the caller then keeps the pre-consolidation
     * rollback guidance). The metadata move, the failed-copies move, the result log and the empty-folder
     * cleanup are each best-effort.
     *
     * @return the consolidated archive folder ({@code __LegacyData_V2} or a numbered sibling), or
     *         {@code null} when consolidation was skipped
     */
    static File consolidate(File legacyFolder, File importedFolder, File failedFolder,
                            File metadataFile, LegacyImportReport report) {
        File target = resolveTargetFolder(legacyFolder.getParentFile());

        //captured before the move (which renames importedFolder away): whether any source file was ever
        //archived. When false, no PlayerData/ subfolder is produced, so the result log must not promise one.
        boolean archivedPlayerDataPresent = importedFolder.isDirectory();

        //critical piece: the archived originals become <target>/<legacyName> (e.g. __LegacyData_V2/PlayerData)
        if (archivedPlayerDataPresent) {
            if (!move(importedFolder, new File(target, legacyFolder.getName()))) {
                EverNifeCore.getLog().warning("Legacy migration consolidation skipped: could not move [{}]"
                                + " into [{}]. The migration itself stands; the archived files remain in place.",
                        importedFolder.getName(), target.getName());
                return null;
            }
        } else {
            target.mkdirs(); //nothing to move (all entities were already present), still consolidate the rest
        }

        //diagnostic copies of broken files, if any were ever made across the whole migration
        if (failedFolder.isDirectory()) {
            move(failedFolder, new File(target, failedFolder.getName()));
        }

        //the progress file leaves the plugin root and joins the archive (a re-scan trigger reads its
        //absence + the empty legacy folder as "nothing to do", so moving it out is safe)
        if (metadataFile.isFile()) {
            move(metadataFile, new File(target, metadataFile.getName()));
        }

        writeResultLog(new File(target, RESULT_LOG_NAME), target, report, archivedPlayerDataPresent);

        //the drained legacy folder is now empty - drop it (a no-op if anything non-.yml lingers there)
        legacyFolder.delete();

        return target;
    }

    // ------------------------------------------------------------------

    /**
     * The archive folder this completion writes into: {@code __LegacyData_V2} while it is free, otherwise
     * the first numbered sibling ({@code __LegacyData_V2_2}, {@code _3}, ...) that is. A folder counts as
     * free when it does not exist yet or exists but is empty, so a re-migration never lands its files
     * inside an archive an earlier one already filled.
     */
    private static File resolveTargetFolder(File parent) {
        File first = new File(parent, CONSOLIDATED_FOLDER_NAME);
        if (isFree(first)) {
            return first;
        }
        for (int suffix = 2; ; suffix++) {
            File candidate = new File(parent, CONSOLIDATED_FOLDER_NAME + "_" + suffix);
            if (isFree(candidate)) {
                return candidate;
            }
        }
    }

    /** A folder a fresh archive may claim: absent, or an existing empty directory (never a file). */
    private static boolean isFree(File folder) {
        if (!folder.exists()) {
            return true;
        }
        String[] entries = folder.list();
        return folder.isDirectory() && entries != null && entries.length == 0;
    }

    private static void writeResultLog(File logFile, File consolidatedFolder, LegacyImportReport report,
                                       boolean archivedPlayerDataPresent) {
        try {
            Files.write(logFile.toPath(),
                    buildResultLog(consolidatedFolder, report, archivedPlayerDataPresent).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            EverNifeCore.getLog().warning("Failed to write the legacy migration result log [{}]: {}",
                    logFile.getPath(), e.toString());
        }
    }

    /** The permanent, human-readable record of the whole migration - the numbers, the sections and their owners. */
    private static String buildResultLog(File consolidatedFolder, LegacyImportReport report,
                                         boolean archivedPlayerDataPresent) {
        Map<String, SectionProgress> sections = report.getSections();
        StringBuilder sb = new StringBuilder();
        sb.append("============================================================\n");
        sb.append(" EverNifeCore - Legacy PlayerData migration - RESULT\n");
        sb.append("============================================================\n\n");
        sb.append(" The one-time import of the legacy 'PlayerData/*.yml' files into the\n");
        sb.append(" configured storage backend is COMPLETE. Every artifact it produced was\n");
        sb.append(" consolidated into this folder:\n\n");
        sb.append("   ").append(consolidatedFolder.getPath()).append("/\n");
        if (archivedPlayerDataPresent) {
            sb.append("     PlayerData/                                (the archived original .yml files)\n");
        }
        sb.append("     playerdata-storage-migration-metadata.yml  (the progress file)\n");
        sb.append("     ").append(RESULT_LOG_NAME).append("                       (this file)\n\n");
        if (archivedPlayerDataPresent) {
            sb.append(" ROLLBACK / DOWNGRADE: an older EverNifeCore reads ONLY the original\n");
            sb.append(" 'PlayerData/' folder. Move the 'PlayerData/' above back to the plugin\n");
            sb.append(" folder BEFORE downgrading, or every player is greeted as brand new.\n\n");
        } else {
            sb.append(" Nothing was archived: every legacy entity was already present in the\n");
            sb.append(" backend, so no 'PlayerData/' folder was produced and there is nothing\n");
            sb.append(" to move back on a downgrade.\n\n");
        }

        sb.append("------------------------------------------------------------\n");
        sb.append(" Run summary\n");
        sb.append("------------------------------------------------------------\n");
        appendLine(sb, "Boots (runs)", String.valueOf(report.getRuns()));
        appendLine(sb, "Started at", orDash(report.getStartedAt()));
        appendLine(sb, "Finished at", orDash(report.getLastRunAt()));
        appendLine(sb, "Last run took", report.getDurationMillis() + " ms");
        appendLine(sb, "Files scanned", String.valueOf(report.getFilesTotalFound()));
        appendLine(sb, "Files archived", String.valueOf(report.getFilesFullyImported()));

        sb.append('\n');
        sb.append("------------------------------------------------------------\n");
        sb.append(" Sections (").append(sections.size()).append(")\n");
        sb.append("------------------------------------------------------------\n");
        int totalEntities = 0;
        Set<String> owners = new LinkedHashSet<>();
        for (Map.Entry<String, SectionProgress> entry : sections.entrySet()) {
            SectionProgress progress = entry.getValue();
            totalEntities += progress.getImported();
            if (!progress.getOwner().isEmpty() && progress.getStatus() != SectionStatus.EMPTY) {
                owners.add(progress.getOwner());
            }
            sb.append("   ").append(entry.getKey())
                    .append(" -> ").append(targetOf(progress))
                    .append("  found=").append(progress.getFound())
                    .append("  imported=").append(progress.getImported())
                    .append("  [").append(progress.getStatus().name()).append("]\n");
        }

        sb.append('\n');
        appendLine(sb, "Owner plugins", owners.isEmpty() ? "-" : String.join(", ", owners));
        appendLine(sb, "Entities migrated", String.valueOf(totalEntities));
        if (report.getRuns() > 1) {
            sb.append("   (entities imported on earlier boots are not re-counted above;\n");
            sb.append("    see 'Boots (runs)' - each boot only reports what IT wrote)\n");
        }
        sb.append("============================================================\n");
        return sb.toString();
    }

    private static String targetOf(SectionProgress progress) {
        if (progress.getStatus() == SectionStatus.PENDING_NO_ADAPTER) {
            return "(no adapter)";
        }
        if (progress.getStatus() == SectionStatus.EMPTY) {
            return "(empty)";
        }
        String owner = progress.getOwner().isEmpty() ? "unknown plugin" : progress.getOwner();
        String pdSection = progress.getPdSection().isEmpty() ? "?" : progress.getPdSection();
        return pdSection + " (" + owner + ")";
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        sb.append(" ").append(pad(label, 22)).append(": ").append(value).append('\n');
    }

    private static String orDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private static String pad(String text, int width) {
        StringBuilder padded = new StringBuilder(text);
        while (padded.length() < width) {
            padded.append(' ');
        }
        return padded.toString();
    }

    /**
     * Moves {@code source} to {@code target}, creating the parent. The target folder is already a fresh,
     * empty one ({@link #resolveTargetFolder}), so a clash is not expected; the timestamp fallback is a
     * last-resort net that keeps a stray pre-existing name from ever being overwritten.
     */
    private static boolean move(File source, File target) {
        try {
            target.getParentFile().mkdirs();
            File dest = target;
            if (dest.exists()) {
                dest = new File(target.getParentFile(), target.getName() + "_" + System.currentTimeMillis());
            }
            Files.move(source.toPath(), dest.toPath());
            return true;
        } catch (IOException e) {
            EverNifeCore.getLog().warning("Legacy migration consolidation: failed to move [{}] into [{}]: {}",
                    source.getPath(), target.getPath(), e.toString());
            return false;
        }
    }
}
