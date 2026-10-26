package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.everyconfig.annotation.Comment;

/** The settings of {@code damage-multiplier}: the bounds the product of every layer is kept within. */
public class DamageMultiplierSettings extends FlagSettings {

    @Comment("The lowest the multiplier goes, whatever the layers multiply to; 0 lets a layer cancel the damage out.")
    private double floor = 0;

    @Comment("The highest the multiplier goes, whatever the layers multiply to.")
    private double cap = 10;

    public double getFloor() {
        return floor;
    }

    public void setFloor(double floor) {
        this.floor = floor;
    }

    public double getCap() {
        return cap;
    }

    public void setCap(double cap) {
        this.cap = cap;
    }
}
