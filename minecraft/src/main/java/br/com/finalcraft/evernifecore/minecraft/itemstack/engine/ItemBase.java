package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import br.com.finalcraft.evernifecore.minecraft.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCMaterialUtil;
import br.com.finalcraft.everylibs.util.FCInputReader;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.Validate;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * What a recipe starts from, before a single edit runs.
 *
 * <p>Three shapes, and the difference between them is when the server is needed: a {@link Material}
 * and a stack are data the JVM already holds, while a namespaced identifier is a name only the
 * running server's registry can turn into an item. Keeping the identifier verbatim until then is
 * what lets a recipe be written, passed around and inspected off a server.</p>
 */
public final class ItemBase {

    @Nonnull
    public static ItemBase of(@Nonnull Material material) {
        Validate.notNull(material, "Material can't be null!");
        Validate.isTrue(material != Material.AIR, "Material can't be AIR!");
        return new ItemBase(material, null, null);
    }

    /**
     * A bukkit {@code NAME[:durability]} or a namespaced {@code mod:item}.
     *
     * <p>A bukkit name is checked here and not at build time: a typo in a material is a mistake in
     * the caller's own line, and it is worth failing where that line is, not three calls later. A
     * name that resolves to a material and nothing else is kept as that material, so the base needs
     * no server to resolve. A namespaced name, and one carrying a data value, are kept verbatim -
     * only the running server can turn either into an item.</p>
     *
     * @throws IllegalArgumentException when this server has no such material, or when the name is AIR
     */
    @Nonnull
    public static ItemBase ofIdentifier(@Nonnull String identifier) {
        Validate.notNull(identifier, "Identifier can't be null!");
        String[] split = identifier.split(":", 2);
        boolean namespaced = split.length == 2 && FCInputReader.parseInt(split[1], null) == null;
        if (!namespaced) {
            Material material;
            try {
                material = FCMaterialUtil.parseMaterial(split[0]);
            } catch (Exception | LinkageError noServerToAsk) {
                //off a server the name cannot be judged; the build will resolve it or say why not
                return new ItemBase(null, identifier, null);
            }
            if (material == null) {
                throw new IllegalArgumentException("The identifier '" + identifier + "' is not a material "
                        + "this server has. Write a name from the server's own list (DIAMOND_SWORD), a name "
                        + "with a data value (WOOL:14), or a namespaced identifier a mod registered "
                        + "(mymod:cool_item).");
            }
            if (material == Material.AIR) {
                throw new IllegalArgumentException("AIR is the absence of an item - a recipe cannot be "
                        + "built on it. Name a real material, or start from of(ItemStack).");
            }
            if (split.length == 1) {
                return new ItemBase(material, null, null);
            }
        }
        return new ItemBase(null, identifier, null);
    }

    @Nonnull
    public static ItemBase of(@Nonnull ItemStack itemStack) {
        Validate.notNull(itemStack, "Item can't be null!");
        Validate.isTrue(itemStack.getType() != Material.AIR, "Item can't be AIR!");
        return new ItemBase(null, null, itemStack.clone());
    }

    private final Material material;
    private final String identifier;
    private final ItemStack itemStack;

    private ItemBase(Material material, String identifier, ItemStack itemStack) {
        this.material = material;
        this.identifier = identifier;
        this.itemStack = itemStack;
    }

    /** Whether this base is data the JVM already has, so resolving it needs no server. */
    public boolean isPure() {
        return material != null;
    }

    /** A fresh stack for a materialization to edit - never the caller's own object. */
    @Nonnull
    public ItemStack resolve() {
        if (material != null) {
            return new ItemStack(material);
        }
        if (identifier != null) {
            return FCItemUtils.fromIdentifier(identifier);
        }
        return NMSUtils.get() != null
                ? NMSUtils.get().validateItemStackHandle(itemStack.clone())
                : itemStack.clone();
    }

    @Override
    public String toString() {
        return "ItemBase{" + (material != null ? material.name()
                : identifier != null ? identifier : itemStack.getType().name()) + "}";
    }

}
