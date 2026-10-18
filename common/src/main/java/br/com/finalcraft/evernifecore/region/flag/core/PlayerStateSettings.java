package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.playerstate.PlayerStateBackup;
import br.com.finalcraft.everyconfig.annotation.Comment;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The settings of a flag that changes a player while inside and gives back what they had on the way out. */
public class PlayerStateSettings extends FlagSettings {

    /** Where what the player had before is kept until it is given back. */
    public enum Restore {
        /** Also in the player's data, so it comes back on the next join after a server stop. */
        PERSISTENT,
        /** Only in memory: given back on leaving and on quitting, lost if the server stops with the player inside. */
        MEMORY
    }

    @Comment({"Where what the player had before entering is kept until it is given back: PERSISTENT also saves it with the",
            "player's data, so it comes back on the next join even after a crash; MEMORY keeps it only while the server runs."})
    @JsonProperty
    private Restore restore = Restore.PERSISTENT;

    @JsonIgnore
    public Restore getRestore() {
        return restore;
    }

    @JsonIgnore
    public void setRestore(Restore restore) {
        this.restore = restore;
    }

    /** Whether what the player had goes to their {@link PlayerStateBackup} too. */
    @JsonIgnore
    public boolean isPersistent() {
        return restore != Restore.MEMORY;
    }
}
