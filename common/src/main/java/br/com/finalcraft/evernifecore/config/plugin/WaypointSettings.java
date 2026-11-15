package br.com.finalcraft.evernifecore.config.plugin;

import br.com.finalcraft.evernifecore.waypoint.Waypoints;
import br.com.finalcraft.everyconfig.annotation.Comment;
import br.com.finalcraft.everyconfig.annotation.Key;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * The waypoint block a plugin keeps as the {@value #SECTION} section of its own {@code config.yml}: the channels its
 * waypoints point through and the length of the particle path. A plugin binds it as a field of its settings bean and
 * may extend it with keys of its own.
 */
public class WaypointSettings {

    public static final String SECTION = "waypoints";

    @Comment({"The channels a waypoint that names none points through; each supported one is used, the others skipped.",
            "The core has compass, particle-path, hologram and web-map (the last two only on Bukkit, with a hologram or",
            "web-map plugin installed); a plugin's own is written <plugin>:<name>."})
    @JsonProperty
    private List<String> channels = new ArrayList<>(Waypoints.STOCK_CHANNELS);

    @Key("particle-path-length")
    @Comment("How many particles, one block apart, the particle-path channel draws towards the target.")
    @JsonProperty
    private int particlePathLength = 16;

    @JsonIgnore
    public List<String> getChannels() {
        return channels;
    }

    @JsonIgnore
    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    @JsonIgnore
    public int getParticlePathLength() {
        return particlePathLength;
    }

    @JsonIgnore
    public void setParticlePathLength(int particlePathLength) {
        this.particlePathLength = particlePathLength;
    }
}
