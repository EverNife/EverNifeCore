package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public class ECSettings {

    public static boolean useNamesInsteadOfUUIDToStorePlayerData = false;
    public static String ZONE_ID_OF_DAY_OF_TODAY = "America/Sao_Paulo";

    public static void initialize(){

        boolean isUsingStorageWithPlayerName = false;

        if (!ConfigManager.getMainConfig().contains("Settings.useNamesInsteadOfUUIDToStorePlayerData")){//Check if there is any playerdata and if the player data is being store with an UUID
            for (Map.Entry<UUID, String> entry : UUIDsController.getEntrySet()) {
                final UUID playerUUID = entry.getKey();
                final String playerName = entry.getValue();
                File uuidConfig = new File(EverNifeCore.instance.getDataFolder(), "PlayerData/" + playerUUID + ".yml");
                if (uuidConfig.exists()){
                    isUsingStorageWithPlayerName = false;
                    break;
                }
                File nameConfig = new File(EverNifeCore.instance.getDataFolder(), "PlayerData/" + playerName + ".yml");
                if (nameConfig.exists()){
                    isUsingStorageWithPlayerName = true;
                    break;
                }
            }
        }

        useNamesInsteadOfUUIDToStorePlayerData = ConfigManager.getMainConfig().getOrSetDefaultValue("Settings.useNamesInsteadOfUUIDToStorePlayerData", isUsingStorageWithPlayerName);

        ZONE_ID_OF_DAY_OF_TODAY = ConfigManager.getMainConfig()
                .getOrSetDefaultValue("Settings.ZONE_ID_OF_DAY_OF_TODAY", "America/Sao_Paulo");


        ConfigManager.getMainConfig().saveIfNewDefaults();
    }

}
