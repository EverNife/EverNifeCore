package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.time.DayOfToday;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ECSettings {

    public static boolean useNamesInsteadOfUUIDToStorePlayerData = false;
    public static int daysSinceLastLoginToLoadPlayerDataInMemory = -1;

    //FCBukkitUtil
    public static boolean WARN_PLAYERS_WHEN_RECEIVED_ITEMS_WERE_SEND_TO_THE_GROUND;

    //Time Related
    public static String ZONE_ID_OF_DAY_OF_TODAY;
    public static DateTimeFormatter SIMPLE_DATE_FORMAT;
    public static DateTimeFormatter DATE_FORMAT_WITH_HOURS;
    public static int PAGEVIEWERS_REFRESH_TIME;

    //Guis
    public static int DEFAULT_GUI_UPDATE_TIME = 2;

    public static void initialize(){

        useNamesInsteadOfUUIDToStorePlayerData = ConfigManager.getMainConfig().getOrSetDefaultValue(
                "Settings.useNamesInsteadOfUUIDToStorePlayerData",
                false,
                "Should EverNifeCore store the PlayerData using the UUID of the player as the filename" +
                        "\nor should it use the PLAYERNAME as file name! If you are not using your server on" +
                        "\nOFFLINE_MODE do not change this configuration."
        );

        daysSinceLastLoginToLoadPlayerDataInMemory = ConfigManager.getMainConfig().getOrSetDefaultValue(
                "Settings.daysSinceLastLoginToLoadPlayerDataInMemory",
                -1,
                "The amount of days since the last login of a player that the PlayerData should be loaded." +
                        "\nIf the player has not logged in for this amount of days, his PlayerData will NOT be loaded." +
                        "\n" +
                        "\n-1 to load all PlayerData. (recommended)" +
                        "\n" +
                        "\nOnly change this value to a reasonable amount (like +30 days) and only if you have a lot of unique players!" +
                        "\nReally, if you don't know what you are doing, don't change this value!" +
                        "\nIf you have many of EverNife's plugins this will screw everything up!"
        );

        ZONE_ID_OF_DAY_OF_TODAY = ConfigManager.getMainConfig().getOrSetDefaultValue("Settings.Time.ZONE_ID_OF_DAY_OF_TODAY",
                ZoneId.systemDefault().getId(),
                "The timezone used for EverNifeCore and it's sub-plugins!" +
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

        WARN_PLAYERS_WHEN_RECEIVED_ITEMS_WERE_SEND_TO_THE_GROUND = ConfigManager.getMainConfig().getOrSetDefaultValue("Settings.FCBukkitUtil.warnWhenPlayersCannotReceiveItensOnItsInventory",
                true,
                "Plugins that use EverNifeCore as dependency, and use the method 'FCBukkitUtil.giveItemsTo'" +
                        "\nto give items to player might want to disable the warn that is send to him " +
                        "\nwhen there is no inventory space available on its inventory. Here you can disable" +
                        "\nthat message."
        );

        ConfigManager.getMainConfig().saveIfNewDefaults();
    }

}
