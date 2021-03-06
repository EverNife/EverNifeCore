package br.com.finalcraft.evernifecore.config.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerController {

    private static Map<UUID,PlayerData> MAP_OF_PLAYER_DATA = new HashMap<UUID, PlayerData>();

    public static void initialize(){
        synchronized (MAP_OF_PLAYER_DATA){
            MAP_OF_PLAYER_DATA.clear();

            for (Map.Entry<UUID, String> entry : UUIDsController.getEntrySet()) {
                final UUID playerUUID = entry.getKey();
                final String playerName = entry.getValue();

                String playerDataFileName = ECSettings.useNamesInsteadOfUUIDToStorePlayerData ? playerName : playerUUID.toString();

                File theConfigFile = new File(EverNifeCore.instance.getDataFolder(), "PlayerData/" + playerDataFileName + ".yml");
                if (theConfigFile.exists()){
                    try {
                        Config config = new Config(theConfigFile);
                        PlayerData playerData = new PlayerData(config);
                        MAP_OF_PLAYER_DATA.put(playerUUID, playerData);
                    }catch (Exception e){
                        EverNifeCore.warning("Failed to load PlayerData [" + theConfigFile.getName() + "] moving it to the corrupt folder. \nTheFile: " + theConfigFile.getAbsolutePath() + " .");
                        e.printStackTrace();
                        try {
                            File corruptFolder = new File(theConfigFile.getParentFile().getParent(), "CorruptedPlayerData");
                            corruptFolder.mkdirs();
                            FileUtils.moveFile(
                                    theConfigFile,
                                    new File(corruptFolder, theConfigFile.getName())
                            );
                        }catch (IOException e2){
                            EverNifeCore.warning("Failed to move the Corrupted PlayerData of " + theConfigFile.getAbsolutePath() + " to the corrupt folder.");
                            e2.printStackTrace();
                        }
                    }
                }
            }

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                getOrCreateOne(onlinePlayer.getUniqueId()).setPlayer(onlinePlayer);
            }

            EverNifeCore.info(ChatColor.translateAlternateColorCodes('&',"&aFinished Loading PlayerData of " + MAP_OF_PLAYER_DATA.size() + " players!"));
        }
    }

    public static void savePlayerDataOnConfig(){
        for (PlayerData playerData : MAP_OF_PLAYER_DATA.values()) {
            try {
                playerData.savePlayerData();
            }catch (Throwable e){
                EverNifeCore.warning("Failed to save PlayerData of: " + playerData.getPlayerName());
                e.printStackTrace();
            }
        }
    }

    public static PlayerData addNewPlayerData(UUID playerUUID){
        Objects.requireNonNull(playerUUID, "PlayerUUID can't be null");

        String playerName   = UUIDsController.getNameFromUUID(playerUUID);
        String theFileName  = ECSettings.useNamesInsteadOfUUIDToStorePlayerData ? playerName : playerUUID.toString();
        File dormantFile  = new File(EverNifeCore.instance.getDataFolder(), "PlayerDataDormant/" + theFileName + ".yml");
        File theConfigFile  = new File(EverNifeCore.instance.getDataFolder(), "PlayerData/" + theFileName + ".yml");

        if (dormantFile.exists()){
            try {
                FileUtils.moveFile(
                        dormantFile,
                        theConfigFile
                );
            }catch (Exception e){
                EverNifeCore.warning("Failed to move dormant PlaeyerData " + dormantFile.getName() + " to the PlayerData folder... this is a terrible (sad) problem!");
                e.printStackTrace();
            }
        }
        Config config       = new Config(theConfigFile);
        PlayerData playerData = new PlayerData(config, playerName, playerUUID);
        playerData.forceSavePlayerData();

        MAP_OF_PLAYER_DATA.put(playerUUID, playerData);
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
        }
        return playerData;
    }

    public static int getPlayerDataCount(){
        return MAP_OF_PLAYER_DATA.size();
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

    //Erase all PDSections reference from all PlayerData
    public static void clearPDSections(Class<? extends PDSection> pdSectionClass){
        for (PlayerData playerData : getAllPlayerData()) {
            playerData.getMapOfPDSections().remove(pdSectionClass);
        }
    }
}
