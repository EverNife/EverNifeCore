package br.com.finalcraft.evernifecore.notify;

import br.com.finalcraft.evernifecore.config.plugin.NotifySettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The chain and the duration each {@link NotifyKind} of one plugin's notifications takes, read from the plugin's
 * {@link NotifySettings}. A plugin registers it at enable ({@link #register}); registering again (a reload) replaces it,
 * and what is already on screen keeps the duration it was shown with. A plugin that registers none, the core included,
 * gets {@link #getBuiltin()}. The profile leaves with its plugin.
 */
public final class NotifyProfile {

    private static final NotifyProfile BUILTIN = parse("the built-in default", new NotifySettings());
    private static final Map<ECPluginData, NotifyProfile> PROFILES = new ConcurrentHashMap<>();

    private final Map<NotifyKind, NotifyChain> chains;
    private final Map<NotifyKind, Duration> durations;

    private NotifyProfile(Map<NotifyKind, NotifyChain> chains, Map<NotifyKind, Duration> durations) {
        this.chains = chains;
        this.durations = durations;
    }

    /**
     * Reads {@code settings} as {@code owner}'s profile and puts it in place of the one {@code owner} had. A kind the
     * settings leave out takes the built-in one.
     *
     * @throws NotifyException naming the key when a key is no {@link NotifyKind}, a chain is malformed, or it names a
     *                         channel that is neither the core's nor written {@code <plugin>:<name>}; nothing changes then
     */
    public static NotifyProfile register(ECPluginData owner, NotifySettings settings) {
        NotifyProfile profile = parse(owner.getMetaInfo().getName() + " config.yml", settings);
        PROFILES.put(owner, profile);
        return profile;
    }

    /** {@code owner}'s registered profile, else {@link #getBuiltin()}. */
    public static NotifyProfile of(ECPluginData owner) {
        NotifyProfile profile = PROFILES.get(owner);
        return profile != null ? profile : BUILTIN;
    }

    /** The defaults of {@link NotifySettings}, for a plugin that registered no profile. */
    public static NotifyProfile getBuiltin() {
        return BUILTIN;
    }

    /** Drops {@code owner}'s profile; its notifications take the built-in one again. */
    public static void unregister(ECPluginData owner) {
        PROFILES.remove(owner);
    }

    /** The chain a notification of {@code kind} without its own takes. */
    public NotifyChain getChain(NotifyKind kind) {
        return chains.get(kind);
    }

    /** How long a notification of {@code kind} without its own duration stays; zero is until cleared. */
    public Duration getDuration(NotifyKind kind) {
        return durations.get(kind);
    }

    private static NotifyProfile parse(String where, NotifySettings settings) {
        Map<NotifyKind, NotifyChain> chains = new EnumMap<>(NotifyKind.class);
        Map<NotifyKind, Duration> durations = new EnumMap<>(NotifyKind.class);
        if (BUILTIN != null) {
            chains.putAll(BUILTIN.chains);
            durations.putAll(BUILTIN.durations);
        }
        for (Map.Entry<String, List<String>> entry : settings.getChains().entrySet()) {
            String key = where + " " + NotifySettings.SECTION + ".chains." + entry.getKey();
            chains.put(kindOf(key, entry.getKey()), readChain(key, entry.getValue()));
        }
        for (Map.Entry<String, FCTimeFrame> entry : settings.getDurations().entrySet()) {
            String key = where + " " + NotifySettings.SECTION + ".durations." + entry.getKey();
            NotifyKind kind = kindOf(key, entry.getKey());
            if (entry.getValue() == null) {
                throw new NotifyException(key + " has no value: write a duration like 3s, or 0s to stay until cleared.");
            }
            durations.put(kind, entry.getValue().toDuration());
        }
        return new NotifyProfile(Collections.unmodifiableMap(chains), Collections.unmodifiableMap(durations));
    }

    private static NotifyKind kindOf(String key, String name) {
        for (NotifyKind kind : NotifyKind.values()) {
            if (kind.getKey().equals(name)) {
                return kind;
            }
        }
        List<String> kinds = new ArrayList<>();
        for (NotifyKind kind : NotifyKind.values()) {
            kinds.add(kind.getKey());
        }
        throw new NotifyException(key + ": '" + name + "' is not a notification kind. The kinds are " + kinds + ".");
    }

    private static NotifyChain readChain(String key, List<String> written) {
        NotifyChain chain;
        try {
            chain = NotifyChain.parse(written == null ? Collections.<String>emptyList() : written);
        } catch (IllegalArgumentException invalid) {
            throw new NotifyException(key + ": " + written + " is not a chain. " + invalid.getMessage());
        }
        for (String name : chain.getChannelNames()) {
            if (!isKnownChannel(name)) {
                throw new NotifyException(key + ": " + written + " names the channel '" + name + "', which nobody registered."
                        + " The channels are " + Notifier.STOCK_CHANNELS + "; a plugin's channel is written <plugin>:<name>.");
            }
        }
        return chain;
    }

    //a <plugin>:<name> of another plugin resolves later, when that plugin registers it; a step it is in waits until then
    private static boolean isKnownChannel(String name) {
        int colon = name.indexOf(':');
        if (colon < 0) {
            return Notifier.STOCK_CHANNELS.contains(name) || Notifier.get().find(name).isPresent();
        }
        return !name.substring(0, colon).equals("evernifecore") || Notifier.STOCK_CHANNELS.contains(name.substring(colon + 1));
    }
}
