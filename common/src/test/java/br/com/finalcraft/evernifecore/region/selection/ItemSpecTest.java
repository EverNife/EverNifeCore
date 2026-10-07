package br.com.finalcraft.evernifecore.region.selection;

import br.com.finalcraft.evernifecore.api.platoverride.item.NativeItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ItemSpecTest {

    @Test
    void aNativeItemRidesInsideTheSpec() {
        NativeItem item = new NativeItem();

        ItemSpec carrying = ItemSpec.DEFAULT_TOOL.withNativeItem(item);

        assertSame(item, carrying.getNativeItem());
        assertNull(ItemSpec.DEFAULT_TOOL.getNativeItem(), "the copy carries it; the default stays a description");
        assertEquals(ItemSpec.DEFAULT_TOOL.getMaterialId(), carrying.getMaterialId());
        assertEquals(ItemSpec.DEFAULT_TOOL.getDisplayName(), carrying.getDisplayName());
        assertEquals(ItemSpec.DEFAULT_TOOL.getLore(), carrying.getLore());
        assertNotEquals(ItemSpec.DEFAULT_TOOL, carrying, "a spec with the plugin's item is another spec");
    }
}
