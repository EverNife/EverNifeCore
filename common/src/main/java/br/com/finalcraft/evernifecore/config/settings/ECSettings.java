package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpWords;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.cooldown.CooldownRetention;
import br.com.finalcraft.evernifecore.region.RegionIndex;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionSweeper;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.time.DayOfToday;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.util.FCTimeUtil;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.Collections;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ECSettings {

    //FCBukkitUtil
    public static boolean WARN_PLAYERS_WHEN_RECEIVED_ITEMS_WERE_SEND_TO_THE_GROUND;

    //Time Related
    public static String ZONE_ID_OF_DAY_OF_TODAY;
    public static DateTimeFormatter SIMPLE_DATE_FORMAT = FCTimeUtil.FORMATTER_DATE_REVERSE;
    public static DateTimeFormatter DATE_FORMAT_WITH_HOURS = FCTimeUtil.FORMATTER_DATETIME_REVERSE;

    //Guis
    public static int DEFAULT_GUI_UPDATE_TIME = 2;

    //Locale
    public static boolean PER_PLAYER_LOCALE = false;

    //Cooldown
    public static int COOLDOWN_RETENTION_DAYS;

    //Storage
    public static boolean STOP_SERVER_IF_STORAGE_IS_UNREACHABLE = true;

    //Commands
    public static boolean COMMAND_REGISTRY_FILES_ENABLED = false;
    public static List<String> COMMAND_HELP_WORDS = HelpWords.DEFAULTS;
    public static int COMMAND_HELP_PAGE_SIZE = 8;
    public static boolean COMMAND_LIST_ALIAS = true;

    //Regions - initialised to the shipped defaults, since tests and the command harness never run initialize()
    public static List<String> REGIONS_DISABLED_WORLDS = Collections.emptyList();
    public static int REGIONS_HUGE_CHUNKS = RegionIndex.DEFAULT_HUGE_CHUNKS;
    public static Duration REGIONS_LISTENER_IDLE_TIME = EventSourceUsage.DEFAULT_IDLE_TIME;
    public static Duration REGIONS_SWEEPER_INTERVAL = RegionSweeper.DEFAULT_INTERVAL;
    public static boolean REGIONS_VISUALIZER_ENABLED = false;
    public static int REGIONS_VISUALIZER_STEP = 1;
    public static int REGIONS_VISUALIZER_RADIUS = 48;
    public static int REGIONS_VISUALIZER_POINTS_PER_TICK = 300;
    public static Duration REGIONS_VISUALIZER_VIEW_TIME = Duration.ofSeconds(10);
    public static boolean REGIONS_SELECTION_LIVE_PREVIEW = false;
    public static Duration REGIONS_SELECTION_IDLE_TIME = Duration.ofMinutes(5);
    public static boolean REGIONS_GENERATE_GLOBAL_SETTINGS_FILES = false;

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

        COMMAND_REGISTRY_FILES_ENABLED = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Settings.Commands.REGISTRY_FILES_ENABLED", false,
                "Set to 'true' to generate plugins/EverNifeCore/commands/<PluginName>.yml, where each" +
                        "\ncommand and sub-command can be disabled or given extra aliases. While 'false' nothing is" +
                        "\nread or written and the annotations are the only source of truth."
        );

        COMMAND_HELP_WORDS = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Settings.Commands.HELP_WORDS", HelpWords.DEFAULTS,
                "The words that open a command's help instead of naming a sub-command, at any depth:" +
                        "\n'/mycmd help', '/mycmd user Steve ajuda'. Add your server's language here." +
                        "\n" +
                        "\nThey intercept, they never reserve: a sub-command labelled with one of these words," +
                        "\nor a capture standing where it was typed, still wins the token. Leaving the list" +
                        "\nempty is not a way to turn the help off - the shipped words come back."
        );
        HelpWords.configure(COMMAND_HELP_WORDS);

        COMMAND_LIST_ALIAS = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Settings.Commands.LIST_ALIAS", true,
                "While 'true' EverNifeCore's online player list also answers '/list', replacing the server's own." +
                        "\nSet to 'false' to keep the server's '/list'; the core's list stays on '/playerlist'."
        );

        COMMAND_HELP_PAGE_SIZE = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Settings.Commands.HELP_PAGE_SIZE", 8,
                "How many sub-command lines one page of a command's help shows. A help that fits in a" +
                        "\nsingle page is printed with no page indicator at all, exactly as it always was."
        );

        loadRegionSettings(ConfigManager.getMainConfig());
        applyRegionSettings();

        if (ConfigManager.getMainConfig().hasNewSeededDefaults()){
            ConfigManager.getMainConfig().save();
            ConfigManager.getMainConfig().clearNewSeededDefaults();
        }
    }

    /**
     * A region time key named for its unit: from before those keys took a duration and lost the unit. Dropped unread,
     * so a file never holds two keys for one setting with only one of them working.
     */
    private static final Pattern RETIRED_TIME_KEY = Pattern.compile(".*-(ticks|seconds|minutes)");

    /** Reads every {@code Regions.*} key of {@code config} into the fields above, seeding what is absent. */
    public static void loadRegionSettings(Config config){
        for (String key : new ArrayList<>(config.getKeys("Regions", true))) {
            if (RETIRED_TIME_KEY.matcher(key).matches()) {
                config.removeValue("Regions." + key);
            }
        }
        REGIONS_DISABLED_WORLDS = config.getOrSetValueIfAbsent(
                "Regions.disabled-worlds", Collections.<String>emptyList(),
                "Worlds where regions are not enforced: no flag of any plugin decides anything there, and the protection" +
                        "\ncheck other plugins ask (ProtectionAll) answers 'allowed'. Regions in these worlds still exist, load," +
                        "\nsave and answer the region API. Use it for worlds whose rules come from somewhere else (a minigame" +
                        "\nmap, a creative world)."
        );
        REGIONS_HUGE_CHUNKS = config.getOrSetValueIfAbsent(
                "Regions.huge-chunks", RegionIndex.DEFAULT_HUGE_CHUNKS,
                "A region spanning more chunks than this is not indexed chunk by chunk: it is checked on every region" +
                        "\nlookup in its world, wherever the lookup is, while a smaller region is checked only where it lies." +
                        "\nRaise it when a few big regions slow down lookups across their whole world (every indexed chunk costs" +
                        "\nmemory); lower it when memory matters more than lookup speed in worlds with very large regions."
        );
        REGIONS_LISTENER_IDLE_TIME = readDuration(config,
                "Regions.listener-idle-time", EventSourceUsage.DEFAULT_IDLE_TIME,
                "How long the server keeps listening to a kind of game event (block break, movement, damage...) after the" +
                        "\nlast region flag that needed it is gone. Short frees the listener sooner; long avoids re-registering it" +
                        "\nwhen regions with that flag come and go often. It never changes what a flag decides."
        );
        REGIONS_SWEEPER_INTERVAL = readDuration(config,
                "Regions.sweeper-interval", RegionSweeper.DEFAULT_INTERVAL,
                "How often expired things are swept: temporary regions, flag values set with a time limit and timed" +
                        "\ngroup memberships. The sweeper only runs while something has a time limit - with none, it costs nothing." +
                        "\nA time limit is applied at most one interval after it passes (with 1s, a region that expires at 12:00:00" +
                        "\nis gone by 12:00:01), and its event goes out on the world's thread. Lowering it costs more, since every" +
                        "\nsweep walks all loaded regions; raise it when exact expiry times do not matter on your server."
        );
        REGIONS_VISUALIZER_ENABLED = config.getOrSetValueIfAbsent(
                "Regions.visualizer.enabled", false,
                "Draws region outlines and live selections with particles when a command asks for it." +
                        "\nParticles cost one packet per point per tick, so this stays off until an admin turns it on;" +
                        "\nstep, radius and points-per-tick below bound the cost once it is on."
        );
        REGIONS_VISUALIZER_STEP = config.getOrSetValueIfAbsent(
                "Regions.visualizer.step", 1,
                "Blocks between two drawn points of an outline edge. 2 halves the packets."
        );
        REGIONS_VISUALIZER_RADIUS = config.getOrSetValueIfAbsent(
                "Regions.visualizer.radius", 48,
                "Only the outline points within this many blocks of the viewer are drawn."
        );
        REGIONS_VISUALIZER_POINTS_PER_TICK = config.getOrSetValueIfAbsent(
                "Regions.visualizer.points-per-tick", 300,
                "The most points one frame of an outline may draw for one player."
        );
        REGIONS_VISUALIZER_VIEW_TIME = readDuration(config,
                "Regions.visualizer.view-time", Duration.ofSeconds(10),
                "How long an outline asked for by a 'view' command stays on screen."
        );
        REGIONS_SELECTION_LIVE_PREVIEW = config.getOrSetValueIfAbsent(
                "Regions.selection.live-preview", false,
                "Draws a selection while it is being made. Needs Regions.visualizer.enabled too - it costs the same particles."
        );
        REGIONS_SELECTION_IDLE_TIME = readDuration(config,
                "Regions.selection.idle-time", Duration.ofMinutes(5),
                "A selection nobody touched for this long is cancelled."
        );
        REGIONS_GENERATE_GLOBAL_SETTINGS_FILES = config.getOrSetValueIfAbsent(
                "Regions.generate-global-settings-configuration-files", false,
                "Whether region flags keep their server-wide settings in a file: regions/flags/<flag>.yml, in the data" +
                        "\nfolder of the plugin that owns the flag. While false no such file is written or read - flags run on" +
                        "\ntheir built-in settings plus what each region overrides, and an old file left behind is ignored (the log" +
                        "\nsays so). Turn it on to tune a flag for the whole server; a flag may also force its own file on or off." +
                        "\nA reload of EverNifeCore applies a change to the flags of every plugin."
        );
    }

    /** A time key: written readable ({@code 3m}), read in that form or as ISO-8601. */
    private static Duration readDuration(Config config, String key, Duration def, String comment){
        return config.getOrSetValueIfAbsent(key, FCTimeFrame.of(def), comment).toDuration();
    }

    /**
     * Pushes the region keys into the components that hold a copy of them: the listener idle delay, the sweeper
     * interval and the huge-region threshold. Idempotent; the visualizer and the selection read their fields
     * when they need them, and the disabled worlds are read by the platform handler.
     */
    public static void applyRegionSettings(){
        EventSourceUsage.get().setIdleTime(REGIONS_LISTENER_IDLE_TIME);
        RegionSweeper.setInterval(REGIONS_SWEEPER_INTERVAL);
        RegionLookup.get().setHugeChunks(REGIONS_HUGE_CHUNKS);
    }

}
