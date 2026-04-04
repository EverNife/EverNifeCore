package br.com.finalcraft.evernifecore.config.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.config.yaml.caching.SmartCachedYamlFileHolder;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCExecutorsUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class PlayerController {

    private static Map<UUID,PlayerData> MAP_OF_PLAYER_DATA = new ConcurrentHashMap<>();

    private static final File DATA_FOLDER = EverNifeCore.instance.getEcPluginData().getPluginData().getDataFolder();
    private static final File PLAYER_DATA_FOLDER = new File(DATA_FOLDER, "PlayerData");
    private static final File CORRUPTED_PLAYER_DATA_FOLDER = new File(DATA_FOLDER, "PlayerData-Corrupted");
    private static final File DORMANT_PLAYER_DATA_FOLDER = new File(DATA_FOLDER, "PlayerData-Dormant");

    private static final Map<Class<? extends PDSection>, PDSectionConfiguration> CONFIGURED_PDSECTIONS = new ConcurrentHashMap<>();

    private static void moveToCorruptedFolder(File playerDataFile){
        try {
            EverNifeCore.getLog().warning("Moving PlayerData File [%s] to the CorruptedPlayerData folder.", playerDataFile.getName());
            CORRUPTED_PLAYER_DATA_FOLDER.mkdirs();

            File newCorruptedFile = new File(CORRUPTED_PLAYER_DATA_FOLDER, playerDataFile.getName());
            while (newCorruptedFile.exists()){
                newCorruptedFile = new File(CORRUPTED_PLAYER_DATA_FOLDER, StringUtils.substring(playerDataFile.getName(),0,-4) + "_" + System.currentTimeMillis() + ".yml");
            }

            FileUtils.moveFile(
                    playerDataFile,
                    newCorruptedFile
            );
        }catch (IOException e2){
            EverNifeCore.getLog().warning("Failed to move the Corrupted PlayerData of " + playerDataFile.getAbsolutePath() + " to the corrupt folder.");
            e2.printStackTrace();
        }
    }

    public static void initialize(){
        long start = System.currentTimeMillis();
        UUIDsController.getUuidHashMap().clear();
        List<Supplier<PlayerData>> playerdataLoader = new ArrayList<>();

        PLAYER_DATA_FOLDER.mkdirs();
        Iterator<File> fileIterator = FileUtils.iterateFiles(PLAYER_DATA_FOLDER,
                new String[]{"yml"},
                false
        );

        AtomicLong cacheTimeSpread = new AtomicLong(0);

        fileIterator.forEachRemaining(theConfigFile -> {
            playerdataLoader.add(() -> {
                try {
                    Config config = new Config(theConfigFile);

                    if (ECSettings.daysSinceLastLoginToLoadPlayerDataInMemory > 0){
                        Long lastSeen = config.getLong("PlayerData.lastSeen");

                        if (lastSeen == null || (System.currentTimeMillis() - lastSeen) > TimeUnit.DAYS.toMillis(ECSettings.daysSinceLastLoginToLoadPlayerDataInMemory)){
                            //moveToDormantFolder(theConfigFile); TODO finish the dormant system
                            return null;
                        }
                    }

                    PlayerData playerData = new PlayerData(config);

                    config.enableSmartCache(); //Cache config for only 5 minutes between uses

                    //If the last edition this file has is from 3 days ago, cache its config right now, as it will probably not change
                    if (System.currentTimeMillis() > playerData.getLastSaved() + TimeUnit.DAYS.toMillis(3)){
                        SmartCachedYamlFileHolder smartCachedYamlFileHolder = (SmartCachedYamlFileHolder) config.getIHasYamlFile();
                        long extraDelayMillis = cacheTimeSpread.getAndAdd(5);

                        //Cache it in a separated time, making caching as spread as possible
                        smartCachedYamlFileHolder.scheduleExpirationRunnable(15_000 + extraDelayMillis, TimeUnit.MILLISECONDS);
                    }

                    return playerData;
                }catch (Throwable e){
                    EverNifeCore.getLog().severe("Failed to load PlayerData [%s] at %s", theConfigFile.getName(), theConfigFile.getAbsolutePath());
                    e.printStackTrace();
                    moveToCorruptedFolder(theConfigFile);
                }
                return null;
            });
        });

        Queue<PlayerData> loadedPlayerData = new ConcurrentLinkedQueue<>();

        ExecutorService executor = FCExecutorsUtil.createVirtualExecutorIfPossible("playerdata-loader");
        CountDownLatch latch = new CountDownLatch(playerdataLoader.size());
        for (Supplier<PlayerData> supplier : playerdataLoader) {
            executor.execute(() -> {
                try {
                    PlayerData playerData = supplier.get();
                    if (playerData != null){ //Can be null when there is an error loading the PlayerData or the loading was ignored (daysSinceLastLoginToLoadPlayerDataInMemory)
                        loadedPlayerData.add(playerData);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        executor.shutdown();

        HashMap<UUID,PlayerData> uuidHashMap = new HashMap<>();
        HashMap<String, PlayerData> nameHashMap = new HashMap<>();

        for (PlayerData playerData : loadedPlayerData) {

            PlayerData existingUUID = uuidHashMap.get(playerData.getUniqueId());
            if (existingUUID != null){
                PlayerData playerToKeep = existingUUID;
                PlayerData playerToRemove = playerData;

                if (playerData.getLastSeen() > existingUUID.getLastSeen()){
                    playerToKeep = playerData;
                    playerToRemove = existingUUID;
                }

                boolean namesAreEqual = playerToKeep.getName().equalsIgnoreCase(playerToRemove.getName());
                String nameEqualMessage = namesAreEqual ? "" : "\nThe PlayerData that was kept has the name [" + playerToKeep.getName() + "] and the removed is named [" + playerToRemove.getName() + "]";

                EverNifeCore.getLog().severe("There is a duplicated PlayerData for the UUID [" + playerData.getUniqueId() + "]!" +
                        "\nThis usually happens when you change your server from 'OlineMode=true' to 'OnlineMode=false' and vice versa." +
                        "\nI will try to fix this, the PlayerData that is more recent will be kept and the older one will be moved to the Corrupted Folder!" + nameEqualMessage);

                uuidHashMap.remove(playerToRemove.getUniqueId());
                nameHashMap.remove(playerToRemove.getName());
                moveToCorruptedFolder(playerToRemove.getConfig().getTheFile());

                playerData = playerToKeep;
            }

            //UUID is ok, we can add again or add new
            uuidHashMap.put(playerData.getUniqueId(), playerData);

            PlayerData existingName = nameHashMap.get(playerData.getName());
            if (existingName != null){
                //This will probably happen a lot less than the UUID problem, but if it happens, we will keep the most recent one

                PlayerData playerToKeep = existingName;
                PlayerData playerToRemove = playerData;

                if (playerData.getLastSeen() > existingName.getLastSeen()){
                    playerToKeep = playerData;
                    playerToRemove = existingName;
                }

                boolean uuidsAreEqual = playerToKeep.getUniqueId().equals(playerToRemove.getUniqueId());
                String nameEqualMessage = uuidsAreEqual ? "" : "\nThe PlayerData that was kept has the uuid [" + playerToKeep.getUniqueId() + "] and the removed is named [" + playerToRemove.getUniqueId() + "]";

                EverNifeCore.getLog().severe("There is a duplicated PlayerData for the NAME [" + playerData.getName() + "]!" +
                        "\nThis usually happens when you change your server from 'OlineMode=true' to 'OnlineMode=false' and vice versa." +
                        "\nI will try to fix this, the PlayerData that is more recent will be kept and the older one will be moved to the Corrupted Folder!" + nameEqualMessage);

                nameHashMap.remove(playerToRemove.getName());
                moveToCorruptedFolder(playerToRemove.getConfig().getTheFile());
            }

            //NAME is ok, we can add again or add new
            nameHashMap.put(playerData.getName(), playerData);

            UUIDsController.addOrUpdateUUIDName(
                    playerData.getUniqueId(),
                    playerData.getName()
            );
        }

        long end = System.currentTimeMillis();
        EverNifeCore.getLog().info("Finished Loading PlayerData of %s players! (%s)", uuidHashMap.size(), FCTimeFrame.of(end-start).getFormattedDiscursive());

        synchronized (MAP_OF_PLAYER_DATA){
            MAP_OF_PLAYER_DATA = uuidHashMap;
        }

        for (FPlayer onlinePlayer : EverNifeCore.getPlatform().getOnlinePlayers()) {
            handlePlayerAsyncPreUUIDToNameCalculation(
                    onlinePlayer.getUniqueId(),
                    onlinePlayer.getName()
            );
            getOrCreateOne(onlinePlayer.getUniqueId()).setPlayer(onlinePlayer);
        }

        //Different from the behavior of calling PlayerData::hotLoadPDSections
        //on reload, as we want to track performance individually, we load them one by one!
        if (CONFIGURED_PDSECTIONS.size() > 0){
            List<PDSectionConfiguration> pdSectionConfigurations;
            synchronized (CONFIGURED_PDSECTIONS){
                pdSectionConfigurations = new ArrayList<>(CONFIGURED_PDSECTIONS.values());
            }
            for (PDSectionConfiguration pdSectionConfiguration : pdSectionConfigurations) {
                start = System.currentTimeMillis();
                for (PlayerData playerData : getAllPlayerData()) {
                    playerData.getPDSection(pdSectionConfiguration.getPdSectionClass());
                }
                end = System.currentTimeMillis();
                EverNifeCore.getLog().info("Finished Loading PDSection {%s} of %s players! (%s)",
                        pdSectionConfiguration.getPdSectionClass().getSimpleName(),
                        uuidHashMap.size(),
                        FCTimeFrame.of(end - start).getFormattedDiscursive()
                );
            }
        }
    }

    public static void savePlayerDataOnConfig(){
        List<Map.Entry<UUID, PlayerData>> allPlayerData;
        synchronized (MAP_OF_PLAYER_DATA){
            allPlayerData = new ArrayList<>(MAP_OF_PLAYER_DATA.entrySet());
        }
        for (Map.Entry<UUID, PlayerData> entry : allPlayerData) {
            UUID uuid = entry.getKey();
            PlayerData playerData = entry.getValue();

            try {
                playerData.savePlayerData();
            }catch (Throwable e){
                String playerName = playerData != null && playerData.getName() != null
                        ? playerData.getName()
                        : UUIDsController.getNameFromUUID(uuid);
                EverNifeCore.getLog().warning("Failed to save PlayerData of [%s] (%s)!", uuid, playerName);
                e.printStackTrace();
            }
        }
    }

    private static PlayerData addNewPlayerData(UUID playerUUID){
        Objects.requireNonNull(playerUUID, "PlayerUUID can't be null");

        String playerName   = UUIDsController.getNameFromUUID(playerUUID);
        String theFileName  = ECSettings.useNamesInsteadOfUUIDToStorePlayerData ? playerName : playerUUID.toString();
        File dormantFile  = new File(DORMANT_PLAYER_DATA_FOLDER, playerUUID + ".yml");
        File theConfigFile  = new File(PLAYER_DATA_FOLDER, theFileName + ".yml");

        if (dormantFile.exists()){
            try {
                Config dormantConfig = new Config(dormantFile);
                String previosName = dormantConfig.getString("PlayerData.Username","");

                if (!previosName.equals(playerName)){
                    dormantConfig.setValue("PlayerData.Username",playerName); //Update playerName
                    EverNifeCore.getLog().info("Moving dormant PlayerData [%s - %s] to the PlayerData folder... The player has a new name, its called: %s", playerUUID, previosName, playerName);
                }else {
                    EverNifeCore.getLog().info("Moving dormant PlayerData [%s - %s] to the PlayerData folder...", playerUUID, playerName);
                }

                dormantConfig.save(theConfigFile);
                dormantFile.delete();
            }catch (Exception e){
                EverNifeCore.getLog().warning("Failed to move dormant PlayerData " + dormantFile.getName() + " to the PlayerData folder... this is a terrible (sad) problem!");
                e.printStackTrace();
            }
        }
        Config config = new Config(theConfigFile);
        PlayerData playerData = new PlayerData(config, playerName, playerUUID);
        playerData.forceSavePlayerData();

        synchronized (MAP_OF_PLAYER_DATA){
            MAP_OF_PLAYER_DATA.put(playerUUID, playerData);
        }
        return playerData;
    }

    public static <T extends PDSection> T getPDSection(UUID uuid, Class<T> pdSectionClass){
        PlayerData playerData = getPlayerData(uuid);
        return playerData != null ? playerData.getPDSection(pdSectionClass) : null;
    }

    public static <T extends PDSection> T getPDSection(FPlayer player, Class<T> pdSectionClass){
        PlayerData playerData = getPlayerData(player);
        return playerData != null ? playerData.getPDSection(pdSectionClass) : null;
    }

    public static <T extends PDSection> T getPDSection(String playerName, Class<T> pdSectionClass){
        PlayerData playerData = getPlayerData(playerName);
        return playerData != null ? playerData.getPDSection(pdSectionClass) : null;
    }

    public static PlayerData getPlayerData(UUID uuid){
        Objects.requireNonNull(uuid, "UUID can't be null");

        return MAP_OF_PLAYER_DATA.get(uuid);
    }

    public static PlayerData getPlayerData(FPlayer player){
        Objects.requireNonNull(player, "Player can't be null");

        return MAP_OF_PLAYER_DATA.get(player.getUniqueId());
    }

    public static PlayerData getPlayerData(String playerName){
        Objects.requireNonNull(playerName, "PlayerName can't be null");

        UUID uuid = UUIDsController.getUUIDFromName(playerName);
        return uuid == null ? null : MAP_OF_PLAYER_DATA.get(uuid);
    }

    public static PlayerData getOrCreateOne(UUID uuid){
        Objects.requireNonNull(uuid, "PlayerUUID can't be null");

        PlayerData playerData = getPlayerData(uuid);
        if (playerData == null){
            playerData = addNewPlayerData(uuid);
            FPlayer player = EverNifeCore.getPlatform().getPlayer(uuid);
            playerData.setPlayer(player);
        }
        return playerData;
    }

    public static int getPlayerDataCount(){
        return MAP_OF_PLAYER_DATA.size();
    }

    public static Map<UUID, PlayerData> getMapOfPlayerData() {
        return MAP_OF_PLAYER_DATA;
    }

    public static Collection<PlayerData> getAllPlayerData(){
        return MAP_OF_PLAYER_DATA.values();
    }

    public static <T extends PDSection> List<T> getAllPlayerData(Class<T> pdSectionClass){
        List<T> list = new ArrayList<>();
        for (PlayerData playerData : getAllPlayerData()) {
            list.add(playerData.getPDSection(pdSectionClass));
        }
        return list;
    }

    //Returns a new instance of the PlayerData
    public static PlayerData reloadPlayerData(UUID playerUUID){
        MAP_OF_PLAYER_DATA.remove(playerUUID);

        final String playerName = UUIDsController.getNameFromUUID(playerUUID);
        if (playerName == null){
            throw new IllegalArgumentException("The UUID must have a playerName associated with it!");
        }

        final String playerDataFileName = ECSettings.useNamesInsteadOfUUIDToStorePlayerData ? playerName : playerUUID.toString();

        File theConfigFile = new File(PLAYER_DATA_FOLDER, playerDataFileName + ".yml");
        if (theConfigFile.exists()){
            try {
                Config config = new Config(theConfigFile);
                PlayerData playerData = new PlayerData(config);
                MAP_OF_PLAYER_DATA.put(playerUUID, playerData);
                return playerData;
            }catch (Exception e){
                EverNifeCore.getLog().severe("Failed to load PlayerData [%s] at %s", theConfigFile.getName(), theConfigFile.getAbsolutePath());
                e.printStackTrace();
                moveToCorruptedFolder(theConfigFile);
            }
        }

        //In case we were not able to actually reload this player data, we create a new one!
        return getOrCreateOne(playerUUID).hotLoadPDSections();
    }

    //Erase all PDSections reference from all PlayerData
    public static void clearPDSections(Class<? extends PDSection> pdSectionClass){
        for (PlayerData playerData : getAllPlayerData()) {
            playerData.getMapOfPDSections().remove(pdSectionClass);
        }
    }

    public static void registerPDSectionCfg(ECPluginData ecPluginData, Class<? extends PDSection> pdSectionClass){
        PDSectionConfiguration pdSectionConfiguration = new PDSectionConfiguration(
                ecPluginData,
                pdSectionClass,
                true
        );

        registerPDSectionCfg(pdSectionConfiguration);
    }

    public static void registerPDSectionCfg(PDSectionConfiguration pdSectionConfiguration){
        CONFIGURED_PDSECTIONS.put(pdSectionConfiguration.getPdSectionClass(), pdSectionConfiguration);
        if (pdSectionConfiguration.shouldHotLoad()){
            getAllPlayerData().forEach(playerData -> {
                playerData.getPDSection(pdSectionConfiguration.getPdSectionClass());
            });
        }
    }

    public static Map<Class<? extends PDSection>, PDSectionConfiguration> getConfiguredPDSections() {
        return CONFIGURED_PDSECTIONS;
    }

    public static void handlePlayerAsyncPreUUIDToNameCalculation(UUID currentUUID, String currentName){
        if (UUIDsController.isUUIDLinkedToName(currentUUID, currentName)){
            //99% os cases on re-login, this will happen
            return; //We already have this player in our database, and the name and the uuid are still the same
        }

        //Now three Scenarios:
        // 1- It's a complete new Player
        // 2- The UUID is different
        // 3- The Name is different

        String existingName = UUIDsController.getNameFromUUID(currentUUID);
        UUID existingUUID = UUIDsController.getUUIDFromName(currentName);

        if (existingName == null && existingUUID == null){
            //This is a complete new Player!
            //We just need to add the new Pair of UUID and Name
            //And create a new PlayerData for this player
            UUIDsController.addOrUpdateUUIDName(currentUUID, currentName);
            PlayerController.getOrCreateOne(currentUUID).hotLoadPDSections();
            return;
        }

        //We have an inconsistency in the playerData, two scenarios:
        // 1- The server is in OnlineMode=true and a player changed his name in the mojang site (so, name is different and uuid is the same)
        // 2- The server changed from OnlineMode=false to OnlineMode=true or vice versa (so, name is the same and uuid is different)
        // C- It's a special case, described bellow

        /*
         * The special case only matters if the server is in onlineMode=true
         *
         *   [C1] Server is in OnlineMode=true or BungeeCord's is enabled
         *   [C2] An old player stops playing and change his mojang name to something else
         *   [C3] A new player or an existing one change his name to the same name from that old player
         *   [C4] In this case we must, at the eminence of the new player,
         *       delete the old player data (or move to dormant) and create a new one for the new player
         */
        UUID offlineCalculatedUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + currentName).getBytes(StandardCharsets.UTF_8));
        if (!currentUUID.equals(offlineCalculatedUUID) // Scenario [C1], we are online
                && existingUUID != null // This means that C2 and C3 is possible, as there is a player (maybe itself in offline mode) with the same name
                && !existingUUID.equals(currentUUID) // This is probably different player
                && !existingUUID.equals(offlineCalculatedUUID)){ // Confirm it's a different player, this checks for "this player is not myself in offline-mode"

            //The CURRENT_NAME is vinculated to another UUID
            PlayerData playerData = PlayerController.getPlayerData(existingUUID);
            PlayerController.getMapOfPlayerData().remove(playerData.getUniqueId());//Unload this PlayerData

            playerData.getConfig().save(new File(EverNifeCore.instance.getEcPluginData().getPluginData().getDataFolder(), "PlayerData-Dormant/" + existingUUID + ".yml"));//Move to dormant folder
            EverNifeCore.getLog().info("[UUIDsController] [%s:%s] was moved to dormant files because his name is not valid anymore!", existingUUID, currentName);

            //Now we can create a new PlayerData for this player
            //Or maybe change, if this is not a complete new player, change his username
            if (existingName == null){
                //This is a complete new Player!
                //We just need to add the new Pair of UUID and Name
                //And create a new PlayerData for this player
                UUIDsController.addOrUpdateUUIDName(currentUUID, currentName);
                PlayerController.getOrCreateOne(currentUUID).hotLoadPDSections();
                return;
            }
        }

        // Treating case [1] and [2]
        if (existingName == null){
            //If no existingName, then we have a new name for an existing UUID
            PlayerData playerData = PlayerController.getPlayerData(existingUUID);
            PlayerController.getMapOfPlayerData().remove(playerData.getUniqueId());//Unload this PlayerData
            playerData.getConfig().setValue("PlayerData.UUID", currentUUID);

            playerData.getConfig().getTheFile().delete(); //Delete previous file, in case we have changed its name
            String newFileName = (ECSettings.useNamesInsteadOfUUIDToStorePlayerData ? currentName : currentUUID.toString()) + ".yml";
            playerData.getConfig().save(new File(EverNifeCore.instance.getEcPluginData().getPluginData().getDataFolder(), "PlayerData/" + newFileName));

            EverNifeCore.getLog().info("[UUIDsController] [%s] changed his UUID from %s to %s", currentName, playerData.getUniqueId(), currentUUID);
            UUIDsController.addOrUpdateUUIDName(currentUUID, currentName);
            PlayerController.getOrCreateOne(currentUUID).hotLoadPDSections();
        }else {
            //If no existingUUID, then we have a new UUID for an existing Name
            PlayerData playerData = PlayerController.getPlayerData(existingName);
            PlayerController.getMapOfPlayerData().remove(playerData.getUniqueId());//Unload this PlayerData
            playerData.getConfig().setValue("PlayerData.Username", currentName);

            playerData.getConfig().getTheFile().delete(); //Delete previous file, in case we have changed its name
            String newFileName = (ECSettings.useNamesInsteadOfUUIDToStorePlayerData ? currentName : currentUUID.toString()) + ".yml";
            playerData.getConfig().save(new File(EverNifeCore.instance.getEcPluginData().getPluginData().getDataFolder(), "PlayerData/" + newFileName));

            EverNifeCore.getLog().info("[UUIDsController] [%s] changed his name from %s to %s", currentUUID, playerData.getName(), currentName);
            UUIDsController.addOrUpdateUUIDName(currentUUID, currentName);
            PlayerController.getOrCreateOne(currentUUID).hotLoadPDSections();;
        }
    }
}
