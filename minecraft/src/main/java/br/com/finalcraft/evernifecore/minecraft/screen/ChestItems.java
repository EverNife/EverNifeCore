package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.platoverride.item.NativeItem;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.minecraft.inventory.ItemStore;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.util.FCMaterialUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.screen.RenderedIcon;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * How the chest renderer turns what a screen describes - an {@link ItemSpec}, a {@link RenderedIcon} - into the
 * {@code ItemStack} a slot shows, and back.
 *
 * <p>An id is a modern vanilla id ({@code minecraft:red_wool}); before 1.13 the colour lives in the data value, so the
 * coloured blocks a screen decorates with are mapped to their legacy material and data here - and nowhere else.</p>
 */
public final class ChestItems {

    private static final String VANILLA = "minecraft:";

    /** In data-value order, which is what a pre-1.13 coloured block reads. */
    private static final List<String> COLORS = Arrays.asList("white", "orange", "magenta", "light_blue", "yellow",
            "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private ChestItems() {

    }

    /**
     * The stack {@code icon} shows: the first of its items this server has, its resolved text and amount, its item-data
     * lines applied. {@code null} for an empty icon. An icon none of whose items exists here is drawn as a barrier (a
     * stone before 1.8), warned once per {@code where}. A chest has no disabled look, so a disabled element is written
     * as one: its name grey unless it has a colour of its own, and {@code reason} appended to the lore in red, a line per
     * line of it.
     */
    @Nullable
    public static ItemStack materialize(@Nullable RenderedIcon icon, boolean enabled, @Nullable String reason,
                                        @Nonnull String where) {
        if (icon == null || icon.isEmpty()) {
            return null;
        }
        ItemSpec primary = icon.getItem();
        ItemStack stack = stackOf(primary);
        for (int i = 0; stack == null && i < icon.getFallbacks().size(); i++) {
            stack = stackOf(icon.getFallbacks().get(i));
        }
        if (stack == null) {
            if (WARNED.add(where + "|" + primary.getMaterialId())) {
                EverNifeCore.getLog().warning("The icon " + where + " asks for [" + primary.getMaterialId() + "]"
                        + (icon.getFallbacks().isEmpty() ? "" : " and its fallbacks " + idsOf(icon.getFallbacks()))
                        + ", and this server has none of them. It is drawn as a placeholder - give the icon a "
                        + "fallback this version knows: Icon.of(ItemSpec first, ItemSpec... fallbacks).");
            }
            stack = new ItemStack(placeholder());
        }
        if (!icon.getDataLines().isEmpty()) {
            stack = ItemEngine.get().transform(stack, icon.getDataLines());
        }
        writeText(stack, enabled ? primary : disabledText(primary, reason));
        if (primary.getNativeItem() == null || primary.getAmount() > 1) {
            stack.setAmount(Math.max(1, primary.getAmount()));
        }
        return stack;
    }

    /**
     * {@code stack} as an {@link ItemSpec} that carries it, with its own name and lore as the spec's text - so drawing
     * it writes back what it already says. {@code null} for an empty slot.
     */
    @Nullable
    public static ItemSpec specOf(@Nullable ItemStack stack) {
        if (ItemStore.isEmpty(stack)) {
            return null;
        }
        ItemSpec spec = ItemSpec.of(idOf(stack.getType()), stack.getAmount()).withNativeItem(new NativeItem(stack.clone()));
        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        if (meta == null) {
            return spec;
        }
        String name = meta.hasDisplayName() ? meta.getDisplayName() : spec.getDisplayName();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? meta.getLore() : spec.getLore();
        return spec.withText(name, lore);
    }

    /** The stack an {@link ItemSpec} carries, or the one its id names on this server; {@code null} when neither. */
    @Nullable
    public static ItemStack stackOf(@Nullable ItemSpec spec) {
        if (spec == null) {
            return null;
        }
        NativeItem nativeItem = spec.getNativeItem();
        if (nativeItem != null) {
            return nativeItem.getItemStack() == null ? null : nativeItem.getItemStack().clone();
        }
        return stackOfId(spec.getMaterialId(), spec.getData());
    }

    /** A filter over stacks, as the filter over {@link ItemSpec}s an editable area takes - {@code denyPlace(stacks(...))}. */
    @Nonnull
    public static Predicate<ItemSpec> stacks(@Nonnull Predicate<ItemStack> filter) {
        return spec -> filter.test(stackOf(spec));
    }

    @Nullable
    private static ItemStack stackOfId(String id, int specData) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        boolean prefixed = id.regionMatches(true, 0, VANILLA, 0, VANILLA.length());
        boolean vanilla = prefixed || id.indexOf(':') < 0;
        String name = (prefixed ? id.substring(VANILLA.length()) : id).toLowerCase(Locale.ROOT); //the legacy colour names below are lower case
        short data = 0;
        if (vanilla && !MCVersion.isHigherEquals(MCVersion.v1_13)) {
            for (String block : new String[]{"_stained_glass_pane", "_stained_glass", "_wool"}) {
                int color = name.endsWith(block) ? COLORS.indexOf(name.substring(0, name.length() - block.length())) : -1;
                if (color >= 0) {
                    data = (short) color;
                    name = block.equals("_wool") ? "wool" : block.substring(1);
                    break;
                }
            }
            if (name.startsWith("golden_")) {
                name = "gold_" + name.substring("golden_".length());
            }
        }
        Material material;
        try {
            material = FCMaterialUtil.parseMaterial(vanilla ? name : id);
            if (material == null && !vanilla) {
                //a hybrid server names a mod's item MODID_ITEM
                material = FCMaterialUtil.parseMaterial(id.replace(':', '_'));
            }
        } catch (RuntimeException | LinkageError unknown) {
            return null;
        }
        if (material == null || material == Material.AIR) {
            return null;
        }
        if (specData >= 0 && !MCVersion.isHigherEquals(MCVersion.v1_13)) {
            data = (short) specData; //from 1.13 the colour is in the id, and the data value is only a tool's damage
        }
        return new ItemStack(material, 1, data);
    }

    private static ItemSpec disabledText(ItemSpec spec, @Nullable String reason) {
        String name = spec.getDisplayName();
        if (name != null && !name.startsWith("§")) {
            name = "§7" + name;
        }
        List<String> lore = new ArrayList<>(spec.getLore());
        if (reason != null && !reason.isEmpty()) {
            for (String line : reason.split("\n")) {
                lore.add("§c" + line);
            }
        }
        return spec.withText(name, lore);
    }

    private static void writeText(ItemStack stack, ItemSpec spec) {
        if (spec.getDisplayName() == null && spec.getLore().isEmpty()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        if (spec.getDisplayName() != null) {
            meta.setDisplayName(spec.getDisplayName());
        }
        if (!spec.getLore().isEmpty()) {
            meta.setLore(spec.getLore());
        }
        stack.setItemMeta(meta);
    }

    private static Material placeholder() {
        Material barrier = Material.getMaterial("BARRIER");
        return barrier != null ? barrier : Material.STONE;
    }

    private static String idOf(Material material) {
        return VANILLA + material.name().toLowerCase(Locale.ROOT);
    }

    private static String idsOf(List<ItemSpec> specs) {
        StringBuilder ids = new StringBuilder();
        for (ItemSpec spec : specs) {
            ids.append(ids.length() == 0 ? "[" : ", [").append(spec.getMaterialId()).append(']');
        }
        return ids.toString();
    }

}
