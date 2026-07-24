package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.cooldown.CooldownRetention;
import br.com.finalcraft.evernifecore.time.DayOfToday;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.util.FCTimeUtil;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ECSettings {

    //FCBukkitUtil
    public static boolean WARN_PLAYERS_WHEN_RECEIVED_ITEMS_WERE_SEND_TO_THE_GROUND;

    //Time Related
    public static String ZONE_ID_OF_DAY_OF_TODAY;
    public static DateTimeFormatter SIMPLE_DATE_FORMAT = FCTimeUtil.FORMATTER_DATE_REVERSE;
    public static DateTimeFormatter DATE_FORMAT_WITH_HOURS = FCTimeUtil.FORMATTER_DATETIME_REVERSE;
    public static int PAGEVIEWERS_REFRESH_TIME;

    //Guis
    public static int DEFAULT_GUI_UPDATE_TIME = 2;

    //PageViewer
    public static boolean PAGEVIEWERS_FULL_LOCALIZATION = false;

    //Locale
    public static boolean PER_PLAYER_LOCALE = false;

    //Cooldown
    public static int COOLDOWN_RETENTION_DAYS;

    //Storage
    public static boolean STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = true;

    public static void initialize(){
        ZONE_ID_OF_DAY_OF_TODAY = ConfigManager.getMainConfig().getOrSetValueIfAbsent("Settings.Time.ZONE_ID_OF_DAY_OF_TODAY",
                ZoneId.systemDefault().getId(),
                "The timezone used for EverNifeCore and it's sub-plugins!" +
                        "\nThis is useful when your HomeZone is not the same as the server zone!" +
                        "\n" +
                        "\nYou can use GMT zones, for example:" +
                        "\n - 'GMT'       # Time at GMT." +
                        "\n - 'GMT-3'     # Time at GMT minus 3 Hours." +
                        "\n - 'GMT+8'     # Time at GMT plus 8 Hours."
        );
        FCReflectionUtil.getFields().getField(DayOfToday.class, "INSTANCE")
                .set(null, new DayOfToday(ZONE_ID_OF_DAY_OF_TODAY));

        SIMPLE_DATE_FORMAT = DateTimeFormatter.ofPattern(
                ConfigManager.getMainConfig().getOrSetValueIfAbsent("Settings.Time.SIMPLE_DATE_FORMAT", "dd/MM/yyyy")
        );
        DATE_FORMAT_WITH_HOURS = DateTimeFormatter.ofPattern(
                ConfigManager.getMainConfig().getOrSetValueIfAbsent("Settings.Time.DATE_FORMAT_WITH_HOURS", "dd/MM/yyyy HH:mm")
        );

        PAGEVIEWERS_REFRESH_TIME = ConfigManager.getMainConfig().getOrSetValueIfAbsent("Settings.PageViewers.REFRESH_TIME", 5,
                "The default amount of time the result of a '/top' command should be cached." +
                        "\nFor example, when using FinalEconomy, the '/baltop' may be cached to prevent lag for X amount of seconds." +
                        "\nIncrease this value if you find lag related to this feature! (very unlikely)" +
                        "\nIf going to decrease, I suggest to at least keep to 1 second");

        PAGEVIEWERS_FULL_LOCALIZATION = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Settings.PageViewers.FULL_LOCALIZATION",
                false,
                "If you want to enable the localization of the PageViewer's messages, set this to true." +
                        "\nThis will allow you to change the messages that are displayed on the PageViewer." +
                        "\n" +
                        "\nBy default the PageViewers will try to adjust to the content-size, but on non latin" +
                        "\nlanguages this may not work as expected. If you are having issues with the PageViewer" +
                        "\nmessages, enable this and customize the messages to fit your needs."
        );

        PER_PLAYER_LOCALE = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Settings.Locale.PER_PLAYER_LOCALE",
                false,
                "When enabled, each player may pick their own language with '/eclocale self <lang>'," +
                        "\nand messages are rendered in that language instead of the plugin's default." +
                        "\nWhile disabled (the default), no per-player language section is registered and" +
                        "\nevery message uses the plugin's configured locale, exactly as before."
        );

        WARN_PLAYERS_WHEN_RECEIVED_ITEMS_WERE_SEND_TO_THE_GROUND = ConfigManager.getMainConfig().getOrSetValueIfAbsent("Settings.FCBukkitUtil.warnWhenPlayersCannotReceiveItensOnItsInventory",
                true,
                "Plugins that use EverNifeCore as dependency, and use the method 'FCBukkitUtil.giveItemsTo'" +
                        "\nto give items to player might want to disable the warn that is send to him " +
                        "\nwhen there is no inventory space available on its inventory. Here you can disable" +
                        "\nthat message."
        );

        COOLDOWN_RETENTION_DAYS = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Settings.Cooldown.RETENTION_DAYS", 30,
                "How long a cooldown entry is kept AFTER its nominal duration ends, before it is pruned." +
                        "\nA read asking for a custom duration beyond this bound is answered as 'free' anyway," +
                        "\nso the entry can no longer change any answer once past it." +
                        "\nOverride it per cooldown id under Settings.Cooldown.RETENTION_OVERRIDES.<id>."
        );

        Map<String, Long> retentionOverrides = new LinkedHashMap<>();
        for (String cooldownId : ConfigManager.getMainConfig().getKeys("Settings.Cooldown.RETENTION_OVERRIDES")) {
            retentionOverrides.put(cooldownId, TimeUnit.DAYS.toMillis(
                    ConfigManager.getMainConfig().getInt("Settings.Cooldown.RETENTION_OVERRIDES." + cooldownId)));
        }
        CooldownRetention.configure(COOLDOWN_RETENTION_DAYS, retentionOverrides);

        STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Settings.Storage.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE", true,
                "Stop the server if we can't contact any used Storage Database." +
                        "\n" +
                        "\nWhen a database declared as 'enabled: true' in storage.yml cannot be reached at" +
                        "\nboot, EverNifeCore prints a report naming every unreachable backend and stops the" +
                        "\nserver, so nothing runs on top of missing data." +
                        "\n" +
                        "\nKEEP THIS TRUE. Setting it to false does NOT make the server work without a" +
                        "\ndatabase: EverNifeCore stays DISABLED either way, every plugin that depends on it" +
                        "\nfails, and the data that IS written diverges from what the database holds." +
                        "\nThe only safe way out of a boot failure is fixing (or disabling) the backend."
        );

        if (ConfigManager.getMainConfig().hasNewSeededDefaults()){
            ConfigManager.getMainConfig().save();
            ConfigManager.getMainConfig().clearNewSeededDefaults();
        }
    }

}
