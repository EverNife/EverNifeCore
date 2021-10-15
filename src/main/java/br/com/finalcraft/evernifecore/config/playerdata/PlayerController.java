package br.com.finalcraft.evernifecore.config.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public class PlayerController {

    public static Map<UUID,PlayerData> mapOfPlayersData = new HashMap<UUID, PlayerData>();

    public static void initialize(){
        synchronized (mapOfPlayersData){
            mapOfPlayersData.clear();

            for (Map.Entry<UUID, String> entry : UUIDsController.getEntrySet()) {
                final UUID playerUUID = entry.getKey();
                final String playerName = entry.getValue();

                String playerDataFileName = ECSettings.useNamesInsteadOfUUIDToStorePlayerData ? playerName : playerUUID.toString();

                File theConfigFile = new File(EverNifeCore.instance.getDataFolder(), "PlayerData/" + playerDataFileName + ".yml");
                if (theConfigFile.exists()){
                    try {
                        Config config = new Config(theConfigFile);
                        PlayerData playerData = new PlayerData(config);
                        mapOfPlayersData.put(playerUUID, playerData);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                getOrCreateOne(onlinePlayer.getUniqueId()).setPlayer(onlinePlayer);
            }

            EverNifeCore.info(ChatColor.translateAlternateColorCodes('&',"&aFinished Loading PlayerData of " + mapOfPlayersData.size() + " players!"));
        }
    }

    public static void savePlayerDataOnConfig(){
        for (PlayerData playerData : mapOfPlayersData.values()) {
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

        File theConfigFile  = new File(EverNifeCore.instance.getDataFolder(), "PlayerData/" + playerUUID + ".yml");
        Config config       = new Config(theConfigFile);
        String playerName   = UUIDsController.getNameFromUUID(playerUUID);

        PlayerData playerData = new PlayerData(config, playerName, playerUUID);
        playerData.forceSavePlayerData();

        mapOfPlayersData.put(playerUUID, playerData);
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

        return mapOfPlayersData.get(uuid);
    }

    public static PlayerData getPlayerData(Player player){
        Objects.requireNonNull(player, "Player can't be null");

        return mapOfPlayersData.get(player.getUniqueId());
    }

    public static PlayerData getPlayerData(OfflinePlayer offlinePlayer){
        Objects.requireNonNull(offlinePlayer, "OfflinePlayer can't be null");

        return mapOfPlayersData.get(offlinePlayer.getUniqueId());
    }

    public static PlayerData getPlayerData(String playerName){
        Objects.requireNonNull(playerName, "PlayerName can't be null");

        UUID uuid = UUIDsController.getUUIDFromName(playerName);
        return uuid == null ? null : mapOfPlayersData.get(uuid);
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
        return mapOfPlayersData.size();
    }

    public static Collection<PlayerData> getAllPlayerData(){
        return mapOfPlayersData.values();
    }

    public static <T extends PDSection> List<T> getAllPlayerData(Class<T> pdSectionClass){
        List<T> list = new ArrayList<>();
        for (PlayerData playerData : getAllPlayerData()) {
            list.add(playerData.getPDSection(pdSectionClass));
        }
        return list;
    }
}
