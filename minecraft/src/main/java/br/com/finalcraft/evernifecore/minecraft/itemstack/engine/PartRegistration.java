package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * How a part enters an engine: its key, the spellings that reach it, what it needs, and how to make
 * one.
 *
 * <p>The part arrives as a {@link Supplier} and not as an instance on purpose. A registration this
 * runtime refuses is never asked for its part, so the class is never loaded - which is what lets a
 * part name api that only newer servers have without breaking older ones.</p>
 */
public final class PartRegistration {

    @Nonnull
    public static PartRegistration of(@Nonnull String key, @Nonnull ItemRequirement requirement,
                                      @Nonnull String[] aliases,
                                      @Nonnull Supplier<ItemDataPart<?>> supplier) {
        return new PartRegistration(key, requirement, aliases, supplier, null);
    }

    private final String key;
    private final ItemRequirement requirement;
    private final String[] aliases;
    private final Supplier<ItemDataPart<?>> supplier;
    private final String hint;

    private PartRegistration(String key, ItemRequirement requirement, String[] aliases,
                             Supplier<ItemDataPart<?>> supplier, String hint) {
        this.key = key;
        this.requirement = requirement;
        this.aliases = aliases.clone();
        this.supplier = supplier;
        this.hint = hint;
    }

    /** What to write instead, on a runtime that refuses this part. Shown inside the refusal. */
    @Nonnull
    public PartRegistration orWrite(@Nonnull String hint) {
        return new PartRegistration(key, requirement, aliases, supplier, hint);
    }

    @Nonnull
    public String getKey() {
        return key;
    }

    @Nonnull
    public ItemRequirement getRequirement() {
        return requirement;
    }

    /** The canonical key first, then every alias that also reaches this part. */
    @Nonnull
    public String[] getSpellings() {
        String[] spellings = new String[aliases.length + 1];
        spellings[0] = key;
        System.arraycopy(aliases, 0, spellings, 1, aliases.length);
        return spellings;
    }

    @Nullable
    public String getHint() {
        return hint;
    }

    @Nonnull
    ItemDataPart<?> newPart() {
        return supplier.get();
    }

    @Override
    public String toString() {
        return "PartRegistration{" + key + Arrays.toString(aliases) + ", " + requirement + "}";
    }

}
