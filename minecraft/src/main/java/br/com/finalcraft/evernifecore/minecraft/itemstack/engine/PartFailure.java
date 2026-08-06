package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import jakarta.annotation.Nonnull;

/**
 * A part that broke while it was allowed to run: a defect in the part, not a gap in the runtime and
 * not a mistake in the config.
 *
 * <p>This is what used to disappear into a blanket catch. It costs its own line and nothing else -
 * the parts that still work describe the item as usual - but it is named, so a bug can be reported
 * instead of being read as "that item just has no CustomModelData".</p>
 */
public final class PartFailure {

    private final String key;
    private final Throwable defect;

    public PartFailure(@Nonnull String key, @Nonnull Throwable defect) {
        this.key = key;
        this.defect = defect;
    }

    @Nonnull
    public String getKey() {
        return key;
    }

    @Nonnull
    public Throwable getDefect() {
        return defect;
    }

    /** The sentence a log carries: what broke, whose fault it is not, and what still worked. */
    @Nonnull
    public String describe() {
        return "The part '" + key + "' failed on this item (" + defect.getClass().getSimpleName()
                + ": " + defect.getMessage() + "). This is a defect in the part, not in your config - "
                + "please report it. The other parts still described the item; this one contributed nothing.";
    }

    @Override
    public String toString() {
        return describe();
    }

}
