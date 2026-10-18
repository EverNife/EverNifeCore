package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.everyconfig.annotation.Comment;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The settings of {@code damage-over-time}: whether armor counts. */
public class DamageOverTimeSettings extends FlagSettings {

    @Comment({"false deals the damage the game's way - armor reduces it, other plugins see the damage event.",
            "true lowers the health by the whole amount, armor or not."})
    @JsonProperty
    private boolean ignoreArmor = false;

    @JsonIgnore
    public boolean isIgnoreArmor() {
        return ignoreArmor;
    }

    @JsonIgnore
    public void setIgnoreArmor(boolean ignoreArmor) {
        this.ignoreArmor = ignoreArmor;
    }
}
