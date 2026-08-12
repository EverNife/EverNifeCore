package br.com.finalcraft.evernifecore.blockdata.storage.legacy;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.blockdata.storage.WorldChunkData;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everydatabase.manager.CachingManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One-time importer of the region-YAML files an older block store left on disk into the collection a
 * {@code SVWorldDataManager} now owns. It reads every {@code <folder>/<world>/r.X.Z.yml} through
 * {@link ConfigFactory}, turns each {@code chunkPos.blockPos -> O} entry into a per-chunk
 * {@link WorldChunkData} entity, and writes it to the backend.
 *
 * <p>Guarantees:</p>
 * <ul>
 *   <li><b>Idempotent</b> - a chunk key the collection already stores is skipped, so a re-run never
 *       overwrites more recent data. One key-only read per region file answers that for every chunk the
 *       file holds.</li>
 *   <li><b>Never deletes a YAML file</b> - the legacy folder is moved to {@code <folder>-Imported/} when
 *       the run ends, region files intact.</li>
 *   <li><b>A broken region file never aborts the run</b> - it is reported and every other file still
 *       imports. It is archived along with them, so retrying it means moving it back.</li>
 * </ul>
 *
 * <p>Every call blocks until the import is over: it is a boot-time, one-shot migration, and the thread it
 * runs on is the caller's to choose.
 *
 * @param <O> the concrete block-value type of the owning manager
 */
public final class LegacyWorldDataImporter<O> {

    private final File legacyFolder;
    private final Class<O> valueType;
    private final CachingManager<String, WorldChunkData<O>> manager;

    public LegacyWorldDataImporter(File legacyFolder, Class<O> valueType,
                                   CachingManager<String, WorldChunkData<O>> manager) {
        this.legacyFolder = legacyFolder;
        this.valueType = valueType;
        this.manager = manager;
    }

    /** Imports every region file under the legacy folder; returns the number of block values imported. */
    public int run() {
        if (legacyFolder == null || !legacyFolder.exists()) {
            return 0;
        }

        File[] worldFolders = legacyFolder.listFiles(File::isDirectory);
        if (worldFolders == null) {
            return 0;
        }

        int importedValues = 0;
        for (File worldFolder : worldFolders) {
            String worldName = worldFolder.getName();
            File[] regionFiles = worldFolder.listFiles(
                    (dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
            if (regionFiles == null) {
                continue;
            }
            for (File regionFile : regionFiles) {
                importedValues += importRegionFile(worldName, regionFile);
            }
        }

        //archiving (never deletes): the region files stay intact for auditing
        archiveFolder(legacyFolder);
        return importedValues;
    }

    /**
     * Stores the chunks of one region file the collection does not have yet - one key-only read to tell
     * which those are, then one batched write - and returns how many block values landed.
     */
    private int importRegionFile(String worldName, File regionFile) {
        try {
            Map<String, WorldChunkData<O>> chunks = readChunks(worldName, regionFile);
            if (chunks.isEmpty()) {
                return 0;
            }

            //idempotency for the whole file in one round trip: versions() names the keys that are stored
            //without decoding a single entity, where an exists() per chunk would be one call per chunk
            Set<String> alreadyStored = manager.repository().versions(chunks.keySet()).join().keySet();

            int importedValues = 0;
            List<WorldChunkData<O>> pending = new ArrayList<>();
            for (WorldChunkData<O> chunk : chunks.values()) {
                if (alreadyStored.contains(chunk.getChunkKey())) {
                    continue;
                }
                pending.add(chunk);
                importedValues += chunk.getValues().size();
            }
            if (pending.isEmpty()) {
                return 0;
            }

            manager.repository().saveAll(pending).join();
            for (WorldChunkData<O> chunk : pending) {
                //a manager that read the whole collection at build has no idea these arrived afterwards,
                //and would answer nothing for them until something else pulled them in
                manager.seedIfAbsent(chunk.getChunkKey(), chunk);
            }
            return importedValues;
        } catch (Throwable e) {
            logWarning("Failed to import the legacy region file [%s]: %s. Whatever chunk of it reached the"
                            + " backend stays there (a re-run skips those), and the file is archived with the"
                            + " others - move it back into the legacy folder to try it again.",
                    regionFile.getAbsolutePath(), e.toString());
            return 0;
        }
    }

    /** Every chunk a region file holds, as the entity that will be stored, keyed the way the store keys it. */
    private Map<String, WorldChunkData<O>> readChunks(String worldName, File regionFile) {
        Config config = ConfigFactory.open(regionFile);
        Map<String, WorldChunkData<O>> chunks = new LinkedHashMap<>();
        for (String chunkPosSerialized : config.getKeys("")) {
            ChunkPos chunkPos = ChunkPos.deserialize(chunkPosSerialized);

            Map<String, O> values = new LinkedHashMap<>();
            for (String blockPosSerialized : config.getKeys(chunkPosSerialized)) {
                try {
                    O value = config.getValue(
                            chunkPosSerialized + "." + blockPosSerialized, valueType);
                    //normalize the block key through BlockPos so the stored form matches what new writes produce
                    BlockPos blockPos = BlockPos.deserialize(blockPosSerialized);
                    values.put(blockPos.serialize(), value);
                } catch (Exception e) {
                    logWarning("Failed to import block [%s] of chunk [%s] from [%s]: %s",
                            blockPosSerialized, chunkPosSerialized, regionFile.getName(), e.toString());
                }
            }
            if (values.isEmpty()) {
                continue;
            }

            String chunkKey = WorldChunkData.keyOf(worldName, chunkPos);
            WorldChunkData<O> chunk = new WorldChunkData<>(chunkKey);
            chunk.setValues(values);
            chunks.put(chunkKey, chunk);
        }
        return chunks;
    }

    private void archiveFolder(File folder) {
        File target = new File(folder.getParentFile(), folder.getName() + "-Imported");
        try {
            if (target.exists()) {
                target = new File(folder.getParentFile(),
                        folder.getName() + "-Imported_" + System.currentTimeMillis());
            }
            Files.move(folder.toPath(), target.toPath());
        } catch (IOException e) {
            //never deletes and never aborts: a folder that cannot be moved simply stays in place
            //(the idempotency check skips its already-imported chunks on a future run)
            logWarning("Failed to archive the legacy block-data folder [%s] into [%s]: %s",
                    folder.getName(), target.getName(), e.toString());
        }
    }

    private static void logWarning(String message, Object... args) {
        String formatted = args.length == 0 ? message : String.format(message, args);
        try {
            EverNifeCore.getLog().warning(formatted);
        } catch (Throwable noPluginRuntime) {
            //pure JUnit runtime (no ECPluginData configured): fall back to JUL
            Logger.getLogger("EverNifeCore").log(Level.WARNING, formatted);
        }
    }
}
