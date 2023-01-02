package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.time.DayOfToday;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

public class ECSettings {

    public static boolean useNamesInsteadOfUUIDToStorePlayerData = false;

    //Time Related
    public static String ZONE_ID_OF_DAY_OF_TODAY;
    public static DateTimeFormatter SIMPLE_DATE_FORMAT;
    public static DateTimeFormatter DATE_FORMAT_WITH_HOURS;
    public static int PAGEVIEWERS_REFRESH_TIME;

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
                "Should EverNifeCore store the PlayerData using the UUID of the player as the filename" +
                        "\nor should it use the PLAYERNAME as file name! If you are not using your server on" +
                        "\nOFFLINE_MODE do not change this configuration."
        );

        ZONE_ID_OF_DAY_OF_TODAY = ConfigManager.getMainConfig().getOrSetDefaultValue("Settings.Time.ZONE_ID_OF_DAY_OF_TODAY",
                ZoneId.systemDefault().getId(),
                "The timezone used for the some of ECPlugins!" +
                        "\nThis is useful when your HomeZone is not the same as the server zone!" +
                        "\n" +
                        "\nYou can use GMT zones, for example:" +
                        "\n - 'GMT'       # Time at GMT." +
                        "\n - 'GMT-3'     # Time at GMT minus 3 Hours." +
                        "\n - 'GMT+8'     # Time at GMT plus 8 Hours."
        );
        FCReflectionUtil.getField(DayOfToday.class, "INSTANCE")
                .set(null, new DayOfToday(ZONE_ID_OF_DAY_OF_TODAY));

        SIMPLE_DATE_FORMAT = DateTimeFormatter.ofPattern(
                ConfigManager.getMainConfig().getOrSetDefaultValue("Settings.Time.SIMPLE_DATE_FORMAT", "dd/MM/yyyy")
        );
        DATE_FORMAT_WITH_HOURS = DateTimeFormatter.ofPattern(
                ConfigManager.getMainConfig().getOrSetDefaultValue("Settings.Time.DATE_FORMAT_WITH_HOURS", "dd/MM/yyyy HH:mm")
        );

        PAGEVIEWERS_REFRESH_TIME = ConfigManager.getMainConfig().getOrSetDefaultValue("Settings.PageViewers.REFRESH_TIME", 5,
                "The default amount of time the result of a '/top' command should be cached." +
                        "\nFor example, when using FinalEconomy, the '/baltop' may be cached to prevent lag for X amount of seconds." +
                        "\nIncrease this value if you find lag related to this feature! (very unlikely)" +
                        "\nIf going to decrease, I suggest to at least keep to 1 second");

        ConfigManager.getMainConfig().saveIfNewDefaults();
    }

}
