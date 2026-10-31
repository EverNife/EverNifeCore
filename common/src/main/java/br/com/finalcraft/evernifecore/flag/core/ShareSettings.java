package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.everyconfig.annotation.Comment;

/** The settings of {@code share-xp} and {@code share-money}: who is close enough to get a copy of a member's gain. */
public class ShareSettings extends FlagSettings {

    @Comment("How far, in blocks, a member may be from the one who gained to get a share; 0 = any distance.")
    private double radius = 50;

    @Comment("Whether only members in the same world get a share. Ignored with a radius, which is always one world.")
    private boolean sameWorld = true;

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public boolean isSameWorld() {
        return sameWorld;
    }

    public void setSameWorld(boolean sameWorld) {
        this.sameWorld = sameWorld;
    }
}
