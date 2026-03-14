package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;

import java.util.List;

public class ItemDataPartMetadata extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        BsonDocument metaData = BsonDocument.parse(argument);
        return item.withMetadata(metaData);
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        return base_item.getMetadata().equals(other_item.getMetadata());
    }

    @Override
    public List<String> read(ItemStack itemStack, List<String> output) {
        output.add("metadata:" + itemStack.getMetadata().toJson());
        return output;
    }

    @Override
    public int getPriority() {
        return PRIORITY_MOST_EARLY + 1;
    }

    @Override
    public boolean removeSpaces() {
        return true;
    }

    @Override
    public String[] createNames() {
        return new String[]{"metadata"};
    }

}
