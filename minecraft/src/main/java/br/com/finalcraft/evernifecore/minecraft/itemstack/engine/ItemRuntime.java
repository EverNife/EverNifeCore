package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import de.tr7zw.changeme.nbtapi.NBT;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * One frozen answer per engine: what THIS runtime can do with items.
 *
 * <p>Probed once, never re-asked. Installing another engine is the only way to get a different
 * answer, which is what a test does to describe a server it is not running on.</p>
 */
public final class ItemRuntime {

    /**
     * Asks the machine we are actually on, once.
     *
     * <p>Every question is asked inside a net, because "not available" arrives in every shape there
     * is: an exception from a server that does not exist, a {@link LinkageError} from a library that
     * will not load, a plain null from a double. All three mean the same thing here - the capability
     * is absent - and none of them may escape into a caller that only wanted to build an item.</p>
     */
    @Nonnull
    public static ItemRuntime probe() {
        MCDetailedVersion version = null;
        try {
            version = MCDetailedVersion.getCurrent();
        } catch (Exception | LinkageError noServer) {
            //a bare JVM: no server to ask, so every server-side capability is absent below
        }

        EnumSet<ItemProbe> found = EnumSet.noneOf(ItemProbe.class);
        if (version != null) {
            ItemStack sample = new ItemStack(Material.STONE);

            try {
                if (sample.getItemMeta() != null) {
                    found.add(ItemProbe.ITEM_META);
                }
            } catch (Exception | LinkageError absent) {
            }

            try {
                NBT.readNbt(sample).getKeys();
                NBT.parseNBT("{}");
                found.add(ItemProbe.NBT);
                //Crucible patches SNBT into 1.7.10, so reaching the tag at all is what proves it
                found.add(ItemProbe.SNBT_IO);
            } catch (Exception | LinkageError absent) {
            }

            if (found.contains(ItemProbe.NBT)) {
                try {
                    NbtDoor.components().snapshot(sample);
                    found.add(ItemProbe.COMPONENTS);
                } catch (Exception | LinkageError absent) {
                }
            }

            try {
                if (Enchantment.getByKey(NamespacedKey.minecraft("unbreaking")) != null
                        || Enchantment.getByKey(NamespacedKey.minecraft("durability")) != null) {
                    found.add(ItemProbe.ENCHANT_REGISTRY);
                }
            } catch (Exception | LinkageError absent) {
            }
        }

        return new ItemRuntime(version, found);
    }

    /** A JVM with no server behind it: the reduced world, where only pure text work is possible. */
    @Nonnull
    public static ItemRuntime bare() {
        return new ItemRuntime(null, EnumSet.noneOf(ItemProbe.class));
    }

    /** A runtime described by hand, which is how a test names the server it wants to stand on. */
    @Nonnull
    public static ItemRuntime of(@Nonnull MCDetailedVersion version, @Nonnull ItemProbe... probes) {
        EnumSet<ItemProbe> set = EnumSet.noneOf(ItemProbe.class);
        set.addAll(Arrays.asList(probes));
        return new ItemRuntime(version, set);
    }

    private final MCDetailedVersion version;
    private final Set<ItemProbe> probes;

    private ItemRuntime(@Nullable MCDetailedVersion version, @Nonnull EnumSet<ItemProbe> probes) {
        this.version = version;
        this.probes = Collections.unmodifiableSet(probes);
    }

    /** Whether there is a server behind this runtime at all. */
    public boolean isLive() {
        return version != null;
    }

    /** The version this runtime reports, or {@code null} when there is no server to report one. */
    @Nullable
    public MCDetailedVersion getVersion() {
        return version;
    }

    public boolean isAtLeast(@Nonnull MCDetailedVersion floor) {
        return version != null && version.isHigherEquals(floor);
    }

    public boolean has(@Nonnull ItemProbe probe) {
        return probes.contains(probe);
    }

    @Nonnull
    public Set<ItemProbe> getProbes() {
        return probes;
    }

    /** How this runtime names itself in a refusal an admin has to act on. */
    @Nonnull
    public String describe() {
        return version == null ? "a JVM with no Minecraft server" : version.name();
    }

    @Override
    public String toString() {
        return "ItemRuntime{" + describe() + ", " + probes + "}";
    }

}
