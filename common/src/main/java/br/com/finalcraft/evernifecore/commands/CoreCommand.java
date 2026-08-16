package br.com.finalcraft.evernifecore.commands;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;
import br.com.finalcraft.evernifecore.eventbus.ECNativeAudience;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageTheme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@FinalCMD(
        aliases = {"evernifecore","ecore"}
)
public class CoreCommand {

    /**
     * Built on the first {@code /ecore info}: the executor is instantiated before its locale fields
     * are filled, so a page built there would carry a message that is still null.
     */
    private static final class Page {

        static final PageViewer<ECPluginData> INSTALLED_PLUGINS = PageViewer.of(ECPluginData.class)
                .id("evernifecore:info")
                .source(() -> new ArrayList<>(ECPluginManager.getECPluginsMap().values()))
                .unlimitedEntries()
                .orderBy(ecPluginData -> ecPluginData.getMetaInfo().getName()).ascending()
                .setFormatLine(
                        FancyText.of("§7# ${number}: §e§l◆ §a ${value} §7§o(${version})").setHover("${plugin_info}")
                                .append("${can_update}").setHover("§aClique to go to DownloadLink").setClickLink("${update_link}")
                )
                .addRowPlaceholder("version", ecPlugin -> ecPlugin.getMetaInfo().getVersion())
                .addRowPlaceholder("can_update", ecPlugin -> ecPlugin.hasUpdate() ? "§b  [Update]" : "")
                .addRowPlaceholder("update_link", ecPlugin -> ecPlugin.hasUpdate() ? ecPlugin.getUpdateLink() : "")
                .addRowPlaceholder("plugin_info", ecPlugin -> {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("\n§d ▲ Name: §a" + ecPlugin.getMetaInfo().getName());
                    stringBuilder.append("\n§d ▲ Version: §a" + ecPlugin.getMetaInfo().getVersion());
                    stringBuilder.append("\n\n§d ▲ Is Up To Date: " + (ecPlugin.hasUpdate() ? "§c" : "§b") + !ecPlugin.hasUpdate());
                    stringBuilder.append("\n");
                    return stringBuilder.toString();
                })
                .theme(PageTheme.classic().withTotalCount())
                .build();
    }

