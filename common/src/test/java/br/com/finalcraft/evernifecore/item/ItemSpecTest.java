package br.com.finalcraft.evernifecore.item;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a spec keeps the id it was given: a Hytale asset id is case-sensitive, so no factory changes its case. */
class ItemSpecTest {

    @Test
    void keepsTheCaseOfTheId() {
        ItemSpec stone = ItemSpec.of("hytale:Rock_Stone", "", Collections.<String>emptyList());

        assertEquals("hytale:Rock_Stone", stone.getMaterialId());
        assertEquals(ItemSpec.of("hytale:Rock_Stone", 1).getMaterialId(), stone.getMaterialId(),
                "both factories keep the id as written");
    }

    @Test
    void aMatcherStillComparesAMinecraftIdWithoutCase() {
        ItemSpec wand = ItemSpec.of(" MINECRAFT:STICK ", "Wand", Collections.<String>emptyList());

        assertEquals("MINECRAFT:STICK", wand.getMaterialId(), "only the surrounding blanks go");
        assertTrue(ItemMatcher.of("minecraft:stick").matches(wand));
    }
}
