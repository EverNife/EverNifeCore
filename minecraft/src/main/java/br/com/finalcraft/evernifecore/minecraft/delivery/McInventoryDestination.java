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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * The player's inventory on Bukkit, {@code evernifecore:inventory}: items are built from the {@link ItemSpec} (or
 * cloned from its native item) and added stack by stack; counting and taking read the material, name and lore. Any thread: off the
 * main thread it waits for the main thread to do the work.
 */
public final class McInventoryDestination implements InventoryDestination {

    /** The storage and hotbar slots of a player inventory, on every version; armor and off-hand come after. */
    private static final int MAIN_SLOTS = 36;

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
        ItemSpec described = describe(stack);
        return described != null && matcher.matches(described);
    }

    @Override
    public List<ItemSpec> getContents(FPlayer player) {
        Player bukkit = bukkitOf(player);
        if (bukkit == null) {
            return Collections.emptyList();
        }
        return onMainThread(() -> {
            ItemStack[] contents = bukkit.getInventory().getContents();
            List<ItemSpec> stacks = new ArrayList<>();
            for (int slot = 0; slot < Math.min(MAIN_SLOTS, contents.length); slot++) {
                ItemSpec described = describe(contents[slot]);
                if (described != null) {
                    stacks.add(described);
                }
            }
            return stacks;
        });
    }

    @Override
    @SuppressWarnings("deprecation") //getItemInHand is the one call every version from 1.7.10 to today answers
    public @Nullable ItemSpec getHeld(FPlayer player) {
        Player bukkit = bukkitOf(player);
        return bukkit == null ? null : onMainThread(() -> describe(bukkit.getInventory().getItemInHand()));
    }

    @Override
    public int roomFor(FPlayer player, ItemSpec item) {
        Player bukkit = bukkitOf(player);
        Material material = materialOf(item.getMaterialId());
        if (bukkit == null || (item.getNativeItem() == null && material == null)) {
            return 0;
        }
        return onMainThread(() -> {
            ItemStack template = item.getNativeItem() != null ? item.getNativeItem().getItemStack().clone() : build(item, material);
            int max = Math.max(1, template.getMaxStackSize());
            ItemStack[] contents = bukkit.getInventory().getContents();
            int room = 0;
            for (int slot = 0; slot < Math.min(MAIN_SLOTS, contents.length); slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || stack.getType() == Material.AIR) {
                    room += max;
                } else if (stack.isSimilar(template)) {
                    room += Math.max(0, max - stack.getAmount());
                }
            }
            return room;
        });
    }

    /** The agnostic description of {@code stack} - material, amount, data, name, lore - or null for an empty slot. */
    @SuppressWarnings("deprecation") //getDurability is the data value on every version from 1.7.10 to today
    static @Nullable ItemSpec describe(@Nullable ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return null;
        }
        ItemSpec spec = ItemSpec.of("minecraft:" + stack.getType().name().toLowerCase(Locale.ROOT), stack.getAmount())
                .withData(Math.max(0, stack.getDurability()));
        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        if (meta == null) {
            return spec;
        }
        String name = meta.hasDisplayName() ? meta.getDisplayName() : "";
        List<String> lore = meta.hasLore() && meta.getLore() != null ? meta.getLore() : Collections.<String>emptyList();
        return name.isEmpty() && lore.isEmpty() ? spec : spec.withText(name, lore);
    }

    @SuppressWarnings("deprecation") //the (material, amount, data) constructor exists on every version
    private static ItemStack build(ItemSpec item, Material material) {
        ItemStack stack = new ItemStack(material, 1, (short) Math.max(0, item.getData()));
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
