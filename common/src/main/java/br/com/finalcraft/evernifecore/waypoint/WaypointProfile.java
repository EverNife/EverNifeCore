package br.com.finalcraft.evernifecore.waypoint;

import br.com.finalcraft.evernifecore.config.plugin.WaypointSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The channels and the particle path length one plugin's waypoints take when they set none, read from the plugin's
 * {@link WaypointSettings}. A plugin registers it at enable ({@link #register}); registering again (a reload) replaces it
 * for the next refresh. A plugin that registers none, the core included, gets {@link #getBuiltin()}. The profile leaves
 * with its plugin.
 */
public final class WaypointProfile {

    private static final WaypointProfile BUILTIN = parse("the built-in default", new WaypointSettings());
    //by the lowercased plugin name, the owner a WaypointId carries
    private static final Map<String, WaypointProfile> PROFILES = new ConcurrentHashMap<>();

    private final List<String> channels;
    private final int particlePathLength;

    private WaypointProfile(List<String> channels, int particlePathLength) {
        this.channels = channels;
        this.particlePathLength = particlePathLength;
    }

    /**
     * Reads {@code settings} as {@code owner}'s profile and puts it in place of the one {@code owner} had.
     *
     * @throws WaypointException naming the key when a channel is neither the core's nor written {@code <plugin>:<name>},
     *                           or the path length is below 1; nothing changes then
     */
    public static WaypointProfile register(ECPluginData owner, WaypointSettings settings) {
        WaypointProfile profile = parse(owner.getMetaInfo().getName() + " config.yml", settings);
        PROFILES.put(ownerKey(owner), profile);
        return profile;
    }

    /** {@code owner}'s registered profile, else {@link #getBuiltin()}. */
    public static WaypointProfile of(ECPluginData owner) {
        return of(ownerKey(owner));
    }

    /** The profile of the plugin {@code id} names, else {@link #getBuiltin()}. */
    public static WaypointProfile of(WaypointId id) {
        return of(id.getOwner());
    }

    /** The defaults of {@link WaypointSettings}, for a plugin that registered no profile. */
    public static WaypointProfile getBuiltin() {
        return BUILTIN;
    }

    /** Drops {@code owner}'s profile; its waypoints take the built-in one again. */
    public static void unregister(ECPluginData owner) {
        PROFILES.remove(ownerKey(owner));
    }

    /** The channel names a waypoint without its own points through: bare names or {@code <plugin>:<name>} keys. */
    public List<String> getChannels() {
        return channels;
    }

    /** How many points the particle path of a waypoint without its own length draws. */
    public int getParticlePathLength() {
        return particlePathLength;
    }

    private static WaypointProfile of(String ownerKey) {
        WaypointProfile profile = PROFILES.get(ownerKey);
        return profile != null ? profile : BUILTIN;
    }

    private static String ownerKey(ECPluginData owner) {
        return owner.getMetaInfo().getName().toLowerCase(Locale.ROOT);
    }

    private static WaypointProfile parse(String where, WaypointSettings settings) {
        List<String> channels = settings.getChannels() == null ? Collections.<String>emptyList() : settings.getChannels();
        for (String name : channels) {
            //a <plugin>:<name> of another plugin resolves at show time, once that plugin registered it
            if (name.indexOf(':') < 0 && !Waypoints.KNOWN_CHANNELS.contains(name)) {
                throw new WaypointException(where + " " + WaypointSettings.SECTION + ".channels: " + channels + " names the channel '"
                        + name + "', which nobody registered. The channels are " + Waypoints.KNOWN_CHANNELS + "; a plugin's channel"
                        + " is written <plugin>:<name>.");
            }
        }
        if (settings.getParticlePathLength() < 1) {
            throw new WaypointException(where + " " + WaypointSettings.SECTION + ".particle-path-length is "
                    + settings.getParticlePathLength() + ": write how many particles the path draws, 1 or more.");
        }
        return new WaypointProfile(Collections.unmodifiableList(new ArrayList<>(channels)), settings.getParticlePathLength());
    }
}
