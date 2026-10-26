package br.com.finalcraft.evernifecore.minecraft.delivery;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.delivery.InventoryDestination;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.util.FCMaterialUtil;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * The player's inventory on Bukkit, {@code evernifecore:inventory}: items are built from the {@link ItemSpec} (or
 * cloned from its native item) and added stack by stack; counting and taking read the material. Any thread: off the
 * main thread it waits for the main thread to do the work.
 */
public final class McInventoryDestination implements InventoryDestination {

    @Override
    public @Nullable ItemSpec deliverItem(ExecutionContext ctx, ItemSpec item) {
        FPlayer player = ctx.getPlayer();
        Player bukkit = player == null ? null : bukkitOf(player);
        if (bukkit == null || !bukkit.isOnline()) {
            return item; //nobody to hand it to: all of it waits
        }
        Material material = materialOf(item.getMaterialId());
        if (item.getNativeItem() == null && material == null) {
            EverNifeCore.getLog().warning("No material " + item.getMaterialId() + " on this server: " + item + " for " + bukkit.getName()
                    + " stays pending. Fix the item id in the plugin's file.");
            return item;
        }
        int left = onMainThread(() -> add(bukkit.getInventory(), item, material));
        return left == 0 ? null : item.withAmount(left);
    }

    /** Adds {@code item} a full stack at a time; how many did not fit. */
    private static int add(PlayerInventory inventory, ItemSpec item, @Nullable Material material) {
        ItemStack template = item.getNativeItem() != null ? item.getNativeItem().getItemStack().clone() : build(item, material);
        int remaining = item.getAmount();
        int left = 0;
        while (remaining > 0) {
            ItemStack stack = template.clone();
            int amount = Math.min(remaining, Math.max(1, stack.getMaxStackSize()));
            stack.setAmount(amount);
            remaining -= amount;
            for (ItemStack rest : inventory.addItem(stack).values()) {
                left += rest.getAmount();
            }
        }
        return left;
    }

    @Override
    public int count(FPlayer player, ItemMatcher matcher) {
        Player bukkit = bukkitOf(player);
        if (bukkit == null) {
            return 0;
        }
        return onMainThread(() -> {
            int held = 0;
            for (ItemStack stack : bukkit.getInventory().getContents()) {
                if (matches(stack, matcher)) {
                    held += stack.getAmount();
                }
            }
            return held;
        });
    }

    @Override
    public boolean take(FPlayer player, ItemMatcher matcher, int amount) {
        Player bukkit = bukkitOf(player);
        if (bukkit == null) {
            return false;
        }
        return onMainThread(() -> {
            PlayerInventory inventory = bukkit.getInventory();
            ItemStack[] contents = inventory.getContents();
            int held = 0;
            for (ItemStack stack : contents) {
                if (matches(stack, matcher)) {
                    held += stack.getAmount();
                }
            }
            if (held < amount) {
                return false;
            }
            int missing = amount;
            for (int slot = 0; slot < contents.length && missing > 0; slot++) {
                ItemStack stack = contents[slot];
                if (!matches(stack, matcher)) {
                    continue;
                }
                int taken = Math.min(missing, stack.getAmount());
                missing -= taken;
                if (taken == stack.getAmount()) {
                    inventory.setItem(slot, null);
                } else {
                    stack.setAmount(stack.getAmount() - taken);
                    inventory.setItem(slot, stack);
                }
            }
            return true;
        });
    }

    private static boolean matches(@Nullable ItemStack stack, ItemMatcher matcher) {
        return stack != null && stack.getType() != Material.AIR && matcher.matchesId("minecraft:" + stack.getType().name().toLowerCase(Locale.ROOT));
    }

    private static ItemStack build(ItemSpec item, Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        boolean named = item.getDisplayName() != null && !item.getDisplayName().isEmpty();
        if (meta != null && (named || !item.getLore().isEmpty())) {
            if (named) {
                meta.setDisplayName(item.getDisplayName());
            }
            if (!item.getLore().isEmpty()) {
                meta.setLore(item.getLore());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** The material an agnostic id names on this server, its pre-1.13 spelling tried too; null when none. */
    static @Nullable Material materialOf(String materialId) {
        String name = materialId.substring(materialId.indexOf(':') + 1);
        Material material = FCMaterialUtil.parseMaterial(name);
        return material != null ? material : FCMaterialUtil.parseMaterial(name.toUpperCase(Locale.ROOT)
                .replace("GOLDEN_", "GOLD_").replace("WOODEN_", "WOOD_"));
    }

    private static @Nullable Player bukkitOf(FPlayer player) {
        return player instanceof MinecraftFPlayer ? ((MinecraftFPlayer<?>) player).getPlayer() : null;
    }

    private static <T> T onMainThread(Callable<T> task) {
        try {
            if (Bukkit.isPrimaryThread()) {
                return task.call();
            }
            return Bukkit.getScheduler().callSyncMethod(EverNifeCoreBukkitPlugin.instance, task).get();
        } catch (Exception failure) {
            throw new IllegalStateException("The inventory could not be reached on the main thread: " + failure, failure);
        }
    }
}
