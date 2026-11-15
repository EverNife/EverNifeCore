package br.com.finalcraft.evernifecore.config.plugin;

import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everyconfig.annotation.Comment;
import br.com.finalcraft.everyconfig.annotation.Key;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The region block a plugin that owns regions keeps as the {@value #SECTION} section of its own {@code config.yml}:
 * where its regions are not enforced, whether its flags keep a settings file, and how its outlines and selections
 * behave. A plugin binds it as a field of its settings bean and may extend it with keys of its own. Times are written
 * as {@link FCTimeFrame} ({@code 10s}, {@code 5m}); the {@link Duration} accessors are what code reads.
 */
public class RegionSettings {

    public static final String SECTION = "regions";

    @Key("disabled-worlds")
    @Comment({"Worlds where this plugin's regions are not enforced: none of their flags decides anything there. The",
            "regions still exist, load, save and answer the region API."})
    @JsonProperty
    private List<String> disabledWorlds = new ArrayList<>();

    @Key("flag-settings-files")
    @Comment({"Whether this plugin's flags keep their server-wide settings in regions/flags/<flag>.yml, in its data folder.",
            "While false no such file is written or read; a flag may still force its own file on or off."})
    @JsonProperty
    private boolean flagSettingsFiles = false;

    @Key("view-time")
    @Comment("How long an outline asked for by a 'view' command stays on screen.")
    @JsonProperty
    private FCTimeFrame viewTime = FCTimeFrame.of(Duration.ofSeconds(10));

    @JsonProperty
    private Selection selection = new Selection();

    @JsonIgnore
    public List<String> getDisabledWorlds() {
        return disabledWorlds;
    }

    @JsonIgnore
    public void setDisabledWorlds(List<String> disabledWorlds) {
        this.disabledWorlds = disabledWorlds;
    }

    @JsonIgnore
    public boolean isFlagSettingsFiles() {
        return flagSettingsFiles;
    }

    @JsonIgnore
    public void setFlagSettingsFiles(boolean flagSettingsFiles) {
        this.flagSettingsFiles = flagSettingsFiles;
    }

    @JsonIgnore
    public Duration getViewTime() {
        return viewTime.toDuration();
    }

    @JsonIgnore
    public void setViewTime(Duration viewTime) {
        this.viewTime = FCTimeFrame.of(viewTime);
    }

    @JsonIgnore
    public Selection getSelection() {
        return selection;
    }

    @JsonIgnore
    public void setSelection(Selection selection) {
        this.selection = selection;
    }

    /** How a region selection of this plugin behaves while it is being made. */
    public static class Selection {

        @Key("live-preview")
        @Comment("Draws a selection while it is being made. Needs the core's Regions.visualizer.enabled too - it costs the same particles.")
        @JsonProperty
        private boolean livePreview = false;

        @Key("idle-time")
        @Comment("A selection nobody touched for this long is cancelled.")
        @JsonProperty
        private FCTimeFrame idleTime = FCTimeFrame.of(Duration.ofMinutes(5));

        @JsonIgnore
        public boolean isLivePreview() {
            return livePreview;
        }

        @JsonIgnore
        public void setLivePreview(boolean livePreview) {
            this.livePreview = livePreview;
        }

        @JsonIgnore
        public Duration getIdleTime() {
            return idleTime.toDuration();
        }

        @JsonIgnore
        public void setIdleTime(Duration idleTime) {
            this.idleTime = FCTimeFrame.of(idleTime);
        }
    }
}
