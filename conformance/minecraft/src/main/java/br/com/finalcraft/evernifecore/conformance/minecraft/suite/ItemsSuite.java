package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.NbtDoor;
import br.com.finalcraft.evernifecore.minecraft.region.selection.McSelectionItems;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.region.selection.ItemSpec;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What the core writes into an item is still there on the copy the player's inventory keeps - the one thing no
 * headless test sees, because only a real server copies the stack on the way in ({@code NbtDoor} takes a
 * different path on 1.7.10, and from 1.20.5 the tag lives in custom data).
 */
public final class ItemsSuite implements ConformanceSuite {

    private static final String NBT_KEY = "ecctag";

    @Override
    public String getId() {
        return "items";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                PlayerCases.of("wand", ctx, ItemsSuite::wand),
                PlayerCases.of("nbt-line", ctx, ItemsSuite::nbtLine),
                PlayerCases.of("unbreakable", ctx, ItemsSuite::unbreakable));
    }

    private static Verdict wand(Player player) {
        McSelectionItems selectionItems = new McSelectionItems();
        List<ItemStack> before = snapshot(player);
        selectionItems.give(FCBukkitUtil.adapt(player), ItemSpec.DEFAULT_TOOL);
        ItemStack held = given(player, before);
        if (held == null) {
            return Verdict.fail("no item reached the inventory");
        }
        boolean marked = selectionItems.isSelectionTool(held);
        return Verdict.of(marked, "marked=" + marked + " type=" + held.getType() + " class=" + held.getClass().getSimpleName());
    }

    private static Verdict nbtLine(Player player) {
        ItemStack built = ItemEngine.get().transform(new ItemStack(Material.STONE),
                Collections.singletonList("nbt:{" + NBT_KEY + ":42}"));
        int onBuilt = readInt(built, NBT_KEY);
        List<ItemStack> before = snapshot(player);
        player.getInventory().addItem(built);
        ItemStack held = given(player, before);
        int onHeld = held == null ? -1 : readInt(held, NBT_KEY);
        return Verdict.of(onBuilt == 42 && onHeld == 42, "built=" + onBuilt + " held=" + onHeld);
    }

    private static Verdict unbreakable(Player player) {
        ItemStack built = FCItemFactory.from(Material.DIAMOND_SWORD).setUnbreakable().build();
        List<ItemStack> before = snapshot(player);
        player.getInventory().addItem(built);
        ItemStack held = given(player, before);
        boolean onBuilt = isUnbreakable(built);
        boolean onHeld = held != null && isUnbreakable(held);
        return Verdict.of(onBuilt && onHeld, "built=" + onBuilt + " held=" + onHeld);
    }

    /** Below 1.11 the flag is only a tag; from 1.20.5 the tag is custom data, so the metadata is asked. */
    private static boolean isUnbreakable(ItemStack item) {
        if (MCVersion.isLower(MCDetailedVersion.v1_20_R4)) {
            return readInt(item, "Unbreakable") == 1;
        }
        return item.getItemMeta() != null && item.getItemMeta().isUnbreakable();
    }

    private static int readInt(ItemStack item, String key) {
        return NbtDoor.custom().read(item, nbt -> nbt.hasTag(key) ? nbt.getInteger(key) : -1);
    }

    private static List<ItemStack> snapshot(Player player) {
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            contents.add(item == null ? null : item.clone());
        }
        return contents;
    }

    /** The slot that changed since {@code before}, read back from the inventory the server keeps. */
    private static ItemStack given(Player player, List<ItemStack> before) {
        ItemStack[] now = player.getInventory().getContents();
        for (int slot = 0; slot < now.length; slot++) {
            ItemStack was = slot < before.size() ? before.get(slot) : null;
            if (now[slot] != null && now[slot].getType() != Material.AIR && (was == null || was.getAmount() != now[slot].getAmount()
                    || was.getType() != now[slot].getType())) {
                return now[slot];
            }
        }
        return null;
    }
}
