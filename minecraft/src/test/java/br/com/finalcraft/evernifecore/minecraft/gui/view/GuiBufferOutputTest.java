package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two questions every write on a screen is decided by.
 *
 * <p>{@code isSameOutput} is the oracle the whole diff machinery rests on, and every render test that
 * counts writes trusts it - so it is asked directly here. An oracle that called two different slots
 * the same would make those tests pass by never noticing anything.</p>
 */
class GuiBufferOutputTest {

    @TempDirNobodyCleans
    Path tempDir;

    private GuiTestWorld world;

    @BeforeEach
    void setup() {
        world = GuiTestWorld.installWithItemMetadata(tempDir);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    // A container answers an empty slot in three different ways depending on the server and on how the
    // slot came to be empty. All three are the same nothing, and a screen that repainted between them
    // would write every tick for no reason a player could see.
    @Test
    void theThreeWaysOfHoldingNothingAreOneAnswer() {
        ItemStack air = new ItemStack(Material.AIR);
        ItemStack countedToZero = new ItemStack(Material.PAPER);
        countedToZero.setAmount(0);

        assertTrue(GuiBuffer.isEmpty(null));
        assertTrue(GuiBuffer.isEmpty(air));
        assertTrue(GuiBuffer.isEmpty(countedToZero), "a stack of nothing is nothing, whatever it says it is");

        assertTrue(GuiBuffer.isSameOutput(null, air));
        assertTrue(GuiBuffer.isSameOutput(air, countedToZero));
        assertTrue(GuiBuffer.isSameOutput(countedToZero, null));
    }

    @Test
    void anEmptySlotAndAFilledOneAreNeverTheSameOutput() {
        ItemStack paper = new ItemStack(Material.PAPER);

        assertFalse(GuiBuffer.isSameOutput(null, paper));
        assertFalse(GuiBuffer.isSameOutput(paper, null));
        assertFalse(GuiBuffer.isSameOutput(new ItemStack(Material.AIR), paper));
    }

    // isSimilar ignores the amount on purpose, which is why the oracle cannot be isSimilar: a counter
    // that only ever changes the stack size is the commonest icon there is.
    @Test
    void aStackThatOnlyChangedItsCountHasToBeRepainted() {
        assertTrue(GuiBuffer.isSameOutput(new ItemStack(Material.PAPER, 3), new ItemStack(Material.PAPER, 3)));
        assertFalse(GuiBuffer.isSameOutput(new ItemStack(Material.PAPER, 3), new ItemStack(Material.PAPER, 4)));
        assertFalse(GuiBuffer.isSameOutput(new ItemStack(Material.PAPER, 3), new ItemStack(Material.STONE, 3)));
    }

    @Test
    void aStackThatOnlyChangedItsNameHasToBeRepainted() {
        ItemStack plain = new ItemStack(Material.PAPER);
        ItemStack named = named(new ItemStack(Material.PAPER), "§aPage 2");

        assertFalse(GuiBuffer.isSameOutput(plain, named), "the name is the whole icon, as far as a reader "
                + "is concerned");
        assertTrue(GuiBuffer.isSameOutput(named, named(new ItemStack(Material.PAPER), "§aPage 2")));
        assertFalse(GuiBuffer.isSameOutput(named, named(new ItemStack(Material.PAPER), "§aPage 3")));
    }

    private static ItemStack named(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

}
