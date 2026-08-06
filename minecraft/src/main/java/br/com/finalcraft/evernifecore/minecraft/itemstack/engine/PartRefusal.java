package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import jakarta.annotation.Nonnull;

/** A part this runtime cannot answer for, and why. Not a failure - nothing was even attempted. */
public final class PartRefusal {

    private final String key;
    private final String reason;

    public PartRefusal(@Nonnull String key, @Nonnull String reason) {
        this.key = key;
        this.reason = reason;
    }

    @Nonnull
    public String getKey() {
        return key;
    }

    @Nonnull
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return key + " (" + reason + ")";
    }

}
