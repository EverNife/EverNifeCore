package br.com.finalcraft.evernifecore.minecraft.worlddata.manager;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.minecraft.region.RegionPos;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.vector.ChunkPos;
import br.com.finalcraft.evernifecore.minecraft.worlddata.BlockMetaData;
import br.com.finalcraft.evernifecore.minecraft.worlddata.ServerData;
import br.com.finalcraft.evernifecore.minecraft.worlddata.WorldData;
import br.com.finalcraft.evernifecore.minecraft.worlddata.manager.config.ServerConfigData;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import lombok.Data;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class SVDataManager<O> extends ServerData<O>{

    private final File mainFolder; //Main folder to store this data
    private final Class<O> targetClass;

    private final BiFunction<ConfigSection, WorldBlockPos, O> onConfigLoad; //How to load the object from file
    private final BiConsumer<ConfigSection, BlockMetaData<O>> onConfigSave; //How to save the object to the file
    private final BiConsumer<ConfigSection, BlockMetaData<O>> onConfigRemove; //How to remove the object from the file
    private final ServerConfigData configData;

    private final transient String targetClassName;

    private transient LinkedHashSet<BlockMetaDataOperation<O>> blocksToSaveOrRemove;

    public SVDataManager(File mainFolder, Class<O> targetClass, BiFunction<ConfigSection, WorldBlockPos, O> onConfigLoad, BiConsumer<ConfigSection, BlockMetaData<O>> onConfigSave, BiConsumer<ConfigSection, BlockMetaData<O>> onConfigRemove) {
        this.mainFolder = mainFolder;
        this.targetClass = targetClass;
        this.onConfigLoad = onConfigLoad;
        this.onConfigSave = onConfigSave;
        this.onConfigRemove = onConfigRemove;
        this.configData = new ServerConfigData(mainFolder);
        this.blocksToSaveOrRemove = new LinkedHashSet<>();
        this.targetClassName = (targetClass == null ? "null" : targetClass.getSimpleName());
    }

    public Class<O> getTargetClass() {
        return targetClass;
    }

    @Override
    public void onBlockMetaSet(BlockMetaData blockMetaData) {
        this.blocksToSaveOrRemove.add(new BlockMetaDataOperation<>(blockMetaData, false));
    }

    @Override
    public void onBlockMetaRemove(BlockMetaData blockMetaData) {
        this.blocksToSaveOrRemove.add(new BlockMetaDataOperation<>(blockMetaData, true));
    }

    public File getMainFolder() {
        return mainFolder;
    }

    public ServerConfigData getConfigData() {
        return configData;
    }

    public LinkedHashSet<BlockMetaDataOperation<O>> getBlocksToSaveOrRemove() {
        return blocksToSaveOrRemove;
    }

    public void save(){
        if (this.blocksToSaveOrRemove.size() == 0){
            return;
        }

        LinkedHashSet<BlockMetaDataOperation<O>> blocksOldReference = this.blocksToSaveOrRemove;
        synchronized (blocksToSaveOrRemove){
            blocksToSaveOrRemove = new LinkedHashSet<>();
        }

        HashSet<Config> configsToSave = new HashSet<>();
        for (BlockMetaDataOperation<O> operation : blocksOldReference) {
            BlockPos blockPos = operation.getBlockMetaData().getBlockPos();
            ChunkPos chunkPos = operation.getBlockMetaData().getChunkData().getChunkPos();
            RegionPos regionPos = chunkPos.getRegionPos();
            String worldName = operation.getBlockMetaData().getChunkData().getWorldData().getWorldName();

            Config config = configData.getOrCreateConfigData(worldName, regionPos);
            ConfigSection section = config.getConfigSection(chunkPos + "." + blockPos);

            if (operation.isRemove()){
                this.onConfigRemove.accept(section, operation.getBlockMetaData());
            }else {
                this.onConfigSave.accept(section, operation.getBlockMetaData());
            }

            configsToSave.add(config);
        }

        for (Config config : configsToSave) {
            config.save();
        }

        //Finally, delete all empty Configs, no need to remove them from the map though
        for (Config config : this.configData.getAllConfigs()) {
            boolean isEmpty = config.getKeys().isEmpty()
                    ? true
                    : config.getKeys().stream().filter(s -> !config.getKeys(s).isEmpty()).findFirst().isPresent() == false;

            if (isEmpty){
                if (config.getTheFile().exists()){
                    FileUtils.deleteQuietly(config.getTheFile());
                }
            }
        }
    }

    public int load(){
        this.worldDataMap.clear();
        this.configData.getConfigMap().clear();
        this.blocksToSaveOrRemove.clear();

        AtomicInteger loadedObjects = new AtomicInteger();

        if (!this.mainFolder.exists()){
            return 0;
        }

        Queue<Config> configs = new ConcurrentLinkedQueue<>();
        final Phaser phaser = new Phaser(1); // Similar to CountDownLatch, but it can be increased/decreased while it's running

        long start = System.currentTimeMillis();
        FileUtils.iterateFiles(mainFolder, new String[]{"yml"}, true)
                .forEachRemaining(file -> {
                    phaser.register(); //Increase the phaser count
                    FCScheduler.runAsync(() -> {
                        try {
                            configs.add(new Config(file).enableSmartCache());
                            loadedObjects.incrementAndGet();
                        }catch (Exception e){
                            e.printStackTrace();
                        }finally {
                            phaser.arriveAndDeregister();
                        }
                    });
                });

        phaser.arriveAndAwaitAdvance(); // await any async tasks to complete

        EverNifeCore.getLog().debugModule(
                ECDebugModule.SVDATA_MANAGER,
                "SVDataManager<%s>.load() [LOADING-PHASE] - Loaded  %s yaml-configs.     (took %s ms)",
                this.targetClassName,
                loadedObjects.get(),
                System.currentTimeMillis() - start
        );

        loadedObjects.set(0);

        start = System.currentTimeMillis();
        for (Config config : configs) {
            phaser.register();
            FCScheduler.runAsync(() -> {
                try {
                    //FileName is   'r.Xcoord.zCoord.yml'
                    String[] split = config.getTheFile().getName().split(Pattern.quote("."));

                    if (!split[0].equals("r")){
                        EverNifeCore.getLog().severe("Failed to load config file [%s] - Invalid file name", config.getTheFile().getName());
                        return;
                    }

                    Integer xCoord = FCInputReader.parseInt(split[1]);
                    Integer zCoord = FCInputReader.parseInt(split[2]);
                    RegionPos regionPos = new RegionPos(xCoord, zCoord);
                    String worldName = config.getTheFile().getParentFile().getName();
                    synchronized (this.configData){
                        this.configData.setConfigData(worldName, regionPos, config);
                    }

                    WorldData<O> worldData = this.getOrCreateWorldData(worldName);
                    List<Runnable> insertOperations = new ArrayList<>();

                    for (String chunkPosSerialized : config.getKeys("")) {
                        for (String blockPosSerialized : config.getKeys(chunkPosSerialized)) {
                            BlockPos blockPos = BlockPos.deserialize(blockPosSerialized);

                            ConfigSection section = config.getConfigSection(chunkPosSerialized + "." + blockPosSerialized);
                            try {
                                O value = this.onConfigLoad.apply(section, new WorldBlockPos(worldName, blockPos));
                                insertOperations.add(() -> {
                                    worldData.setBlockData(blockPos, value);
                                });
                                loadedObjects.incrementAndGet();
                            }catch (Exception e){
                                EverNifeCore.warning(String.format("Failed to load BlockPos Data from the config at [%s]",  section.toString()));
                                e.printStackTrace();
                            }
                        }
                    }

                    synchronized (worldData){
                        insertOperations.forEach(Runnable::run);
                    }
                }catch (Exception e){
                    EverNifeCore.warning(String.format("Failed to load RegionData for the SVDataManager at [%s]",  config.getAbsolutePath()));
                    e.printStackTrace();
                }finally {
                    phaser.arriveAndDeregister();
                }
            });
        }

        phaser.arriveAndAwaitAdvance(); // await any async tasks to complete
        EverNifeCore.getLog().debugModule(
                ECDebugModule.SVDATA_MANAGER,
                "SVDataManager<%s>.load() [EXTRACT-PHASE] - Extract %s object instances. (took %s ms)",
                this.targetClassName,
                loadedObjects.get(),
                System.currentTimeMillis() - start
        );
        EverNifeCore.getLog().debug("SVDataManager.load() [PHASE-CONFIG-EXTRACT] - Took %s ms to load [%s] objects", System.currentTimeMillis() - start, loadedObjects.get());

        this.blocksToSaveOrRemove.clear();//We need to clear again because several items may have been set!
        return loadedObjects.get();
    }

    /**
     * Utility class to help identify BlockPos that should be saved
     */
    @Data
    private class BlockMetaDataOperation<O> {
        private final BlockMetaData<O> blockMetaData;
        private final boolean remove;
    }

    @Data
    public static class WorldBlockPos {
        private final String worldName;
        private final BlockPos blockPos;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  StepBuilder
    // -----------------------------------------------------------------------------------------------------------------

    public static <O> IStepStoreAtFolder<O> targeting(Class<O> watchedClass){
        return new BuilderImp(watchedClass);
    }

    public static interface IStepStoreAtFolder<O> {
        public IBuilder<O> storeAtFolder(File baseFolder);
    }

    public static interface IBuilder<O> {
        public IBuilder<O> setOnConfigLoad(BiFunction<ConfigSection, WorldBlockPos, O> onConfigLoad);
        public IBuilder<O> setOnConfigSave(BiConsumer<ConfigSection, BlockMetaData<O>> onConfigSave);
        public IBuilder<O> setOnConfigRemove(BiConsumer<ConfigSection, BlockMetaData<O>> onConfigRemove);
        public SVDataManager<O> build();
    }

    public static class BuilderImp<O> implements IStepStoreAtFolder<O>, IBuilder<O> {
        private final Class<O> watchedClass;
        private File baseFolder = null; //Main folder to store this data
        private BiFunction<ConfigSection, WorldBlockPos, O> onConfigLoad = null; //How to load the object from file
        private BiConsumer<ConfigSection, BlockMetaData<O>> onConfigSave = null; //How to save the object to the file
        private BiConsumer<ConfigSection, BlockMetaData<O>> onConfigRemove = null; //How to remove the object from the file

        public BuilderImp(Class<O> watchedClass) {
            this.watchedClass = watchedClass;
        }

        @Override
        public BuilderImp<O> storeAtFolder(File baseFolder) {
            this.baseFolder = baseFolder;
            return this;
        }

        @Override
        public IBuilder<O> setOnConfigLoad(BiFunction<ConfigSection, WorldBlockPos, O> onConfigLoad) {
            this.onConfigLoad = onConfigLoad;
            return this;
        }

        @Override
        public IBuilder<O> setOnConfigSave(BiConsumer<ConfigSection, BlockMetaData<O>> onConfigSave) {
            this.onConfigSave = onConfigSave;
            return this;
        }

        @Override
        public IBuilder<O> setOnConfigRemove(BiConsumer<ConfigSection, BlockMetaData<O>> onConfigRemove) {
            this.onConfigRemove = onConfigRemove;
            return this;
        }

        public SVDataManager<O> build(){
            if (onConfigLoad == null){ //If not set, we assume the objct is a LoadableSalvable
                onConfigLoad = (configSection, worldBlockPos) -> configSection.getLoadable("", watchedClass);
            }
            if (onConfigSave == null){ //If not set, we assume the objct is a LoadableSalvable
                onConfigSave = (configSection, blockMetaData) -> configSection.setValue("", blockMetaData.getValue());
            }
            if (onConfigRemove == null){ //If not set, do simple removal
                onConfigRemove = (configSection, blockMetaData) -> configSection.setValue("", null);
            }

            return new SVDataManager<>(
                    baseFolder,
                    watchedClass,
                    onConfigLoad,
                    onConfigSave,
                    onConfigRemove
            );
        }
    }
}
