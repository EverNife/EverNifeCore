package br.com.finalcraft.evernifecore.playerdata.storage.legacy;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.playerdata.storage.PDSectionBinding;
import br.com.finalcraft.evernifecore.playerdata.storage.PlayerDataBinding;
import br.com.finalcraft.everylibs.executors.util.FCExecutorsUtil;
import br.com.finalcraft.everydatabase.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bulk, single-pass importer of the legacy per-player YAML files into the pluggable storage.
 * Each file is split into independent entities: the base block becomes the {@link PlayerData}
 * entity ({@link LegacyPlayerDataYamlConverter}), and every root key covered by a registered
 * {@code legacyYaml(rootKey, adapter)} binding is routed by that adapter into the section's own
 * collection/backend.
 *
 * <p>Guarantees:</p>
 * <ul>
 *   <li><b>Idempotent</b> - entities whose UUID is already present in the target collection
 *       are skipped (a re-run never overwrites more recent data).</li>
 *   <li><b>Never deletes a YAML file</b> - processed files go to {@code PlayerData-Imported/},
 *       broken ones to {@code PlayerData-Failed/}; the section blocks remain intact inside them.</li>
 *   <li><b>A broken file never aborts the run</b> - it is archived as failed and reported.</li>
 * </ul>
 *
 * <p>Every run rewrites the {@link LegacyMigrationMetadata} progress file with each root key it
 * found - the ones no adapter claims included, since those are what brings the import back on a
 * later boot, once the plugin owning them is installed.</p>
 */
public final class LegacyPlayerDataImporter {

    /** The base block of a legacy file - the importer converts it itself, no adapter involved. */
    private static final String BASE_ROOT_KEY = "PlayerData";
    /** The player-cooldown block: read by nobody today, but claimed here so it is not reported as orphan. */
    private static final String LEGACY_COOLDOWN_ROOT_KEY = "Cooldown";

    private final File legacyFolder;
    private final PlayerDataBinding playerDataBinding;
    private final List<PDSectionBinding<PDSection>> adapterBindings = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public LegacyPlayerDataImporter(File legacyFolder, PlayerDataBinding playerDataBinding,
                                    Collection<? extends PDSectionBinding<? extends PDSection>> sectionBindings) {
        this.legacyFolder = legacyFolder;
        this.playerDataBinding = playerDataBinding;
        for (PDSectionBinding<? extends PDSection> binding : sectionBindings) {
            if (binding.getConfiguration().getLegacyYamlRootKey() != null
                    && binding.getConfiguration().getLegacyYamlAdapter() != null) {
                adapterBindings.add((PDSectionBinding<PDSection>) binding);
            }
        }
    }

    public LegacyImportReport run() {
        long start = System.currentTimeMillis();
        LegacyImportReport report = new LegacyImportReport();

        File[] files = legacyFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            report.setDurationMillis(System.currentTimeMillis() - start);
            return report;
        }
        report.setTotalFiles(files.length);

