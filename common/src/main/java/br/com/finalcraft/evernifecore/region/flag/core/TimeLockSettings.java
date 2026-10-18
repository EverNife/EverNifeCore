package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everyconfig.annotation.Comment;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;

/** The settings of {@link TimeLockFlag}: how the player's clock gets to the locked time. */
public class TimeLockSettings extends PlayerStateSettings {

    @Comment("Ramp the player's clock to the locked time instead of jumping to it.")
    @JsonProperty
    private boolean smoothTransition = true;

    @Comment("How long the ramp takes; 0s jumps even with smoothTransition on.")
    @JsonProperty
    private FCTimeFrame transition = FCTimeFrame.of(Duration.ofSeconds(2));

    @JsonIgnore
    public boolean isSmoothTransition() {
        return smoothTransition;
    }

    @JsonIgnore
    public void setSmoothTransition(boolean smoothTransition) {
        this.smoothTransition = smoothTransition;
    }

    /** The ramp to use: zero when it jumps. */
    @JsonIgnore
    public Duration getTransition() {
        return smoothTransition ? transition.toDuration() : Duration.ZERO;
    }

    @JsonIgnore
    public void setTransition(Duration transition) {
        this.transition = FCTimeFrame.of(transition);
    }
}
