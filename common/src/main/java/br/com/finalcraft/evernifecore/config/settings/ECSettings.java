package br.com.finalcraft.evernifecore.config.settings;

import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpWords;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.cooldown.CooldownRetention;
import br.com.finalcraft.evernifecore.region.RegionIndex;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionSweeper;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.time.DayOfToday;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.util.FCTimeUtil;

import java.time.ZoneId;
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
    public static int REGIONS_LISTENER_IDLE_TICKS = EventSourceUsage.DEFAULT_IDLE_TICKS;
    public static int REGIONS_SWEEPER_INTERVAL_TICKS = 20;
    public static boolean REGIONS_VISUALIZER_ENABLED = false;
    public static int REGIONS_VISUALIZER_STEP = 1;
    public static int REGIONS_VISUALIZER_RADIUS = 48;
    public static int REGIONS_VISUALIZER_POINTS_PER_TICK = 300;
    public static int REGIONS_VISUALIZER_VIEW_SECONDS = 10;
    public static boolean REGIONS_SELECTION_LIVE_PREVIEW = false;
    public static int REGIONS_SELECTION_IDLE_MINUTES = 5;

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

        REGIONS_DISABLED_WORLDS = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Regions.disabled-worlds", Collections.<String>emptyList(),
                "Worlds where no region rule is ever checked - regions there still exist and load, but no" +
                        "\nflag decides anything. Cheaper than an empty world for a minigame map that has its own rules."
        );
        REGIONS_HUGE_CHUNKS = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Regions.huge-chunks", RegionIndex.DEFAULT_HUGE_CHUNKS,
                "A region whose bounding box spans more chunks than this is not indexed chunk by chunk;" +
                        "\nit is checked on every lookup of its world instead. Raising it trades memory for lookup speed."
        );
        REGIONS_LISTENER_IDLE_TICKS = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Regions.listener-idle-ticks", EventSourceUsage.DEFAULT_IDLE_TICKS,
                "How long (in ticks) an event listener nobody needs any more stays registered before it is removed." +
                        "\nA short value saves the listener's cost sooner; a long one avoids re-registering on churn."
        );
        REGIONS_SWEEPER_INTERVAL_TICKS = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Regions.sweeper-interval-ticks", 20,
                "How often (in ticks) expired flag values, memberships and transient regions are swept." +
                        "\nA deadline is honoured at most this late."
        );
        REGIONS_VISUALIZER_ENABLED = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Regions.visualizer.enabled", false,
                "Draws region outlines and live selections with particles when a command asks for it." +
                        "\nParticles cost one packet per point per tick, so this stays off until an admin turns it on;" +
                        "\nstep, radius and points-per-tick below bound the cost once it is on."
        );
        REGIONS_VISUALIZER_STEP = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Regions.visualizer.step", 1,
                "Blocks between two drawn points of an outline edge. 2 halves the packets."
        );
        REGIONS_VISUALIZER_RADIUS = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Regions.visualizer.radius", 48,
                "Only the outline points within this many blocks of the viewer are drawn."
        );
        REGIONS_VISUALIZER_POINTS_PER_TICK = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Regions.visualizer.points-per-tick", 300,
                "The most points one frame of an outline may draw for one player."
        );
        REGIONS_VISUALIZER_VIEW_SECONDS = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Regions.visualizer.view-seconds", 10,
                "How long an outline asked for by a 'view' command stays on screen."
        );
        REGIONS_SELECTION_LIVE_PREVIEW = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Regions.selection.live-preview", false,
                "Draws a selection while it is being made. Needs Regions.visualizer.enabled too - it costs the same particles."
        );
        REGIONS_SELECTION_IDLE_MINUTES = ConfigManager.getMainConfig().getOrSetValueIfAbsent(
                "Regions.selection.idle-minutes", 5,
                "A selection nobody touched for this many minutes is cancelled."
        );
        applyRegionSettings();

        if (ConfigManager.getMainConfig().hasNewSeededDefaults()){
            ConfigManager.getMainConfig().save();
            ConfigManager.getMainConfig().clearNewSeededDefaults();
        }
    }

    /**
     * Pushes the region keys into the components that hold a copy of them: the listener idle delay, the sweeper
     * interval and the huge-region threshold. Idempotent; the visualizer and the selection read their fields
     * when they need them, and the disabled worlds are read by the platform handler.
     */
    public static void applyRegionSettings(){
        EventSourceUsage.get().setIdleTicks(REGIONS_LISTENER_IDLE_TICKS);
        RegionSweeper.setIntervalMillis(REGIONS_SWEEPER_INTERVAL_TICKS * 50L);
        RegionLookup.get().setHugeChunks(REGIONS_HUGE_CHUNKS);
    }

}
