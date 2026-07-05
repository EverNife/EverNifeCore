package br.com.finalcraft.evernifecore.worlddata.manager.storage.legacy;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.worlddata.manager.storage.WorldChunkData;
import br.com.finalcraft.evernifecore.worlddata.manager.storage.WorldChunkDataBinding;
import br.com.finalcraft.everyconfig.config.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One-time importer of the legacy region-YAML files of an {@code SVDataManager} into the pluggable
 * storage. Mirrors {@code LegacyPlayerDataImporter}: it reads every {@code <folder>/<world>/r.X.Z.yml}
 * via {@link ConfigFactory}, converts each {@code chunkPos.blockPos -> O} entry into a per-chunk
 * {@link WorldChunkData} entity, and saves it to the backend.
 *
 * <p>Guarantees:</p>
 * <ul>
 *   <li><b>Idempotent</b> - a chunk key already present in the target collection is skipped, so a
 *       re-run never overwrites more recent data.</li>
 *   <li><b>Never deletes a YAML file</b> - processed world folders are moved to
 *       {@code <folder>-Imported/}; the region files stay intact inside them.</li>
 *   <li><b>A broken region file never aborts the run</b> - it is reported and left in place.</li>
 * </ul>
 *
 * @param <O> the concrete block-value type of the owning manager
 */
public final class LegacyWorldDataImporter<O> {

    private final File legacyFolder;
    private final Class<O> valueType;
    private final WorldChunkDataBinding binding;

    public LegacyWorldDataImporter(File legacyFolder, Class<O> valueType, WorldChunkDataBinding binding) {
        this.legacyFolder = legacyFolder;
        this.valueType = valueType;
        this.binding = binding;
    }

    /** Imports every region file under the legacy folder; returns the number of block values imported. */
    public int run() {
        if (legacyFolder == null || !legacyFolder.exists()) {
            return 0;
        }

        int importedValues = 0;
        File[] worldFolders = legacyFolder.listFiles(File::isDirectory);
        if (worldFolders == null) {
            return 0;
        }

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

        //Archive the legacy folder (never deletes): the region files stay intact for auditing.
        archiveFolder(legacyFolder);
        return importedValues;
    }

    private int importRegionFile(String worldName, File regionFile) {
        int imported = 0;
        try {
            Config config = ConfigFactory.open(regionFile);
            for (String chunkPosSerialized : config.getKeys("")) {
                ChunkPos chunkPos = ChunkPos.deserialize(chunkPosSerialized);
                String chunkKey = WorldChunkData.keyOf(worldName, chunkPos);

                //idempotency: a chunk already on the backend is never overwritten by a re-run
                if (binding.getRepository().exists(chunkKey).join()) {
                    continue;
                }

                Map<String, Object> values = new LinkedHashMap<>();
                for (String blockPosSerialized : config.getKeys(chunkPosSerialized)) {
                    try {
                        O value = config.getValue(
                                chunkPosSerialized + "." + blockPosSerialized, valueType);
                        //normalize the block key through BlockPos so the on-disk form matches new writes
                        BlockPos blockPos = BlockPos.deserialize(blockPosSerialized);
                        values.put(blockPos.serialize(), value);
                        imported++;
                    } catch (Exception e) {
                        logWarning("Failed to import block [%s] of chunk [%s] from [%s]: %s",
                                blockPosSerialized, chunkPosSerialized, regionFile.getName(), e.toString());
                    }
                }

                if (!values.isEmpty()) {
                    WorldChunkData chunk = new WorldChunkData(chunkKey);
                    chunk.setValues(values);
                    binding.getRepository().save(chunk).join();
                }
            }
        } catch (Throwable e) {
            logWarning("Failed to import the legacy region file [%s] - it is left in place: %s",
                    regionFile.getAbsolutePath(), e.toString());
        }
        return imported;
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
            logWarning("Failed to archive the legacy worlddata folder [%s] into [%s]: %s",
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
