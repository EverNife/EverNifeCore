package br.com.finalcraft.evernifecore.minecraft.region.selection;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.NbtDoor;
import br.com.finalcraft.evernifecore.minecraft.util.FCMaterialUtil;
import br.com.finalcraft.evernifecore.region.selection.ISelectionItems;
import br.com.finalcraft.evernifecore.region.selection.ItemSpec;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/**
 * The selection tool on Bukkit: the plugin's own item when the spec carries one, otherwise an item built from the
 * spec. Either way the core adds one thing - the {@code enc:selection} mark that tells the click listener it is a tool.
 */
public final class McSelectionItems implements ISelectionItems {

    /** How the selection mark is written to and read from an item: the item's nbt on a server, anything else in a test. */
    public interface ItemMark {

        void mark(ItemStack item);

        boolean isMarked(ItemStack item);
    }

    static final String MARK = "enc:selection";

    private final ItemMark mark;

    public McSelectionItems() {
        this(nbtMark());
    }

    public McSelectionItems(ItemMark mark) {
        this.mark = mark;
    }

    @Override
    public void give(FPlayer player, ItemSpec spec) {
        if (!(player instanceof MinecraftFPlayer)) {
            return;
        }
        Player bukkit = ((MinecraftFPlayer<?>) player).getPlayer();
        //a clone keeps whatever the plugin put on its item - the mark is added, nothing is rewritten
        ItemStack tool = spec.getNativeItem() != null ? spec.getNativeItem().getItemStack().clone() : build(spec);
        mark.mark(tool);
        for (ItemStack left : bukkit.getInventory().addItem(tool).values()) {
            bukkit.getWorld().dropItemNaturally(bukkit.getLocation(), left);   //a full inventory still gets its tool
        }
    }

    /** Whether {@code item} is a selection tool this core handed out. */
    public boolean isSelectionTool(@Nullable ItemStack item) {
        return item != null && item.getType() != Material.AIR && mark.isMarked(item);
    }

    static ItemStack build(ItemSpec spec) {
        ItemStack item = new ItemStack(materialOf(spec.getMaterialId()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(spec.getDisplayName());
            meta.setLore(spec.getLore());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** The material behind an agnostic id on this server; a modern id is retried in its pre-1.13 spelling, then the default tool. */
    static Material materialOf(String materialId) {
        String name = materialId.substring(materialId.indexOf(':') + 1);
        Material material = FCMaterialUtil.parseMaterial(name);
        if (material == null) {
            material = FCMaterialUtil.parseMaterial(legacyName(name));
        }
        if (material == null) {
            String fallback = ItemSpec.DEFAULT_TOOL.getMaterialId().substring(ItemSpec.DEFAULT_TOOL.getMaterialId().indexOf(':') + 1);
            ECDebugModule.REGIONS.debug("No material {} on this server - the selection tool falls back to {}.", materialId, fallback);
            material = FCMaterialUtil.parseMaterial(fallback);
            if (material == null) {
                material = FCMaterialUtil.parseMaterial(legacyName(fallback));
            }
        }
        return material;
    }

    /** The pre-1.13 spelling of a tool id: golden_ was gold_ and wooden_ was wood_. */
    static String legacyName(String name) {
        return name.toUpperCase(Locale.ROOT).replace("GOLDEN_", "GOLD_").replace("WOODEN_", "WOOD_");
    }

    private static ItemMark nbtMark() {
        return new ItemMark() {
            @Override
            public void mark(ItemStack item) {
                NbtDoor.custom().modifyBatch(item, nbt -> nbt.setBoolean(MARK, true));
            }

            @Override
            public boolean isMarked(ItemStack item) {
                return NbtDoor.custom().read(item, nbt -> nbt.hasTag(MARK));
            }
        };
    }
}
