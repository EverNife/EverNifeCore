package br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime;

/**
 * One thing a runtime either can or cannot do with items, asked once by {@link ItemRuntime#probe()}.
 *
 * <p>A probe answers "can this runtime do X", never "which version is this". Version is the primary
 * ladder and it is enough almost everywhere; a probe exists only where the version lies - a bare JVM
 * that reports no version at all, a test double that answers no metadata, a backport that ships a
 * capability its version never had.</p>
 */
public enum ItemProbe {

    /** {@code ItemStack.getItemMeta()} answers an object instead of null or an exception. */
    ITEM_META("this runtime answers no item metadata"),

    /** The NBT api loaded and can read an item's tag. */
    NBT("this runtime has no working NBT access"),

    /** SNBT text can be parsed into a tag - a backport may have this even on an ancient version. */
    SNBT_IO("this runtime cannot parse SNBT text"),

    /** The item-component api of 1.20.5 and up answers. */
    COMPONENTS("this runtime has no item components"),

    /** The enchantment registry resolves a namespaced key. */
    ENCHANT_REGISTRY("this runtime has no enchantment registry to resolve a key against");

    private final String absence;

    ItemProbe(String absence) {
        this.absence = absence;
    }

    /** How to say, in a sentence an admin reads, that this runtime does not have it. */
    public String getAbsence() {
        return absence;
    }

}
