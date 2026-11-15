package br.com.finalcraft.evernifecore.config.plugin;

import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everyconfig.annotation.Comment;
import br.com.finalcraft.everyconfig.annotation.Key;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;

/**
 * The provenance block a plugin that asks for origins keeps as the {@value #SECTION} section of its own
 * {@code config.yml}: how long a placed block is remembered. The one record keeps the longest retention among the plugins
 * that have it open. A plugin that wants history from boot opens the record and calls {@code record()} in its enable. A
 * plugin binds it as a field of its settings bean and may extend it with keys of its own.
 */
public class ProvenanceSettings {

    public static final String SECTION = "provenance";

    @JsonProperty
    private Retention retention = new Retention();

    @JsonIgnore
    public Retention getRetention() {
        return retention;
    }

    @JsonIgnore
    public void setRetention(Retention retention) {
        this.retention = retention;
    }

    /** How long each kind of record is kept. */
    public static class Retention {

        @Key("player-placed")
        @Comment({"How long a placed block remembers who placed it; an older record reads as natural and is dropped. The record",
                "is shared by every plugin that asks for origins and keeps the longest of their retentions."})
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
