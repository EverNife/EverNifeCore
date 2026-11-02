package br.com.finalcraft.evernifecore.ecplugin;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.provenance.ProvenanceTracker;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.booster.BoosterManager;
import br.com.finalcraft.evernifecore.booster.Boosters;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.cooldown.Cooldowns;
import br.com.finalcraft.evernifecore.dialogue.Dialogues;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.flag.ActorHolders;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.group.GroupLookup;
import br.com.finalcraft.evernifecore.group.GroupManager;
import br.com.finalcraft.evernifecore.group.Groups;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.locale.LocaleOverlay;
import br.com.finalcraft.evernifecore.logic.CustomVariables;
import br.com.finalcraft.evernifecore.logic.Multipliers;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestinations;
import br.com.finalcraft.evernifecore.logic.value.Contributions;
import br.com.finalcraft.evernifecore.logic.value.DynamicValues;
import br.com.finalcraft.evernifecore.menu.MenuRegistry;
import br.com.finalcraft.evernifecore.notify.Notifier;
import br.com.finalcraft.evernifecore.npc.Npcs;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.ranking.RankedLists;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.prompt.PromptRegistry;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.types.TypeRegistries;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.waypoint.Waypoints;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ECPluginManager {

    /**
     * Map that holds all EverNifeCore Plugins that are using its features like localization or logging.
     */
    private static final Map<String, ECPluginData> EVERNIFECORE_PLUGINS_MAP = new LinkedHashMap<>();

    /**
     * The same data, keyed by the plugin object that was handed in. It answers before the platform is
     * asked to validate the object and to spell its name - the price a log line used to pay on every
     * single call. It never holds an entry {@link #EVERNIFECORE_PLUGINS_MAP} does not.
     */
    private static final Map<Object, ECPluginData> PLUGIN_DATA_BY_IDENTITY = new IdentityHashMap<>();

    /**
     * The {@link ECPluginData} of {@code plugin}, built the first time this object is seen and read
     * back from memory afterwards - only that first call asks the platform anything.
     */
    @Nonnull
    public static synchronized ECPluginData getOrCreateECorePluginData(Object plugin){
        if (plugin instanceof ECPluginData){
            return (ECPluginData) plugin;
        }

        ECPluginData known = PLUGIN_DATA_BY_IDENTITY.get(plugin);
        if (known != null){
            return known;
        }

        IECPluginExtractor ecPluginExtractor = EverNifeCore.instance
                .getProviders()
                .getECPluginExtractor();

        ecPluginExtractor.validateJavaPlugin(plugin);

        ECPluginData ecPluginData = EVERNIFECORE_PLUGINS_MAP.computeIfAbsent(
                ecPluginExtractor.getPluginName(plugin),
                pluginName -> new ECPluginData(plugin)
        );

        PLUGIN_DATA_BY_IDENTITY.put(plugin, ecPluginData);
        return ecPluginData;
    }

    public static ECPluginData getProvidingPlugin(@Nonnull Class<?> clazz) {
        Object providingPlugin = EverNifeCore.instance
                .getProviders()
                .getECPluginExtractor()
                .getProvidingPlugin(clazz);

        return getOrCreateECorePluginData(providingPlugin);
    }

    public static void reloadPlugin(@Nullable FCommandSender sender, @Nonnull ECPluginData ecPluginData) {
        if (!ecPluginData.canReload()){
            throw new IllegalStateException(String.format(
                    "The plugin [%s] does not implement a '@ECPlugin.Reload' System on it! Tell the author (%s) !",
                    ecPluginData.getMetaInfo().getName(),
                    ecPluginData.getMetaInfo().getAuthor()
            ));
        }

        reloadPlugin(sender, ecPluginData, () -> ecPluginData.reloadPlugin());
    }

    public static void reloadPlugin(@Nullable FCommandSender sender, ECPluginData ecPluginData, @Nonnull Runnable runnable){
        //Fire Pre-Reload
        //Mainly used for Plugins that has other addons or modules
        long start = System.currentTimeMillis();

        //up front, so the debug lines the reload itself emits already obey the edited file
        ecPluginData.loadDebugConfig();

        //Fire Pre-Reload before the reload runs, so listeners observe the old state
        EverNifeCore.getEventBus().post(new ECPluginReloadEvent.Pre(ecPluginData));

        //Do the reload
        runnable.run();
        //Reload locales as well
        ecPluginData.reloadAllCustomLocales();

        long end = System.currentTimeMillis();

        //Notify the Console
        ecPluginData.getLog().info("§e[Reloading] §a" + ecPluginData.getMetaInfo().getName() + " has been reloaded! §7(It took " + FCTimeFrame.of(end - start).getFormattedDiscursive(true) + ")");

        //Notify the sender if it's a Player
        if (sender != null) {
            FCMessageUtil.pluginHasBeenReloaded(sender, ecPluginData.getMetaInfo().getName());
//            FCSound.LEVEL_UP.playSoundFor((Player) sender);
        }

        //Some ECPlugins might have subModules or Addons that opt to reload after this one; reload them if necessary
        for (ECPluginData ecPlugin : getECPluginsMap().values()) {
            if (ecPlugin.canReload()){
                for (String pluginName : ecPlugin.getReloadAfter()) {
                    if (ecPluginData.getMetaInfo().getName().equalsIgnoreCase(pluginName)){
                        ecPlugin.getLog().info("[ECPlugin] Reloading by demand of '" + ecPluginData.getMetaInfo().getName() + "'.");
                        ecPlugin.reloadPlugin();
                    }
                }
            }
        }

        EverNifeCore.getEventBus().post(new ECPluginReloadEvent.Post(ecPluginData));
    }

    /**
     * Drops this plugin from the registry together with every shortcut pointing at its data: the
     * identity cache entries and whatever the plugin objects themselves cached
     * ({@link IECPluginBootstrap#clearCachedPluginData()}). Asked for again afterwards, the plugin
     * gets fresh data - the same answer it got before it was ever registered.
     *
     * <p>It is also the plugin's way out when it leaves without its shutdown hook (the platform calls it on
     * disable, for any plugin): it takes back the same set {@link IECPluginBootstrap#onECPluginShutdownPre()}
     * does, discarding what the regions, groups and quests still had to write, since the plugin's storage is
     * gone by then.</p>
     */
    public static synchronized void removePluginData(String pluginName){
        ECPluginData removed = EVERNIFECORE_PLUGINS_MAP.remove(pluginName);
        if (removed == null){
            return;
        }

        releaseEverythingOf(removed, false);

        //by value, not by removed.getPlugin(): a plugin re-instantiated at runtime leaves two objects
        //answering to one name, and both were handed this very data
        List<Object> orphaned = new ArrayList<>();
        for (Map.Entry<Object, ECPluginData> entry : PLUGIN_DATA_BY_IDENTITY.entrySet()) {
            if (entry.getValue() == removed){
                orphaned.add(entry.getKey());
            }
        }

        for (Object plugin : orphaned) {
            PLUGIN_DATA_BY_IDENTITY.remove(plugin);
            if (plugin instanceof IECPluginBootstrap){
                ((IECPluginBootstrap) plugin).clearCachedPluginData();
            }
        }
    }

    /**
     * Takes back everything the core keeps for {@code plugin}: its managers closed (provenance, regions, groups,
     * quests, cooldowns, variables, boosters, values, contributions, audit, rankings, dialogues, screens), its
     * registrations dropped (group kinds, prompts, menus, locale overlays, notify channels, waypoints, npcs, flags,
     * actor holders, currencies, destinations, types, multipliers, booster sources, event sources, usage watches,
     * listeners, commands, parsers, bus subscriptions). Every step is idempotent and runs even when an earlier one
     * threw - the failure goes to the plugin's log. {@code writePending} says whether the regions, groups and quests
     * write what is pending (the plugin's storage is still open) or discard it.
     */
    static void releaseEverythingOf(ECPluginData plugin, boolean writePending) {
        //the writers first: provenance and regions write to the plugin's storage, which the plugin closes right after
        step(plugin, "provenance", () -> ProvenanceTracker.closeOwnedBy(plugin));
        step(plugin, "regions", () -> {
            if (writePending) RegionManager.closeOwnedBy(plugin); else RegionManager.discardOwnedBy(plugin);
        });
        step(plugin, "groups", () -> {
            if (writePending) GroupManager.closeOwnedBy(plugin); else GroupManager.discardOwnedBy(plugin);
        });
        step(plugin, "quests", () -> {
            if (writePending) QuestEngine.closeOwnedBy(plugin); else QuestEngine.discardOwnedBy(plugin);
        });
        step(plugin, "group kinds", () -> GroupLookup.get().unregisterAll(plugin));
        step(plugin, "group network", () -> Groups.unregisterNetwork(plugin));
        step(plugin, "cooldowns", () -> Cooldowns.closeOwnedBy(plugin));
        step(plugin, "custom variables", () -> CustomVariables.closeOwnedBy(plugin));
        step(plugin, "boosters", () -> BoosterManager.closeOwnedBy(plugin));
        step(plugin, "dynamic values", () -> DynamicValues.closeOwnedBy(plugin));
        step(plugin, "contributions", () -> Contributions.closeOwnedBy(plugin));
        step(plugin, "audit log", () -> AuditLog.closeOwnedBy(plugin));
        step(plugin, "ranked lists", () -> RankedLists.closeOwnedBy(plugin));
        //before its screens: a session ends as OWNER_DISABLED, not CLOSED
        step(plugin, "dialogues", () -> Dialogues.closeOwnedBy(plugin));
        step(plugin, "screens", () -> Screens.closeOwnedBy(plugin));
        step(plugin, "prompt channels", () -> PromptRegistry.get().unregisterAll(plugin));
        step(plugin, "menus", () -> MenuRegistry.get().unregisterAll(plugin));
        step(plugin, "locale overlays", () -> LocaleOverlay.closeOwnedBy(plugin));
        step(plugin, "notify channels", () -> Notifier.get().closeOwnedBy(plugin));
        step(plugin, "waypoints", () -> Waypoints.get().hideOwnedBy(plugin));
        step(plugin, "npcs", () -> Npcs.unregisterAll(plugin));
        step(plugin, "flags", () -> FlagRegistry.get().unregisterAll(plugin));
        step(plugin, "actor holders", () -> ActorHolders.unregisterAll(plugin));
        step(plugin, "currencies", () -> Currencies.unregisterAll(plugin));
        step(plugin, "delivery destinations", () -> DeliveryDestinations.unregisterAll(plugin));
        step(plugin, "types", () -> TypeRegistries.unregisterAll(plugin));
        step(plugin, "multipliers", () -> Multipliers.unregisterAll(plugin));
        step(plugin, "booster sources", () -> Boosters.unregisterAll(plugin));
        step(plugin, "event sources", () -> EventSource.unregisterAll(plugin));
        step(plugin, "event source watches", () -> EventSourceUsage.stopAll(plugin));
        step(plugin, "listeners", () -> ECListener.unregisterAll(plugin));
        //only when there is one: the first touch of FinalCMDManager registers the platform's parsers
        step(plugin, "commands", () -> {
            if (!plugin.getRegisteredCommands().isEmpty()) FinalCMDManager.unregisterAllCommands(plugin);
        });
        step(plugin, "argument parsers", () -> ArgParserManager.unregisterAll(plugin));
        step(plugin, "bus subscriptions", () -> EverNifeCore.getEventBus().unsubscribeAll(plugin));
    }

    private static void step(ECPluginData plugin, String what, Runnable release) {
        try {
            release.run();
        } catch (RuntimeException failure) {
            plugin.getLog().warning("Releasing the {} of {} failed; the rest is still released.",
                    what, plugin.getMetaInfo().getName(), failure);
        }
    }

    /**
     * Every registered plugin by name, in registration order. A read-only snapshot: the registry is
     * the only place a plugin enters or leaves from, because dropping one has to take its cached
     * shortcuts down with it ({@link #removePluginData(String)}).
     */
    public static synchronized Map<String, ECPluginData> getECPluginsMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(EVERNIFECORE_PLUGINS_MAP));
    }
}

