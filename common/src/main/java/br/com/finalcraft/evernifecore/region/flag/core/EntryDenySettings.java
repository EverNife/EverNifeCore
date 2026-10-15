package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.everyconfig.annotation.Comment;

/**
 * The settings of {@code deny-entry}: those of every movement denial, plus what happens to a player who is found
 * inside without having crossed the border - logged in there, or the region appeared or grew around them.
 */
public class EntryDenySettings extends MoveDenySettings {

    @Comment({"Send out a denied player found inside without crossing the border (logged in there, or the region appeared",
            "around them): to the revert point when it is fixed, spawn or worldspawn, else to stuckFallback, else to the",
            "server spawn. false leaves them where they are, free to walk out."})
    private boolean expelFoundInside = false;

    public boolean isExpelFoundInside() {
        return expelFoundInside;
    }

    public void setExpelFoundInside(boolean expelFoundInside) {
        this.expelFoundInside = expelFoundInside;
    }
}