        //Phase A - parse + conversion + routing + idempotency check (one virtual thread per file)
        Queue<ParsedFile> parseResults = new ConcurrentLinkedQueue<>();
        ExecutorService executor = FCExecutorsUtil.createVirtualExecutorIfPossible("legacy-import");
        try {
            CountDownLatch latch = new CountDownLatch(files.length);
            for (File file : files) {
                executor.execute(() -> {
                    try {
                        parseResults.add(parseFile(file));
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("The legacy PlayerData import was interrupted!", e);
        } finally {
            executor.shutdown();
        }

        List<ParsedFile> parsed = new ArrayList<>(parseResults);
        parsed.sort(Comparator.comparing(parsedFile -> parsedFile.file.getName()));

        //Phase B - one saveAll batch per collection, with per-entity fallback
        savePlayerDataBatch(parsed);
        saveSectionBatches(parsed);

        //Phase C - archiving (never deletes) + report
        File importedFolder = new File(legacyFolder.getParentFile(), legacyFolder.getName() + "-Imported");
        File failedFolder = new File(legacyFolder.getParentFile(), legacyFolder.getName() + "-Failed");
        int archivedFiles = 0;
        int failedFiles = 0;
        for (ParsedFile parsedFile : parsed) {
            if (parsedFile.failReason != null) {
                report.addFailedFile(parsedFile.file.getName(), parsedFile.failReason);
                moveFile(parsedFile.file, failedFolder);
                failedFiles++;
                continue;
            }
            if (moveFile(parsedFile.file, importedFolder)) {
                archivedFiles++;
            }
            if (parsedFile.baseAlreadyPresent) {
                report.addSkippedPlayer();
            } else {
                report.addImportedPlayer();
            }
            for (SectionEntry entry : parsedFile.sections) {
                String sectionName = entry.binding.getPdSectionClass().getSimpleName();
                if (entry.alreadyPresent) {
                    report.addSkippedSection(sectionName);
                } else {
                    report.addImportedSection(sectionName);
                }
            }
            for (String unmappedKey : parsedFile.unmappedKeys) {
                report.addUnmappedRootKey(unmappedKey);
            }
        }

        //Phase D - progress file: what the next boot's trigger reads instead of counting rows
        writeProgress(parsed, files.length, archivedFiles, failedFiles);

        report.setDurationMillis(System.currentTimeMillis() - start);
        return report;
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Phase A - per-file conversion (runs on a virtual thread)
    // -----------------------------------------------------------------------------------------------------------------------------//

    private ParsedFile parseFile(File file) {
        ParsedFile parsed = new ParsedFile(file);
        try {
            Config legacyConfig = ConfigFactory.open(file);
            parsed.playerData = LegacyPlayerDataYamlConverter.convertBase(legacyConfig);
            UUID uuid = parsed.playerData.getUniqueId();
            parsed.baseAlreadyPresent = playerDataBinding.getRepository().exists(uuid).join();

            for (PDSectionBinding<PDSection> binding : adapterBindings) {
                String rootKey = binding.getConfiguration().getLegacyYamlRootKey();
                if (!legacyConfig.contains(rootKey)) {
                    continue;
                }
                PDSection section = binding.getConfiguration().getLegacyYamlAdapter()
                        .apply(legacyConfig.getConfigSection(rootKey));
                if (section == null) {
                    continue; //the adapter chose to skip this player
                }
                section.attachPlayerData(parsed.playerData);
                boolean alreadyPresent = binding.getRepository().exists(uuid).join();
                parsed.sections.add(new SectionEntry(parsed, binding, section, alreadyPresent));
            }

            Set<String> mappedRootKeys = mappedRootKeys();
            for (String rootKey : legacyConfig.getKeys()) {
                //an empty block has nothing to migrate, so it never needs an adapter
                parsed.rootKeys.put(rootKey, !legacyConfig.getKeys(rootKey).isEmpty());
                if (!mappedRootKeys.contains(rootKey)) {
                    parsed.unmappedKeys.add(rootKey);
                }
            }
        } catch (Throwable e) {
            parsed.failReason = e.getMessage() != null ? e.getMessage() : e.toString();
            logWarning("Failed to convert the legacy PlayerData file [%s] - it will be moved to"
                    + " the '-Failed' folder.", file.getName());
            e.printStackTrace();
        }
        return parsed;
    }

    /** Root keys this run knows how to handle - the same for every file, since it only depends on the bindings. */
    private Set<String> mappedRootKeys() {
        Set<String> mapped = new HashSet<>();
        mapped.add(BASE_ROOT_KEY);
        mapped.add(LEGACY_COOLDOWN_ROOT_KEY);
        for (PDSectionBinding<PDSection> binding : adapterBindings) {
            mapped.add(binding.getConfiguration().getLegacyYamlRootKey());
        }
        return mapped;
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Phase B - batched saves per collection
    // -----------------------------------------------------------------------------------------------------------------------------//

    private void savePlayerDataBatch(List<ParsedFile> parsed) {
        List<ParsedFile> pending = new ArrayList<>();
        for (ParsedFile parsedFile : parsed) {
            if (parsedFile.failReason == null && !parsedFile.baseAlreadyPresent) {
                pending.add(parsedFile);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        List<PlayerData> entities = new ArrayList<>(pending.size());
        for (ParsedFile parsedFile : pending) {
            entities.add(parsedFile.playerData);
        }
        if (trySaveAll(playerDataBinding.getRepository(), entities)) {
            return;
        }
        for (ParsedFile parsedFile : pending) { //per-entity fallback
            try {
                playerDataBinding.getRepository().save(parsedFile.playerData).join();
            } catch (Throwable e) {
                markSaveFailed(parsedFile, "PlayerData", e);
            }
        }
    }

    private void saveSectionBatches(List<ParsedFile> parsed) {
        Map<PDSectionBinding<PDSection>, List<SectionEntry>> byBinding = new LinkedHashMap<>();
        for (ParsedFile parsedFile : parsed) {
            if (parsedFile.failReason != null) {
                continue;
            }
            for (SectionEntry entry : parsedFile.sections) {
                if (!entry.alreadyPresent) {
                    byBinding.computeIfAbsent(entry.binding, k -> new ArrayList<>()).add(entry);
                }
            }
        }
        for (Map.Entry<PDSectionBinding<PDSection>, List<SectionEntry>> bucket : byBinding.entrySet()) {
            PDSectionBinding<PDSection> binding = bucket.getKey();
            List<PDSection> entities = new ArrayList<>(bucket.getValue().size());
            for (SectionEntry entry : bucket.getValue()) {
                entities.add(entry.section);
            }
            if (trySaveAll(binding.getRepository(), entities)) {
                continue;
            }
            for (SectionEntry entry : bucket.getValue()) { //per-entity fallback
                try {
                    binding.getRepository().save(entry.section).join();
                } catch (Throwable e) {
                    markSaveFailed(entry.owner, "PDSection {" + binding.getPdSectionClass().getSimpleName() + "}", e);
                }
            }
        }
    }

    private static <V> boolean trySaveAll(Repository<UUID, V> repository, List<V> entities) {
        try {
            repository.saveAll(entities).join();
            return true;
        } catch (Throwable batchFailure) {
            return false; //the caller retries entity by entity
        }
    }

    private void markSaveFailed(ParsedFile parsedFile, String what, Throwable error) {
        String reason = "failed to save " + what + ": "
                + (error.getMessage() != null ? error.getMessage() : error.toString());
        //a partially saved file is archived as failed; the next 'force' run skips
        //what ALREADY reached the backend (idempotency) and retries only the rest
        parsedFile.failReason = parsedFile.failReason == null ? reason : parsedFile.failReason + "; " + reason;
        logWarning("Legacy import of [%s]: %s", parsedFile.file.getName(), reason);
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Phase C - archiving
    // -----------------------------------------------------------------------------------------------------------------------------//

    /** @return true when the file left the legacy folder. */
    private boolean moveFile(File source, File targetFolder) {
        try {
            targetFolder.mkdirs();
            File target = new File(targetFolder, source.getName());
            if (target.exists()) {
                String name = source.getName();
                target = new File(targetFolder, name.substring(0, name.length() - ".yml".length())
                        + "_" + System.currentTimeMillis() + ".yml");
            }
            Files.move(source.toPath(), target.toPath());
            return true;
        } catch (IOException e) {
            //never deletes and never aborts: a file that cannot be moved simply stays in place
            //(idempotency skips its entities again on a future run)
            logWarning("Failed to archive the legacy PlayerData file [%s] into [%s]: %s",
                    source.getName(), targetFolder.getName(), e.toString());
            return false;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Phase D - progress file
    // -----------------------------------------------------------------------------------------------------------------------------//

    private void writeProgress(List<ParsedFile> parsed, int totalFound, int archivedFiles, int failedFiles) {
        File progressFile = LegacyMigrationMetadata.fileOf(legacyFolder);
        LegacyMigrationMetadata metadata = LegacyMigrationMetadata.load(progressFile); //keeps started-at + runs
        metadata.beginRun();
        //whatever did not reach an archive folder is still sitting in the legacy folder
        metadata.setFiles(totalFound, archivedFiles, totalFound - archivedFiles - failedFiles, failedFiles);
        metadata.replaceSections(discoverSections(parsed));
        metadata.recomputeComplete();
        metadata.save(progressFile);
    }

    /**
     * Aggregates every root key seen in the run - the ones nobody migrates included, since a key
     * without an adapter is exactly what a later boot must come back for.
     */
    private Map<String, LegacyMigrationMetadata.SectionProgress> discoverSections(List<ParsedFile> parsed) {
        Map<String, Integer> foundByRootKey = new LinkedHashMap<>();
        Map<String, Integer> importedByRootKey = new LinkedHashMap<>();
        Set<String> rootKeysWithContent = new HashSet<>();
        for (ParsedFile parsedFile : parsed) {
            for (Map.Entry<String, Boolean> rootKey : parsedFile.rootKeys.entrySet()) {
                foundByRootKey.merge(rootKey.getKey(), 1, Integer::sum);
                if (rootKey.getValue()) {
                    rootKeysWithContent.add(rootKey.getKey());
                }
            }
            if (parsedFile.failReason != null) {
                continue; //nothing of this file reached the backend
            }
            if (!parsedFile.baseAlreadyPresent) {
                importedByRootKey.merge(BASE_ROOT_KEY, 1, Integer::sum);
            }
            for (SectionEntry entry : parsedFile.sections) {
                if (!entry.alreadyPresent) {
                    importedByRootKey.merge(entry.binding.getConfiguration().getLegacyYamlRootKey(), 1, Integer::sum);
                }
            }
        }

        Map<String, PDSectionBinding<PDSection>> bindingByRootKey = new LinkedHashMap<>();
        for (PDSectionBinding<PDSection> binding : adapterBindings) {
            bindingByRootKey.put(binding.getConfiguration().getLegacyYamlRootKey(), binding);
        }
        Set<String> mappedRootKeys = mappedRootKeys();

        Map<String, LegacyMigrationMetadata.SectionProgress> sections = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> found : foundByRootKey.entrySet()) {
            String rootKey = found.getKey();
            LegacyMigrationMetadata.SectionStatus status;
            if (!rootKeysWithContent.contains(rootKey)) {
                status = LegacyMigrationMetadata.SectionStatus.EMPTY;
            } else if (mappedRootKeys.contains(rootKey)) {
                status = LegacyMigrationMetadata.SectionStatus.DONE;
            } else {
                status = LegacyMigrationMetadata.SectionStatus.PENDING_NO_ADAPTER;
            }
            sections.put(rootKey, new LegacyMigrationMetadata.SectionProgress(status, found.getValue(),
                    importedByRootKey.getOrDefault(rootKey, 0), ownerOf(rootKey, bindingByRootKey.get(rootKey)),
                    pdSectionOf(rootKey, bindingByRootKey.get(rootKey))));
        }
        return sections;
    }

    private static String ownerOf(String rootKey, PDSectionBinding<PDSection> binding) {
        if (BASE_ROOT_KEY.equals(rootKey)) {
            return "EverNifeCore";
        }
        if (binding == null || binding.getConfiguration().getPluginData() == null) {
            return "";
        }
        return binding.getConfiguration().getPluginData().getMetaInfo().getName();
    }

    private static String pdSectionOf(String rootKey, PDSectionBinding<PDSection> binding) {
        if (BASE_ROOT_KEY.equals(rootKey)) {
            return PlayerData.class.getSimpleName();
        }
        return binding == null ? "" : binding.getPdSectionClass().getSimpleName();
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Internals
    // -----------------------------------------------------------------------------------------------------------------------------//

    private static final class ParsedFile {
        final File file;
        PlayerData playerData;                                    //null when the parse failed
        boolean baseAlreadyPresent;
        String failReason;                                        //non-null = archive as failed
        final List<SectionEntry> sections = new ArrayList<>();
        final List<String> unmappedKeys = new ArrayList<>();
        /** Every root key of the file (adapter or not) -> whether it holds anything. */
        final Map<String, Boolean> rootKeys = new LinkedHashMap<>();

        ParsedFile(File file) {
            this.file = file;
        }
    }

    private static final class SectionEntry {
        final ParsedFile owner;
        final PDSectionBinding<PDSection> binding;
        final PDSection section;
        final boolean alreadyPresent;

        SectionEntry(ParsedFile owner, PDSectionBinding<PDSection> binding, PDSection section, boolean alreadyPresent) {
            this.owner = owner;
            this.binding = binding;
            this.section = section;
            this.alreadyPresent = alreadyPresent;
        }
    }

    private static void logWarning(String message, Object... args) {
        String formatted = args.length == 0 ? message : String.format(message, args);
        try {
            EverNifeCore.getLog().warning(formatted);
        } catch (Throwable noPluginRuntime) {
            //pure JUnit runtime (no ECPluginData configured): falls back to JUL
            Logger.getLogger("EverNifeCore").log(Level.WARNING, formatted);
        }
    }
}
