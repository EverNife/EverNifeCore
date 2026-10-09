package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils.MinecraftIdentifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The grammar half of FCItemUtils.fromMinecraftIdentifier; resolving the registry name needs a live server.
public class FCItemUtilsMinecraftIdentifierTest {

    @Test
    public void registryNameAloneIsOneItemWithNoMeta() {
        MinecraftIdentifier id = MinecraftIdentifier.parse("minecraft:stone");
        assertEquals("minecraft:stone", id.registryName);
        assertEquals(1, id.amount);
        assertEquals(0, id.meta);
        assertNull(id.snbt);
    }

    @Test
    public void readsTheAmount() {
        MinecraftIdentifier id = MinecraftIdentifier.parse("minecraft:stone 32");
        assertEquals(32, id.amount);
        assertEquals(0, id.meta);
        assertNull(id.snbt);
    }

    @Test
    public void readsTheMeta() {
        MinecraftIdentifier id = MinecraftIdentifier.parse("minecraft:wool 2 14");
        assertEquals("minecraft:wool", id.registryName);
        assertEquals(2, id.amount);
        assertEquals(14, id.meta);
        assertNull(id.snbt);
    }

    @Test
    public void snbtKeepsItsOwnSpaces() {
        MinecraftIdentifier id = MinecraftIdentifier.parse("minecraft:stone 1 0 {display:{Name:\"a b\"}, ecctag:7}");
        assertEquals(1, id.amount);
        assertEquals(0, id.meta);
        assertEquals("{display:{Name:\"a b\"}, ecctag:7}", id.snbt);
    }

    @Test
    public void aWordWhereTheAmountGoesNamesTheForm() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MinecraftIdentifier.parse("minecraft:stone many"));
        assertTrue(e.getMessage().contains("'many'") && e.getMessage().contains("<registry name> [amount] [meta] [{snbt}]"), e.getMessage());
    }
}
