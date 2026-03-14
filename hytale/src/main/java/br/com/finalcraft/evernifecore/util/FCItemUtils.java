package br.com.finalcraft.evernifecore.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import jakarta.annotation.Nonnull;

import java.util.Objects;

public class FCItemUtils {

    public static @Nonnull String getLocalizedName(ItemStack itemStack){
        return getLocalizedName(itemStack.getItemId());
    }

    public static @Nonnull String getLocalizedName(String itemId){
        Objects.requireNonNull(itemId, "itemId cannot be null!");

        if (itemId.isEmpty()){
            return itemId;
        }

        Item itemConfig = Item.getAssetMap().getAsset(itemId);
        if (itemConfig != null) {
            try {
                Message itemName = Message.translation(itemConfig.getTranslationKey());
                String itemNameText = itemName.getRawText();
                if (itemNameText != null && !itemNameText.isEmpty() && !itemNameText.startsWith("item.") && !itemNameText.contains("com.hypixel")) {
                    return itemNameText;
                }
            } catch (Exception ignored) {

            }
        }

        try {
            Message itemName = Message.translation("item." + itemId);
            String itemNameText = itemName.getRawText();
            if (itemNameText != null && !itemNameText.isEmpty() && !itemNameText.startsWith("item.") && !itemNameText.contains("com.hypixel")) {
                return itemNameText;
            }
        } catch (Exception ignored) {

        }

        return itemId;
    }

}
