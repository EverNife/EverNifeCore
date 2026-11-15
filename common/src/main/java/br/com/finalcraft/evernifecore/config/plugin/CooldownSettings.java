package br.com.finalcraft.evernifecore.config.plugin;

import br.com.finalcraft.everyconfig.annotation.Comment;
import br.com.finalcraft.everyconfig.annotation.Key;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The cooldown block a plugin keeps as the {@value #SECTION} section of its own {@code config.yml}: how long its
 * cooldown entries are kept after they end. A plugin binds it as a field of its settings bean and may extend it with
 * keys of its own.
 */
public class CooldownSettings {

    public static final String SECTION = "cooldowns";

    @Key("retention-days")
    @Comment({"How many days a cooldown entry of this plugin is kept AFTER its nominal duration ends, before it is pruned.",
            "null keeps the server's Settings.Cooldown.RETENTION_DAYS of EverNifeCore."})
    @JsonProperty
    private @Nullable Integer retentionDays;

    @Key("retention-overrides")
    @Comment("The retention, in days, of one cooldown id of this plugin: <id>: <days>. Wins over retention-days.")
    @JsonProperty
    private Map<String, Integer> retentionOverrides = new LinkedHashMap<>();

    /** The plugin's retention in days; null keeps the server's. */
    @JsonIgnore
    public @Nullable Integer getRetentionDays() {
        return retentionDays;
    }

    @JsonIgnore
    public void setRetentionDays(@Nullable Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    /** The retention in days of each cooldown id that has its own. */
    @JsonIgnore
    public Map<String, Integer> getRetentionOverrides() {
        return retentionOverrides;
    }

    @JsonIgnore
    public void setRetentionOverrides(Map<String, Integer> retentionOverrides) {
        this.retentionOverrides = retentionOverrides;
    }
}
