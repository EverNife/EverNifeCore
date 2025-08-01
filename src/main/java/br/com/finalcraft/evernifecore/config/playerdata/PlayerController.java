package br.com.finalcraft.evernifecore.config.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.config.yaml.caching.SmartCachedYamlFileHolder;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.listeners.PlayerLoginListener;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class PlayerController {

    private static Map<UUID,PlayerData> MAP_OF_PLAYER_DATA = new HashMap<>();

    private static final File PLAYER_DATA_FOLDER = new File(EverNifeCore.instance.getDataFolder(), "PlayerData");
    private static final File CORRUPTED_PLAYER_DATA_FOLDER = new File(EverNifeCore.instance.getDataFolder(), "PlayerData-Corrupted");
    private static final File DORMANT_PLAYER_DATA_FOLDER = new File(EverNifeCore.instance.getDataFolder(), "PlayerData-Dormant");

    private static final Map<Class<? extends PDSection>, PDSectionConfiguration> CONFIGURED_PDSECTIONS = new HashMap();

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
            EverNifeCore.warning("Failed to move the Corrupted PlayerData of " + playerDataFile.getAbsolutePath() + " to the corrupt folder.");
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

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(playerdataLoader.size(), Math.max(4, Runtime.getRuntime().availableProcessors())));
        CountDownLatch latch = new CountDownLatch(playerdataLoader.size());
        for (Supplier<PlayerData> supplier : playerdataLoader) {
            executor.execute(() -> {
                try {
                    PlayerData playerData = supplier.get();
                    if (playerData != null){ //Can be null when there is an error loading the PlayerData of the loading was ignored
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

            PlayerData existinUUID = uuidHashMap.get(playerData.getUniqueId());
            if (existinUUID != null){
                PlayerData playerToKeep = existinUUID;
                PlayerData playerToRemove = playerData;

                if (playerData.getLastSeen() > existinUUID.getLastSeen()){
                    playerToKeep = playerData;
                    playerToRemove = existinUUID;
                }

                boolean namesAreEqual = playerToKeep.getPlayerName().equalsIgnoreCase(playerToRemove.getPlayerName());
                String nameEqualMessage = namesAreEqual ? "" : "\nThe PlayerData that was kept has the name [" + playerToKeep.getPlayerName() + "] and the removed is named [" + playerToRemove.getPlayerName() + "]";

                EverNifeCore.getLog().severe("There is a duplicated PlayerData for the UUID [" + playerData.getUniqueId() + "]!" +
                        "\nThis usually happens when you change your server from 'OlineMode=true' to 'OnlineMode=false' and vice versa." +
                        "\nI will try to fix this, the PlayerData that is more recent will be kept and the older one will be moved to the Corrupted Folder!" + nameEqualMessage);

                uuidHashMap.remove(playerToRemove.getUniqueId());
                nameHashMap.remove(playerToRemove.getPlayerName());
                moveToCorruptedFolder(playerToRemove.getConfig().getTheFile());

                playerData = playerToKeep;
            }

            //UUID is ok, we can add again or add new
            uuidHashMap.put(playerData.getUniqueId(), playerData);

            PlayerData existingName = nameHashMap.get(playerData.getPlayerName());
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

                EverNifeCore.getLog().severe("There is a duplicated PlayerData for the NAME [" + playerData.getPlayerName() + "]!" +
                        "\nThis usually happens when you change your server from 'OlineMode=true' to 'OnlineMode=false' and vice versa." +
                        "\nI will try to fix this, the PlayerData that is more recent will be kept and the older one will be moved to the Corrupted Folder!" + nameEqualMessage);

                nameHashMap.remove(playerToRemove.getPlayerName());
                moveToCorruptedFolder(playerToRemove.getConfig().getTheFile());
            }

            //NAME is ok, we can add again or add new
            nameHashMap.put(playerData.getPlayerName(), playerData);

            UUIDsController.addOrUpdateUUIDName(
                    playerData.getUniqueId(),
                    playerData.getPlayerName()
            );
        }

        long end = System.currentTimeMillis();
        EverNifeCore.getLog().info("Finished Loading PlayerData of %s players! (%s)", uuidHashMap.size(), FCTimeFrame.of(end-start).getFormattedDiscursive());

        synchronized (MAP_OF_PLAYER_DATA){
            MAP_OF_PLAYER_DATA = uuidHashMap;
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            PlayerLoginListener.handlePlayerAsyncPreUUIDToNameCalculation(
                    onlinePlayer.getUniqueId(),
                    onlinePlayer.getName()
            );
            getOrCreateOne(onlinePlayer.getUniqueId()).setPlayer(onlinePlayer);
        }

        //Different from the behavavior of calling PlayerData::hotLoadPDSections
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
                String playerName = playerData != null && playerData.getPlayerName() != null
                        ? playerData.getPlayerName()
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
                EverNifeCore.warning("Failed to move dormant PlayerData " + dormantFile.getName() + " to the PlayerData folder... this is a terrible (sad) problem!");
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

    public static <T extends PDSection> T getPDSection(Player player, Class<T> pdSectionClass){
        PlayerData playerData = getPlayerData(player);
        return playerData != null ? playerData.getPDSection(pdSectionClass) : null;
    }

    public static <T extends PDSection> T getPDSection(OfflinePlayer offlinePlayer, Class<T> pdSectionClass){
        PlayerData playerData = getPlayerData(offlinePlayer);
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

    public static PlayerData getPlayerData(Player player){
        Objects.requireNonNull(player, "Player can't be null");

        return MAP_OF_PLAYER_DATA.get(player.getUniqueId());
    }

    public static PlayerData getPlayerData(OfflinePlayer offlinePlayer){
        Objects.requireNonNull(offlinePlayer, "OfflinePlayer can't be null");

        return MAP_OF_PLAYER_DATA.get(offlinePlayer.getUniqueId());
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
            playerData.setPlayer(Bukkit.getPlayer(uuid));
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

    public static void registerPDSectionCfg(Plugin plugin, Class<? extends PDSection> pdSectionClass){
        PDSectionConfiguration pdSectionConfiguration = new PDSectionConfiguration(
                ECPluginManager.getOrCreateECorePluginData(plugin),
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
}
