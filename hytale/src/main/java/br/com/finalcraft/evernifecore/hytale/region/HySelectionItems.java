package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleUtil;
import br.com.finalcraft.evernifecore.region.selection.ISelectionItems;
import br.com.finalcraft.evernifecore.region.selection.ItemSpec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import jakarta.annotation.Nullable;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;

import java.util.Collections;

/**
 * The selection tool on Hytale: the plugin's own item when the spec carries one, otherwise the item the spec names -
 * a {@code hytale:} id, or {@link #DEFAULT_ITEM} for an id of another platform. Either way the core adds the
 * {@code enc:selection} metadata that tells the region Systems it is a tool. The spec's name and lore are not written:
 * the public API has no display name on an item.
 */
public final class HySelectionItems implements ISelectionItems {

    static final String MARK = "enc:selection";
    /** The tool when the spec names no Hytale item - the default spec names a Minecraft hoe. */
    static final String DEFAULT_ITEM = "Tool_Hoe_Crude";

    @Override
    public void give(FPlayer player, ItemSpec spec) {
        FCHytaleUtil.giveItemsTo(player, Collections.singletonList(tool(spec)));
    }

    static ItemStack tool(ItemSpec spec) {
        ItemStack item = spec.getNativeItem() != null ? spec.getNativeItem().getItemStack() : new ItemStack(itemIdOf(spec.getMaterialId()));
        return item.withMetadata(MARK, BsonBoolean.TRUE);   //a new stack: the plugin's own item stays as it was
    }

    static String itemIdOf(String materialId) {
        int colon = materialId.indexOf(':');
        return colon > 0 && materialId.substring(0, colon).equals(HyRegionContexts.NAMESPACE) ? materialId.substring(colon + 1) : DEFAULT_ITEM;
    }

    /** Whether {@code item} is a selection tool this core handed out. */
    public static boolean isSelectionTool(@Nullable ItemStack item) {
        if (ItemStack.isEmpty(item)) {
            return false;
        }
        BsonDocument metadata = item.getMetadata();
        return metadata != null && metadata.getBoolean(MARK, BsonBoolean.FALSE).getValue();
    }
}
