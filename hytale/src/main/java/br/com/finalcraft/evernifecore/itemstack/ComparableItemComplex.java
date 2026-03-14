package br.com.finalcraft.evernifecore.itemstack;

import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;

import java.util.Objects;

public class ComparableItemComplex extends ComparableItem {

    protected final BsonDocument metadata;

    protected BsonDocument extractMedata(ItemStack itemStack){
        try {
            ItemStack safeClone = FCItemFactory.from(itemStack).build();
            BsonDocument metadata = safeClone.getMetadata();
            return metadata;
        }catch (Exception ignored){

        }
        return new BsonDocument();
    }

    protected BsonDocument extractMedata(ComparableItem comparableItem){
        if (comparableItem instanceof ComparableItemComplex){
            return ((ComparableItemComplex) comparableItem).getMetadata();
        }
        return extractMedata(comparableItem.getItemStack());
    }

    public ComparableItemComplex(BlockType block) {
        super(block);
        this.metadata = extractMedata(this.getItemStack());
    }

    public ComparableItemComplex(ItemStack itemStack) {
        super(itemStack);
        this.metadata = extractMedata(this.getItemStack());
    }

    public ComparableItemComplex(ItemStack itemStack, String itemId) {
        super(itemStack, itemId);
        this.metadata = extractMedata(this.getItemStack());
    }

    public ComparableItemComplex(String itemId) {
        super(itemId);
        this.metadata = extractMedata(this.getItemStack());
    }

    public ComparableItemComplex(ItemStack itemStack, String itemId, BsonDocument metadata) {
        super(itemStack, itemId);
        this.metadata = metadata;
    }

    public ComparableItemComplex(String itemId, BsonDocument metadata) {
        super(itemId);
        this.metadata = metadata;
    }

    public BsonDocument getMetadata() {
        return metadata;
    }

    @Override
    public boolean match(ItemStack itemStack) {
        return super.match(itemStack) && this.metadata.equals(extractMedata(itemStack));
    }

    @Override
    public boolean match(BlockType block) {
        return super.match(block); //TODO Compare Block NBT as well
    }

    @Override
    public boolean match(ComparableItem comparableItem) {
        return super.match(comparableItem) && this.metadata.equals(extractMedata(comparableItem));
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ComparableItemComplex == false) return false;
        ComparableItemComplex that = (ComparableItemComplex) o;
        return this.getItemId() == that.getItemId()
                && Objects.equals(this.getMetadata(), that.getMetadata());
    }

    private static final String SEPARATOR = " | ";

    @Override
    public String serialize() {
        String json = itemStack.getMetadata().toJson();

        if (json.isEmpty() || json.equals("{}")) {
            return super.serialize();
        }

        return super.serialize() + SEPARATOR + itemStack.getMetadata().toJson();
    }

    @Loadable
    public static ComparableItemComplex onConfigLoad(ConfigSection section){
        String serialized = section.getString("");

        int idx = serialized.indexOf(SEPARATOR);

        String itemId;
        String metadata;
        if (idx >= 0) {
            itemId = serialized.substring(0, idx);
            metadata = serialized.substring(idx + SEPARATOR.length());
        } else {
            itemId = serialized;
            metadata = "";
        }

        ItemStack itemStack = FCItemFactory.from(itemId).build();

        if (!metadata.isEmpty() && !metadata.equals("{}")) {
            itemStack.withMetadata(
                    BsonDocument.parse(metadata)
            );
        }

        return new ComparableItemComplex(
                itemStack,
                itemId
        );
    }
}
