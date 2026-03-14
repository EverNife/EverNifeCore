package br.com.finalcraft.evernifecore.itemstack;

import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Salvable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import jakarta.annotation.Nullable;

import java.util.Objects;

public class ComparableItem implements Salvable {

    protected final ItemStack itemStack;
    protected final String itemId;
    protected final @Nullable String blockId;

    protected transient String localized_name;

    public ComparableItem(BlockType block) {
        Item item = block.getItem();
        Objects.requireNonNull(item, "block.getItem() cannot be null inside a ComparableItem!");
        this.itemStack = new ItemStack(item.getId(), 1);
        this.itemId = item.getId();
        this.blockId = block.getId();
    }

    public ComparableItem(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.itemId = itemStack.getItemId();
        this.blockId = itemStack.getItem().getBlockId();
    }

    public ComparableItem(ItemStack itemStack, String itemId) {
        this.itemStack = itemStack;
        this.itemId = itemId;
        this.blockId = Item.getAssetMap().getAsset(itemId).getBlockId();
    }

    public ComparableItem(String itemId) {
        this.itemStack = new ItemStack(itemId, 1);
        this.itemId = itemId;
        this.blockId = Item.getAssetMap().getAsset(itemId).getBlockId();
    }

    public boolean match(ItemStack itemStack) {
        return itemStack.getItemId().equals(this.getItemId());
    }

    public boolean match(BlockType block) {
        return block.getId().equals(this.blockId);
    }

    public boolean match(ComparableItem comparableItem) {
        return this.getItemId().equals(comparableItem.getItemId());
    }

    public boolean match(String itemId) {
        return this.getItemId().equals(itemId);
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public String getItemId() {
        return itemId;
    }

    @Nullable
    public String getBlockId() {
        return blockId;
    }

    public String getLocalizedName() {
        if (localized_name == null){
            localized_name = FCItemUtils.getLocalizedName(getItemId());
        }
        return localized_name;
    }

    public String serialize(){
        return itemId;
    }

    public static ComparableItem deserialize(String serializedLine) {
        //This will accept the following patterns:
        // 1. itemId
        return new ComparableItem(serializedLine);
    }

    @Override
    public String toString() {
        return serialize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ComparableItem == false) return false;
        ComparableItem that = (ComparableItem) o;
        return this.getItemId() == that.getItemId();
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId);
    }

    @Override
    public void onConfigSave(ConfigSection section) {
        section.setValue("", serialize());
    }

    @Loadable
    public static ComparableItem onConfigLoad(ConfigSection section){
        return deserialize(section.getString(""));
    }
}
