package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.time.DayOfToday;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

public class ECSettings {

    public static boolean useNamesInsteadOfUUIDToStorePlayerData = false;

    //Time Related
    public static String ZONE_ID_OF_DAY_OF_TODAY            = "America/Sao_Paulo";
    public static SimpleDateFormat SIMPLE_DATE_FORMAT       = new SimpleDateFormat("dd/MM/yyyy");
    public static SimpleDateFormat DATE_FORMAT_WITH_HOURS   = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    //Guis
    public static int DEFAULT_GUI_UPDATE_TIME = 2;

    public static void initialize(){

        boolean isUsingStorageWithPlayerName = false;
        if (!ConfigManager.getMainConfig().contains("Settings.useNamesInsteadOfUUIDToStorePlayerData")){//Check if there is any playerdata and if the player data is being store with an UUID
            for (Map.Entry<UUID, String> entry : UUIDsController.getEntrySet()) {
                final String playerName = entry.getValue();
                File nameConfig = new File(EverNifeCore.instance.getDataFolder(), "PlayerData/" + playerName + ".yml");
                if (nameConfig.exists() && nameConfig.isFile()){
                    isUsingStorageWithPlayerName = true;
                    break;
                }

                final UUID playerUUID = entry.getKey();
                File uuidConfig = new File(EverNifeCore.instance.getDataFolder(), "PlayerData/" + playerUUID + ".yml");
                if (uuidConfig.exists() && uuidConfig.isFile()){
                    isUsingStorageWithPlayerName = false;
                    break;
                }
            }
        }
        useNamesInsteadOfUUIDToStorePlayerData = ConfigManager.getMainConfig().getOrSetDefaultValue(
                "Settings.useNamesInsteadOfUUIDToStorePlayerData",
                isUsingStorageWithPlayerName,
                "Should EverNifeCore store the PlayerData using the UUID of the player as the filename\n" +
                        "\nor should it use the PLAYERNAME as file name! If you are not using your server on OFFLINE_MODE" +
                        "\ndo not change this configuration."
        );

        ZONE_ID_OF_DAY_OF_TODAY = ConfigManager.getMainConfig().getOrSetDefaultValue("Settings.Time.ZONE_ID_OF_DAY_OF_TODAY",
                ZoneId.systemDefault().getId(),
                "The timezone used for the some of ECPlugins! This is useful" +
                        "\n when your HomeZone is not the same as the server zone!");
        DayOfToday.initialize();

        SIMPLE_DATE_FORMAT = new SimpleDateFormat(
                ConfigManager.getMainConfig().getOrSetDefaultValue("Settings.Time.SIMPLE_DATE_FORMAT", "dd/MM/yyyy")
        );
        DATE_FORMAT_WITH_HOURS = new SimpleDateFormat(
                ConfigManager.getMainConfig().getOrSetDefaultValue("Settings.Time.DATE_FORMAT_WITH_HOURS", "dd/MM/yyyy HH:mm")
        );

        ConfigManager.getMainConfig().saveIfNewDefaults();
    }

}
