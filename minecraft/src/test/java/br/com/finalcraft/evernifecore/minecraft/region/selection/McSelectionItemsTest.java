package br.com.finalcraft.evernifecore.minecraft.region.selection;

import br.com.finalcraft.evernifecore.api.platoverride.item.NativeItem;
import br.com.finalcraft.evernifecore.minecraft.loader.imp.McPlatform;
import br.com.finalcraft.evernifecore.minecraft.region.visual.McParticleOutlineRenderer;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.region.selection.ItemSpec;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import br.com.finalcraft.evernifecore.testing.minecraft.ItemWorld;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The selection tool handed out on Bukkit: built from the spec or taken from the plugin, always marked. */
class McSelectionItemsTest {

    /**
     * A mark kept beside the items, by identity: the real one lives in the item's nbt, which needs a live server.
     * Like the real one on a plain stack, it marks a copy and leaves the stack it was handed untouched.
     */
    private static final class RememberedMark implements McSelectionItems.ItemMark {
        final Set<ItemStack> marked = Collections.newSetFromMap(new IdentityHashMap<>());

        @Override
        public ItemStack mark(ItemStack item) {
            ItemStack copy = item.clone();
            marked.add(copy);
            return copy;
        }

        @Override
        public boolean isMarked(ItemStack item) {
            return marked.contains(item);
        }
    }

    /** A player whose inventory keeps what it was given. */
    private static Player holder(List<ItemStack> given) {
        AtomicReference<Player> self = new AtomicReference<>();
        PlayerInventory inventory = Doubles.of(PlayerInventory.class)
                .on("addItem", args -> {
                    for (Object item : (Object[]) args[0]) {
                        given.add((ItemStack) item);
                    }
                    return new HashMap<Integer, ItemStack>();
                })
                .build();
        Player player = Doubles.of(Player.class)
                .returning("getName", "Steve")
                .returning("isOnline", true)
                .returning("getInventory", inventory)
                .on("getPlayer", args -> self.get())
                .on("getLocation", args -> new Location(ItemWorld.world("world"), 0, 64, 0))
                .on("getWorld", args -> ItemWorld.world("world"))
                .build();
        self.set(player);
        return player;
    }

    @Test
    void theToolCarriesTheSpecNameLoreAndMark() {
        try (ECoreTestWorld platform = Platforms.lenient().install(); ItemWorld items = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            RememberedMark mark = new RememberedMark();
            List<ItemStack> given = new ArrayList<>();

            new McSelectionItems(mark).give(FCBukkitUtil.adapt(holder(given)), ItemSpec.DEFAULT_TOOL);

            assertEquals(1, given.size());
            ItemStack tool = given.get(0);
            assertEquals(Material.GOLDEN_HOE, tool.getType());
            assertEquals(ItemSpec.DEFAULT_TOOL.getDisplayName(), tool.getItemMeta().getDisplayName());
            assertEquals(ItemSpec.DEFAULT_TOOL.getLore(), tool.getItemMeta().getLore());
            assertTrue(mark.isMarked(tool));
        }
    }

    @Test
    void aNativeItemIsGivenAsIsPlusTheMark() {
        try (ECoreTestWorld platform = Platforms.lenient().install(); ItemWorld items = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            RememberedMark mark = new RememberedMark();
            List<ItemStack> given = new ArrayList<>();
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta meta = sword.getItemMeta();
            meta.setDisplayName("Claim Blade");
            meta.setLore(Collections.singletonList("Marks the corners of a claim"));
            sword.setItemMeta(meta);

            new McSelectionItems(mark).give(FCBukkitUtil.adapt(holder(given)), ItemSpec.DEFAULT_TOOL.withNativeItem(new NativeItem(sword)));

            ItemStack tool = given.get(0);
            assertNotSame(sword, tool, "the plugin's own stack is not handed out: a clone of it is");
            assertEquals(Material.DIAMOND_SWORD, tool.getType());
            assertEquals("Claim Blade", tool.getItemMeta().getDisplayName(), "nothing the plugin set is rewritten by the spec");
            assertEquals(Collections.singletonList("Marks the corners of a claim"), tool.getItemMeta().getLore());
            assertTrue(mark.isMarked(tool));
            assertFalse(mark.isMarked(sword), "the plugin's original stays unmarked");
        }
    }

    @Test
    void aMarkedItemIsRecognisedAndAPlainOneIsNot() {
        try (ECoreTestWorld platform = Platforms.lenient().install(); ItemWorld items = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            RememberedMark mark = new RememberedMark();
            McSelectionItems selectionItems = new McSelectionItems(mark);
            ItemStack marked = new ItemStack(Material.GOLDEN_HOE);
            marked = mark.mark(marked);
            ItemStack air = new ItemStack(Material.AIR);
            mark.mark(air);

            assertTrue(selectionItems.isSelectionTool(marked));
            assertFalse(selectionItems.isSelectionTool(new ItemStack(Material.GOLDEN_HOE)), "the same material without the mark");
            assertFalse(selectionItems.isSelectionTool(null));
            assertFalse(selectionItems.isSelectionTool(air), "an empty hand is never a tool");
        }
    }

    @Test
    void thePlatformAnswersTheBukkitItemsAndRenderer() {
        try (ECoreTestWorld platform = Platforms.lenient().install(); ItemWorld items = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            McPlatform bukkit = new McPlatform();

            assertTrue(bukkit.getSelectionItems() instanceof McSelectionItems);
            assertTrue(bukkit.getOutlineRenderer() instanceof McParticleOutlineRenderer);
        }
    }

    @Test
    void aLegacyServerGetsTheLegacyName() {
        assertEquals("GOLD_HOE", McSelectionItems.legacyName("golden_hoe"));
        assertEquals("WOOD_AXE", McSelectionItems.legacyName("wooden_axe"));
        assertEquals("DIAMOND_SWORD", McSelectionItems.legacyName("diamond_sword"));
    }
}
