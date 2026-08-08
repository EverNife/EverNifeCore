package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handing a player an item they have no room for.
 *
 * <p>The overflow is the whole point: the amount that fit and the amount that did not have to add up to
 * what was handed over, or the player is out of items and nobody can say where they went. The ground is
 * where the excess goes, and the caller who does not want that says so.</p>
 *
 * <p>The last test drives it through a screen closing, which is the only caller inside the framework and
 * the one whose failure is silent - the close swallows whatever the return path throws and writes a log
 * line, so a screen that loses the item still closes cleanly.</p>
 */
class ItemsThatDoNotFitTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;
    private PlayerDouble player;

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        player = world.newPlayer("Steve");
        //the warning the drop path sends is a @FCLocale field only a real enable fills in; unloaded it
        //is null, and the drop never happens because the send throws first
        FCLocaleManager.loadLocale(world.getPluginData(), true, FCBukkitUtil.class);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private static ItemStack stackOf(Material material, int amount) {
        return new ItemStack(material, amount);
    }

    @Test
    void whatFitsGoesIntoTheirInventoryAndNothingHitsTheGround() {
        FCBukkitUtil.giveItemsTo(player.asPlayer(), stackOf(Material.DIAMOND, 5));

        assertEquals(5, player.getPlayerInventory().getItem(0).getAmount());
        assertTrue(player.getDrops().isEmpty(), "there was room, so nothing was thrown away: " + player.getDrops());
    }

    @Test
    void whatDoesNotFitLandsWhereThePlayerIsStanding() {
        player.withFullInventory(stackOf(Material.DIRT, 1));

        FCBukkitUtil.giveItemsTo(player.asPlayer(), stackOf(Material.DIAMOND, 5));

        List<PlayerDouble.Drop> drops = player.getDrops();
        assertEquals(1, drops.size(), "one stack had nowhere to go, so one stack is on the floor: " + drops);
        assertEquals(Material.DIAMOND, drops.get(0).item.getType());
        assertEquals(5, drops.get(0).item.getAmount(), "all five of them, none quietly lost");
        assertSame(player.getStandingAt(), drops.get(0).location, "at their feet, not at spawn");
    }

    /** Only the part that did not fit goes to the ground; a partial fit is not an all-or-nothing drop. */
    @Test
    void onlyTheRemainderIsDropped() {
        player.withFullInventory(stackOf(Material.DIRT, 1));
        //one slot back, holding 60 of the 64 a stack takes
        player.getPlayerInventory().placeWithoutRecording(0, stackOf(Material.DIAMOND, 60));

        FCBukkitUtil.giveItemsTo(player.asPlayer(), stackOf(Material.DIAMOND, 10));

        assertEquals(64, player.getPlayerInventory().getItem(0).getAmount(), "the open stack was topped up first");
        assertEquals(1, player.getDrops().size());
        assertEquals(6, player.getDrops().get(0).item.getAmount(), "and only the six that still had no home fell");
    }

    /** A caller who says not to drop is choosing to destroy the excess, and gets exactly that. */
    @Test
    void aCallerThatRefusesTheDropLosesTheExcessInstead() {
        player.withFullInventory(stackOf(Material.DIRT, 1));

        FCBukkitUtil.giveItemsTo(player.asPlayer(), false, stackOf(Material.DIAMOND, 5));

        assertTrue(player.getDrops().isEmpty(), "nothing may reach the ground: " + player.getDrops());
    }

    @Test
    void closingAScreenWithNoRoomLeftPutsWhatWasCarriedOnTheGround() {
        Gui<LayoutBase> gui = Gui.of(3).debounce(0);
        gui.storage(Slots.of(10, 11, 12, 13)).backedBy(new GenericInventory()).policy(ClickPolicy.EDIT_ALL);
        player.withFullInventory(stackOf(Material.DIRT, 1));
        GuiView view = world.openDetachedAndRegistered(gui, player);
        player.holding(stackOf(Material.DIAMOND, 4));

        world.closeDetached(view);

        assertEquals(1, player.getDrops().size(),
                "the window is gone and their hands are full, so the platform's own answer is the floor: "
                        + player.getDrops());
        assertEquals(4, player.getDrops().get(0).item.getAmount());
    }
}
