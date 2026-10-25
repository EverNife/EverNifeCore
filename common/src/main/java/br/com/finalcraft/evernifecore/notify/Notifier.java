package br.com.finalcraft.evernifecore.notify;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.notify.channel.ActionBarChannel;
import br.com.finalcraft.evernifecore.notify.channel.BossBarChannel;
import br.com.finalcraft.evernifecore.notify.channel.ChatChannel;
import br.com.finalcraft.evernifecore.notify.channel.SidebarChannel;
import br.com.finalcraft.evernifecore.notify.channel.SoundChannel;
import br.com.finalcraft.evernifecore.notify.channel.TitleChannel;
import br.com.finalcraft.evernifecore.notify.channel.ToastChannel;
import br.com.finalcraft.evernifecore.notify.channel.TrackerChannel;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivers a {@link Notification} through the first step of its chain this platform supports: the chain set on the
 * notification, else the kind's ({@code Settings.Notify.chains.<kind>}). Channels live in a {@link TypeRegistry}
 * ({@link #registry()}); the core registers chat, action bar, title, sound, boss bar, sidebar, toast and tracker, and a
 * plugin adds its own with {@link #register}. A player who quits is forgotten by every channel.
 */
public final class Notifier {

    /** The bare names of the core's channels, in the order they register. */
    public static final List<String> STOCK_CHANNELS = Collections.unmodifiableList(Arrays.asList(
            "chat", "action-bar", "title", "sound", "boss-bar", "sidebar", "toast", "tracker"));

    private static final Notifier INSTANCE = new Notifier();

    private final TypeRegistry<NotifyChannel> registry = TypeRegistry.of(NotifyChannel.class, "notify-channel");
    //weak: a descriptor that left the registry (its plugin shut down) takes its channel with it
    private final Map<TypeDescriptor<NotifyChannel>, NotifyChannel> channels =
            Collections.synchronizedMap(new WeakHashMap<TypeDescriptor<NotifyChannel>, NotifyChannel>());
    //the core's channels are one set of instances for the life of the process: a core reload re-registers the same ones
    private final List<NotifyChannel> stock = Collections.unmodifiableList(Arrays.<NotifyChannel>asList(
            new ChatChannel(), new ActionBarChannel(), new TitleChannel(), new SoundChannel(),
            new BossBarChannel(), new SidebarChannel(), new ToastChannel(), new TrackerChannel()));
    private final Set<String> reportedUnsupported = ConcurrentHashMap.newKeySet();

    private Notifier() {
        ECEventBus.global().subscribe(ECPlayerQuitEvent.class, event -> onQuit(event.getPlayerData().getUniqueId()));
    }

    public static Notifier get() {
        return INSTANCE;
    }

    /** The channels by key; {@link #register} is how a channel gets in. */
    public TypeRegistry<NotifyChannel> registry() {
        return registry;
    }

    /**
     * Registers the core's 8 channels under {@code core}, the EverNifeCore plugin data.
     *
     * @throws IllegalStateException when they are registered already
     */
    public void registerStock(ECPluginData core) {
        for (int i = 0; i < stock.size(); i++) {
            register(core, stock.get(i), STOCK_DESCRIPTIONS[i]);
        }
    }

    private static final String[] STOCK_DESCRIPTIONS = {
            "The chat line: always there, the last step of every default chain.",
            "Above the hotbar; one at a time per player, the highest priority wins, the same id replaces.",
            "The big centred title and its subtitle; queued like the action bar.",
            "Plays the notification's sound; a notification without one plays nothing.",
            "A bar at the top of the screen with the notification's progress; same id updates it.",
            "The scoreboard sidebar: a heading and lines. Opt-in, no default chain uses it.",
            "A one-shot popup: an advancement toast in Minecraft, a notification in Hytale.",
            "The quest tracker: a heading and progress lines; same id updates it.",
    };

    /**
     * Registers {@code channel} under {@code owner}, the plugin its key names; it leaves with {@code owner}'s shutdown.
     *
     * @throws IllegalStateException when the key is already registered
     */
    public void register(ECPluginData owner, NotifyChannel channel, String description) {
        TypeDescriptor<NotifyChannel> descriptor = TypeDescriptor.<NotifyChannel>of(channel.getKey())
                .describe(description)
                .factory(params -> channel)
                .build();
        channels.put(descriptor, channel);
        registry.register(owner, descriptor);
    }

    /** The channel {@code name} names: a bare name or an {@code owner:name} key; empty when none is registered. */
    public Optional<NotifyChannel> find(String name) {
        return registry.find(name).map(channels::get);
    }

    /** Every registered channel, in registration order. */
    public List<NotifyChannel> getChannels() {
        List<NotifyChannel> all = new ArrayList<>();
        for (TypeDescriptor<NotifyChannel> descriptor : registry.getAll()) {
            NotifyChannel channel = channels.get(descriptor);
            if (channel != null) {
                all.add(channel);
            }
        }
        return all;
    }

    /** The bare names of the registered channels this platform supports, the list an error message teaches. */
    public List<String> getSupportedChannelNames() {
        List<String> names = new ArrayList<>();
        for (TypeDescriptor<NotifyChannel> descriptor : registry.getAll()) {
            NotifyChannel channel = channels.get(descriptor);
            if (channel != null && channel.isSupported()) {
                names.add(descriptor.getName());
            }
        }
        return names;
    }

    /** The configured chain of {@code kind} ({@code Settings.Notify.chains.<kind>}). */
    public NotifyChain getChain(NotifyKind kind) {
        return ECSettings.getNotifyChain(kind);
    }

    /**
     * The channels of the first step of {@code chain} that are all registered and supported here; null when no step
     * is. A channel of a plugin that has not registered yet makes its step unsupported until it does.
     */
    public @Nullable List<NotifyChannel> resolve(NotifyChain chain) {
        for (Set<String> step : chain.getSteps()) {
            List<NotifyChannel> resolved = new ArrayList<>();
            for (String name : step) {
                NotifyChannel channel = find(name).orElse(null);
                if (channel == null || !channel.isSupported()) {
                    resolved = null;
                    break;
                }
                resolved.add(channel);
            }
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    /**
     * Delivers through the first supported step of the notification's chain (its own, else its kind's). Never throws
     * for a platform without a supported step: it delivers nothing and logs once per chain and platform -
     * {@link #validate} is the load-time check that keeps that from happening. Thread: any.
     */
    public void notify(FPlayer player, Notification notification) {
        NotifyChain chain = notification.getChannels() != null ? notification.getChannels() : getChain(notification.getKind());
        List<NotifyChannel> step = resolve(chain);
        if (step == null) {
            String platform = EverNifeCore.getPlatform().getPlatformProviderId();
            if (reportedUnsupported.add(chain.describe() + "|" + platform)) {
                EverNifeCore.getLog().warning("The notify chain " + chain.describe() + " (" + notification.getKind().getKey()
                        + ") has no step this platform (" + platform + ") supports, so nothing was shown. Add a step with a"
                        + " channel it has: " + getSupportedChannelNames() + ".");
            }
            return;
        }
        for (NotifyChannel channel : step) {
            channel.deliver(player, notification);
        }
    }

    /** {@link #notify(FPlayer, Notification)} to each player. Thread: any. */
    public void notify(Collection<? extends FPlayer> players, Notification notification) {
        for (FPlayer player : players) {
            notify(player, notification);
        }
    }

    /** Drops what {@code id} shows {@code player} on every channel: a boss bar, a tracker, a queued action bar or title. Thread: any. */
    public void clear(FPlayer player, String id) {
        for (NotifyChannel channel : getChannels()) {
            channel.clear(player, id);
        }
    }

    /**
     * Fails when no step of {@code chain} is supported on this platform - the check a plugin runs at load on every chain
     * it declares, so a chain that would show nothing is found at boot, not by a player.
     *
     * @param what the chain's name for the message, e.g. {@code tracked}
     * @throws NotifyException naming the chain, this platform and the channels it supports
     */
    public void validate(ECPluginData caller, String what, NotifyChain chain) throws NotifyException {
        if (resolve(chain) != null) {
            return;
        }
        throw new NotifyException("The notify chain " + chain.describe() + " of " + caller.getMetaInfo().getName().toLowerCase(Locale.ROOT)
                + ":" + what + " has no step this platform (" + EverNifeCore.getPlatform().getPlatformProviderId() + ") supports"
                + " - add a step with a channel it has " + getSupportedChannelNames() + ".");
    }

    /** Every channel {@code owner} registered forgets its players and stops ({@link NotifyChannel#clearAll()}). */
    public void closeOwnedBy(ECPluginData owner) {
        for (TypeDescriptor<NotifyChannel> descriptor : registry.getAll()) {
            NotifyChannel channel = channels.get(descriptor);
            if (channel != null && registry.getOwner(descriptor).map(found -> found == owner).orElse(false)) {
                channel.clearAll();
            }
        }
    }

    /**
     * Every channel, registered or not, forgets every player and stops its ticks, waiting for one already running: after
     * it returns nothing writes to the platform that was installed. Called when a test world closes.
     */
    public void clearAll() {
        for (NotifyChannel channel : stock) {
            channel.clearAll();
        }
        for (NotifyChannel channel : getChannels()) {
            if (!stock.contains(channel)) {
                channel.clearAll();
            }
        }
    }

    private void onQuit(UUID player) {
        for (NotifyChannel channel : stock) {
            channel.onQuit(player);
        }
        for (NotifyChannel channel : getChannels()) {
            if (!stock.contains(channel)) {
                channel.onQuit(player);
            }
        }
    }
}
