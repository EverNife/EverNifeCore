package br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer;

import jakarta.annotation.Nonnull;

/** An edit the recipe asked for that this runtime cannot apply, named next to the reason. */
public final class RefusedEdit {

    private final String name;
    private final String reason;

    public RefusedEdit(@Nonnull String name, @Nonnull String reason) {
        this.name = name;
        this.reason = reason;
    }

    /** The part key, or the builder call, that asked for it. */
    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return name + " (" + reason + ")";
    }

}
