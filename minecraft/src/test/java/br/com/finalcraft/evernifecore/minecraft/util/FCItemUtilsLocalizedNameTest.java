package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.ecnmsutils.NMSUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FCItemUtilsLocalizedNameTest {

    @Test
    void withoutAnNmsBridgeTheNameIsReadOffTheMaterial() {
        assertFalse(NMSUtils.isSupported(), "a unit test runs with no CraftBukkit behind it");
        assertEquals("Diamond Sword", FCItemUtils.getLocalizedName(new ItemStack(Material.DIAMOND_SWORD)));
    }
}