    @FinalCMD.SubCMD(
            subcmd = "info",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Show info of the EverNifeCore and its addons!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Mostra informações do EverNifeCore e de seus addons!")
            },
            permission = PermissionNodes.EVERNIFECORE_COMMAND_INFO
    )
    public void info(FCommandSender sender, @Arg(value = "[page]", context = "[1:*]") Integer page){
        Page.INSTALLED_PLUGINS.send(page, sender);
    }

    @FinalCMD.SubCMD(
            subcmd = "reload",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Fully reload EverNifeCore! Including all PlayerData of all players!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Da reload no EverNifeCore! Incluindo todos os PlayerData de todos os jogadores!")
            },
            permission = PermissionNodes.EVERNIFECORE_COMMAND_RELOAD
    )
    public void reload(FCommandSender sender){
        ECPluginManager.reloadPlugin(sender, EverNifeCore.instance.getEcPluginData());
    }

    @FinalCMD.SubCMD(
            subcmd = "events",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Who is listening to which event - on the bus and on the server"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Quem escuta qual evento - no bus e no servidor")
            },
            permission = PermissionNodes.EVERNIFECORE_COMMAND_EVENTS
    )
    public void events(FCommandSender sender, @Arg("[type]") String type){
        ECEventBus bus = EverNifeCore.getEventBus();
        if (type == null) {
            sendEventsOverview(sender, bus);
            return;
        }

        Class<? extends IECEvent> eventType = resolveEventType(bus, type);
        if (eventType == null) {
            sender.sendMessage("§cNo event type called §e" + type + "§c. Give the full class name, or the simple name of one of "
                    + "the types the bus knows: §7" + knownTypes(bus).stream().map(Class::getSimpleName).sorted().collect(Collectors.joining(", ")));
            return;
        }

        sender.sendMessage("§6" + eventType.getName());
        List<ECEventSubscription<?>> subscriptions = bus.getSubscriptions(eventType);
        sender.sendMessage("§ebus: §7" + subscriptions.size() + " subscription(s), in delivery order");
        for (ECEventSubscription<?> subscription : subscriptions) {
            sender.sendMessage(" §7- §f" + subscription);
        }
        if (!ECEvent.class.isAssignableFrom(eventType)) {
            sender.sendMessage("§7audiences: never asked - the type implements IECEvent only, so no platform can see it");
            return;
        }
        for (ECNativeAudience audience : bus.getNativeAudiences()) {
            boolean listening = audience.hasListeners(eventType);
            sender.sendMessage("§e" + audience.name() + ": " + (listening ? "§alistening" : "§7silent"));
            for (String line : audience.describeListeners(eventType)) {
                sender.sendMessage(" §7- §f" + line);
            }
        }
    }

    private static void sendEventsOverview(FCommandSender sender, ECEventBus bus) {
        //by simple name for the eye, by full name so two types that share it stay two entries
        Map<Class<?>, List<ECEventSubscription<?>>> byType = new TreeMap<>(
                Comparator.<Class<?>, String>comparing(Class::getSimpleName).thenComparing(Class::getName));
        for (ECEventSubscription<?> subscription : bus.getSubscriptions()) {
            byType.computeIfAbsent(subscription.getEventType(), type -> new ArrayList<>()).add(subscription);
        }
        List<ECListenerWatch> watches = bus.getListenerWatches();

        sender.sendMessage("§6Event bus: §e" + byType.size() + " §7type(s) subscribed, §e" + watches.size() + " §7listener watch(es)");
        for (Map.Entry<Class<?>, List<ECEventSubscription<?>>> entry : byType.entrySet()) {
            String hover = entry.getKey().getName() + entry.getValue().stream()
                    .map(subscription -> "\n§7- §f" + subscription)
                    .collect(Collectors.joining());
            sender.sendMessage(FancyText.of("§e" + entry.getKey().getSimpleName() + " §7" + entry.getValue().size() + " subscription(s)")
                    .setHover(hover));
        }
        for (ECListenerWatch watch : watches) {
            sender.sendMessage("§dwatch §7" + watch);
        }
    }

    /**
     * The full class name first - on Bukkit the core's loader finds any plugin's class by name - and
     * otherwise a simple name that exactly one known type answers to.
     */
    private static Class<? extends IECEvent> resolveEventType(ECEventBus bus, String type) {
        try {
            Class<?> named = Class.forName(type, false, ECEventBus.class.getClassLoader());
            if (IECEvent.class.isAssignableFrom(named)) {
                return named.asSubclass(IECEvent.class);
            }
        } catch (ClassNotFoundException | LinkageError notByFullName) {
            //fall through to the simple-name match
        }
        Class<? extends IECEvent> match = null;
        for (Class<? extends IECEvent> candidate : knownTypes(bus)) {
            if (candidate.getSimpleName().equalsIgnoreCase(type)) {
                if (match != null && match != candidate) {
                    return null; //ambiguous: two known types share the simple name, the full name tells them apart
                }
                match = candidate;
            }
        }
        return match;
    }

    /**
     * Every event type the bus can name: the ones subscribed to AND the ones a listener watch follows.
     * The watched ones matter most here - an event nobody is subscribed to yet is exactly the one an
     * operator asks about, and its producer already named it to the bus.
     */
    private static Set<Class<? extends IECEvent>> knownTypes(ECEventBus bus) {
        Set<Class<? extends IECEvent>> types = new LinkedHashSet<>();
        for (ECEventSubscription<?> subscription : bus.getSubscriptions()) {
            types.add(subscription.getEventType());
        }
        for (ECListenerWatch watch : bus.getListenerWatches()) {
            types.addAll(watch.getEventTypes());
        }
        return types;
    }

}
