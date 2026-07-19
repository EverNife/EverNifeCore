package br.com.finalcraft.evernifecore.worlddata.manager;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.worlddata.BlockMetaData;
import br.com.finalcraft.evernifecore.worlddata.ServerData;
import br.com.finalcraft.evernifecore.worlddata.WorldData;
import br.com.finalcraft.evernifecore.worlddata.manager.storage.WorldChunkData;
import br.com.finalcraft.evernifecore.worlddata.manager.storage.WorldChunkDataBinding;
import br.com.finalcraft.evernifecore.worlddata.manager.storage.legacy.LegacyWorldDataImporter;
import br.com.finalcraft.everydatabase.Storage;
import lombok.Data;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Spatial per-block data store ({@code world -> chunk -> block -> O}) persisted on the EveryDatabase
 * backend. Each {@code (world, chunk)} is one {@link WorldChunkData} entity holding a
 * {@code block -> O} map; {@code O} is a Jackson-serializable POJO.
 *
 * <p>The in-memory model ({@link ServerData}/{@link WorldData}/{@link BlockMetaData}) is unchanged:
 * {@link #load()} mirrors the whole dataset into {@code worldDataMap}, and {@link #save()} flushes
 * the pending block changes into the chunk entities and lets the {@code CachingManager} write them
 * back in a batch.</p>
 *
 * <p><b>Threading.</b> The block-change writers ({@link #onBlockMetaSet}/{@link #onBlockMetaRemove})
 * may run on any thread. They append to the pending-operations set while holding an internal
 * monitor; {@link #save()} coordinates with them by swapping that set for a fresh one under the same
 * monitor and draining the captured snapshot, so its swap-and-drain never races an in-flight write
 * and no queued operation is lost. {@link #load()} clears the pending set under the same monitor.
 * The backing set is a concurrent, insertion-ordered set: the set-then-remove ordering of the same
 * block is preserved.</p>
 *
 * <p>Build one with {@link #targeting(Class)}, supplying the backend {@link Storage} and collection.
 * A {@code legacyFolder} enables the one-time region-YAML import (see {@link #importLegacy()}).</p>
 *
 * @param <O> the block-value type (must be Jackson-serializable)
 */
public class SVDataManager<O> extends ServerData<O> {

    private final Class<O> targetClass;
    private final File legacyFolder;
    private final WorldChunkDataBinding binding;

    private final transient String targetClassName;

    //stable monitor guarding the pending-operations set; NOT the set itself, which save() swaps out
    private final transient Object saveLock = new Object();

    private transient Set<BlockMetaDataOperation<O>> blocksToSaveOrRemove;

    public SVDataManager(Class<O> targetClass, File legacyFolder, WorldChunkDataBinding binding) {
        this.targetClass = targetClass;
        this.legacyFolder = legacyFolder;
        this.binding = binding;
        this.blocksToSaveOrRemove = Collections.synchronizedSet(new LinkedHashSet<>());
        this.targetClassName = (targetClass == null ? "null" : targetClass.getSimpleName());
    }

    public Class<O> getTargetClass() {
        return targetClass;
    }

    public File getLegacyFolder() {
        return legacyFolder;
    }

    public WorldChunkDataBinding getBinding() {
        return binding;
    }

    @Override
    public void onBlockMetaSet(BlockMetaData blockMetaData) {
        synchronized (saveLock) {
            this.blocksToSaveOrRemove.add(new BlockMetaDataOperation<>(blockMetaData, false));
        }
    }

    @Override
    public void onBlockMetaRemove(BlockMetaData blockMetaData) {
        synchronized (saveLock) {
            this.blocksToSaveOrRemove.add(new BlockMetaDataOperation<>(blockMetaData, true));
        }
    }

    public Set<BlockMetaDataOperation<O>> getBlocksToSaveOrRemove() {
        return blocksToSaveOrRemove;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Persistence
    // -----------------------------------------------------------------------------------------------------------------

    public void save() {
        if (this.blocksToSaveOrRemove.isEmpty()) {
            return;
        }

        Set<BlockMetaDataOperation<O>> pending;
        synchronized (saveLock) {
            pending = blocksToSaveOrRemove;
            blocksToSaveOrRemove = Collections.synchronizedSet(new LinkedHashSet<>());
        }

        for (BlockMetaDataOperation<O> operation : pending) {
            BlockPos blockPos = operation.getBlockMetaData().getBlockPos();
            ChunkPos chunkPos = operation.getBlockMetaData().getChunkData().getChunkPos();
            String worldName = operation.getBlockMetaData().getChunkData().getWorldData().getWorldName();
            String chunkKey = WorldChunkData.keyOf(worldName, chunkPos);
            String blockKey = blockPos.serialize();

            WorldChunkData chunk = binding.getManager()
                    .getOrCompute(chunkKey, WorldChunkData::new).join();

            if (operation.isRemove()) {
                chunk.removeValue(blockKey);
            } else {
                chunk.putValue(blockKey, operation.getBlockMetaData().getValue());
            }

            //an emptied chunk is deleted from the backend (the equivalent of the old empty-region-file
            //removal); otherwise the dirty entity is flushed below
            if (chunk.isEmpty()) {
                binding.getManager().deleteAndEvict(chunkKey).join();
                chunk.markClean();
            }
        }

        binding.getManager().flushDirty().join();
    }

    public int load() {
        this.worldDataMap.clear();
        synchronized (saveLock) {
            blocksToSaveOrRemove.clear();
        }

        long start = System.currentTimeMillis();
        binding.getManager().preloadAll().join();

        int loadedObjects = 0;
        for (WorldChunkData chunk : binding.getManager().cachedValues()) {
            String worldName = WorldChunkData.worldOf(chunk.getChunkKey());
            WorldData<O> worldData = this.getOrCreateWorldData(worldName);
            synchronized (worldData) {
                for (Map.Entry<String, Object> entry : chunk.getValues().entrySet()) {
                    BlockPos blockPos = BlockPos.deserialize(entry.getKey());
                    @SuppressWarnings("unchecked")
                    O value = (O) entry.getValue();
                    worldData.setBlockData(blockPos, value);
                    loadedObjects++;
                }
            }
        }

        //setBlockData routed every load through onBlockMetaSet, queuing a needless re-save
        synchronized (saveLock) {
            blocksToSaveOrRemove.clear();
        }

        try {
            EverNifeCore.getLog().debugModule(
                    ECDebugModule.SVDATA_MANAGER,
                    "SVDataManager<%s>.load() - Loaded %s object instances. (took %s ms)",
                    this.targetClassName,
                    loadedObjects,
                    System.currentTimeMillis() - start
            );
        } catch (Throwable noPluginRuntime) {
            //pure JUnit runtime (no ECPluginData configured): the debug line is best-effort
        }
        return loadedObjects;
    }

    /** Runs the one-time legacy region-YAML import (no-op when no {@code legacyFolder} is set). */
    public int importLegacy() {
        if (legacyFolder == null) {
            return 0;
        }
        return new LegacyWorldDataImporter<>(legacyFolder, targetClass, binding).run();
    }

    /**
     * Utility class to help identify BlockPos that should be saved
     */
    @Data
    private static class BlockMetaDataOperation<O> {
        private final BlockMetaData<O> blockMetaData;
        private final boolean remove;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  StepBuilder
    // -----------------------------------------------------------------------------------------------------------------

    public static <O> IStepStorage<O> targeting(Class<O> watchedClass) {
        return new BuilderImp<>(watchedClass);
    }

    public interface IStepStorage<O> {
        IBuilder<O> on(Storage storage, String collection);
    }

    public interface IBuilder<O> {
        /** The legacy region-YAML folder to import once ({@code <folder>/<world>/r.X.Z.yml}); optional. */
        IBuilder<O> importingLegacyFrom(File legacyFolder);

        SVDataManager<O> build();
    }

    public static class BuilderImp<O> implements IStepStorage<O>, IBuilder<O> {
        private final Class<O> watchedClass;
        private Storage storage;
        private String collection;
        private File legacyFolder = null;

        public BuilderImp(Class<O> watchedClass) {
            this.watchedClass = watchedClass;
        }

        @Override
        public IBuilder<O> on(Storage storage, String collection) {
            this.storage = storage;
            this.collection = collection;
            return this;
        }

        @Override
        public IBuilder<O> importingLegacyFrom(File legacyFolder) {
            this.legacyFolder = legacyFolder;
            return this;
        }

        @Override
        public SVDataManager<O> build() {
            WorldChunkDataBinding binding = WorldChunkDataBinding.create(watchedClass, collection, storage);
            return new SVDataManager<>(watchedClass, legacyFolder, binding);
        }
    }
}
