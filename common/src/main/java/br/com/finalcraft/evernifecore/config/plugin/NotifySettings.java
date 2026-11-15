package br.com.finalcraft.evernifecore.config.plugin;

import br.com.finalcraft.evernifecore.notify.NotifyKind;
import br.com.finalcraft.evernifecore.notify.NotifyProfile;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everyconfig.annotation.Comment;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The notification block a plugin keeps as the {@value #SECTION} section of its own {@code config.yml}: which channels
 * each {@link NotifyKind} goes through and how long it stays. A plugin binds it as a field of its settings bean (and may
 * extend it with keys of its own) and hands it to {@link NotifyProfile#register} at enable; keys are the
 * {@link NotifyKind#getKey() kind keys}. Its defaults are also the built-in profile of a plugin that registers none.
 */
public class NotifySettings {

    public static final String SECTION = "notify";

    @Comment({"The channels of each kind (progress, complete, tracked, info, deny). Each step is tried in order and the",
            "first one this platform fully supports is shown; '+' joins channels shown together. A plugin's channel is",
            "written <plugin>:<name>. The sidebar is opt-in: no default names it."})
    @JsonProperty
    private Map<String, List<String>> chains = defaultChains();

    @Comment({"How long a notification of each kind stays on the action bar, the title and the boss bar when the code",
            "that sends it sets no duration. 0s stays until it is cleared."})
    @JsonProperty
    private Map<String, FCTimeFrame> durations = defaultDurations();

    private static Map<String, List<String>> defaultChains() {
        Map<String, List<String>> chains = new LinkedHashMap<>();
        chains.put(NotifyKind.PROGRESS.getKey(), Arrays.asList("action-bar", "chat"));
        chains.put(NotifyKind.COMPLETE.getKey(), Arrays.asList("title+sound", "chat+sound", "chat"));
        chains.put(NotifyKind.TRACKED.getKey(), Arrays.asList("boss-bar", "tracker", "action-bar", "chat"));
        chains.put(NotifyKind.INFO.getKey(), Arrays.asList("chat"));
        chains.put(NotifyKind.DENY.getKey(), Arrays.asList("action-bar", "chat"));
        return chains;
    }

    private static Map<String, FCTimeFrame> defaultDurations() {
        Map<String, FCTimeFrame> durations = new LinkedHashMap<>();
        durations.put(NotifyKind.PROGRESS.getKey(), FCTimeFrame.of(Duration.ofSeconds(3)));
        durations.put(NotifyKind.COMPLETE.getKey(), FCTimeFrame.of(Duration.ofSeconds(5)));
        durations.put(NotifyKind.TRACKED.getKey(), FCTimeFrame.of(Duration.ZERO));
        durations.put(NotifyKind.INFO.getKey(), FCTimeFrame.of(Duration.ofSeconds(3)));
        durations.put(NotifyKind.DENY.getKey(), FCTimeFrame.of(Duration.ofSeconds(3)));
        return durations;
    }

    /** The chain of each kind key, as written: one entry per step, channels of a step joined by {@code +}. */
    @JsonIgnore
    public Map<String, List<String>> getChains() {
        return chains;
    }

    @JsonIgnore
    public void setChains(Map<String, List<String>> chains) {
        this.chains = chains;
    }

    /** The duration of each kind key; zero stays until cleared. */
    @JsonIgnore
    public Map<String, FCTimeFrame> getDurations() {
        return durations;
    }

    @JsonIgnore
    public void setDurations(Map<String, FCTimeFrame> durations) {
        this.durations = durations;
    }
}
