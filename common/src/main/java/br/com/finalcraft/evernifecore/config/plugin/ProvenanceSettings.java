package br.com.finalcraft.evernifecore.config.plugin;

import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everyconfig.annotation.Comment;
import br.com.finalcraft.everyconfig.annotation.Key;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;

/**
 * The provenance block a plugin that asks for origins keeps as the {@value #SECTION} section of its own
 * {@code config.yml}: how long its records last and whether it records from boot. A plugin binds it as a field of its
 * settings bean and may extend it with keys of its own.
 */
public class ProvenanceSettings {

    public static final String SECTION = "provenance";

    @JsonProperty
    private Retention retention = new Retention();

    @Key("record-from-boot")
    @Comment({"true: record who placed, spawned or dropped what from boot on; false: record only while this plugin asks",
            "for origins - a block placed before then reads as natural."})
    @JsonProperty
    private boolean recordFromBoot = false;

    @JsonIgnore
    public Retention getRetention() {
        return retention;
    }

    @JsonIgnore
    public void setRetention(Retention retention) {
        this.retention = retention;
    }

    @JsonIgnore
    public boolean isRecordFromBoot() {
        return recordFromBoot;
    }

    @JsonIgnore
    public void setRecordFromBoot(boolean recordFromBoot) {
        this.recordFromBoot = recordFromBoot;
    }

    /** How long each kind of record is kept. */
    public static class Retention {

        @Key("player-placed")
        @Comment("How long a placed block remembers who placed it; an older record reads as natural and is dropped.")
        @JsonProperty
        private FCTimeFrame playerPlaced = FCTimeFrame.of(Duration.ofDays(30));

        @JsonIgnore
        public Duration getPlayerPlaced() {
            return playerPlaced.toDuration();
        }

        @JsonIgnore
        public void setPlayerPlaced(Duration playerPlaced) {
            this.playerPlaced = FCTimeFrame.of(playerPlaced);
        }
    }
}
