package br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime;

import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What a runtime has to offer before a part or an edit is allowed to touch an item.
 *
 * <p>It lives in the registration, outside the part, so a part this server cannot support is never
 * instantiated - and its class stays free to name api this server does not have.</p>
 */
public final class ItemRequirement {

    /** Anything that runs on a plain {@code ItemStack}: no server, no library, no version floor. */
    @Nonnull
    public static ItemRequirement base() {
        return new ItemRequirement(null, EnumSet.noneOf(ItemProbe.class));
    }

    @Nonnull
    public static ItemRequirement atLeast(@Nonnull MCDetailedVersion floor) {
        return new ItemRequirement(floor, EnumSet.noneOf(ItemProbe.class));
    }

    private final MCDetailedVersion floor;
    private final Set<ItemProbe> probes;

    private ItemRequirement(@Nullable MCDetailedVersion floor, @Nonnull EnumSet<ItemProbe> probes) {
        this.floor = floor;
        this.probes = Collections.unmodifiableSet(probes);
    }

    /** The same requirement, also demanding {@code probes}. */
    @Nonnull
    public ItemRequirement with(@Nonnull ItemProbe... probes) {
        EnumSet<ItemProbe> joined = EnumSet.noneOf(ItemProbe.class);
        joined.addAll(this.probes);
        joined.addAll(Arrays.asList(probes));
        return new ItemRequirement(floor, joined);
    }

    public boolean isSatisfiedBy(@Nonnull ItemRuntime runtime) {
        if (floor != null && !runtime.isAtLeast(floor)) {
            return false;
        }
        for (ItemProbe probe : probes) {
            if (!runtime.has(probe)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Why {@code runtime} does not satisfy this, in a sentence that names the gap - never a bare
     * "unsupported". Returns {@code null} when there is no gap to explain.
     */
    @Nullable
    public String explain(@Nonnull ItemRuntime runtime) {
        List<String> gaps = new ArrayList<>();
        if (floor != null && !runtime.isAtLeast(floor)) {
            gaps.add("it needs " + floor.getReleaseFamily() + " or newer and this is " + runtime.describe());
        }
        for (ItemProbe probe : probes) {
            if (!runtime.has(probe)) {
                gaps.add(probe.getAbsence());
            }
        }
        return gaps.isEmpty() ? null : String.join(", and ", gaps);
    }

    @Override
    public String toString() {
        return "ItemRequirement{" + (floor == null ? "base" : floor.name()) + ", " + probes + "}";
    }

}
