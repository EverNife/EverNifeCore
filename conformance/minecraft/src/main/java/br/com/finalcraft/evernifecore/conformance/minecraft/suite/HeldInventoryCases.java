package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.HoldOptions;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.player.state.QuitPolicy;
import br.com.finalcraft.evernifecore.player.state.StateHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code inventory} slot of {@link HeldPlayerState} over real items: a name and lore, armor, and the off hand from 1.9. Held in
 * memory it comes back on release; held persistently, with a window so the stop keeps it instead of giving it back, it survives the
 * matrix's restart and the next join gives it back.
 */
final class HeldInventoryCases {

    private static final String TOKEN_FILE = "held-inventory.token";
    private static final StateHolder HOLDER = StateHolder.of("evernifecore:conformance-held-inventory");
    /** How long the read half waits for the join to give it back, and what checks it meanwhile. */
    private static final int GIVE_BACK_POLLS = 40;
    private static final long POLL_TICKS = 5L;

    private HeldInventoryCases() {
    }

    static ConformanceCase givenBack(ConformanceContext ctx) {
        return PlayerCases.of("held-inventory-real-items-come-back", ctx, player -> {
            FPlayer viewer = FCBukkitUtil.adapt(player);
            IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
            ItemStack[] kept = player.getInventory().getContents();
            ItemStack[] armor = player.getInventory().getArmorContents();
            HeldPlayerState held = HeldPlayerState.of(player.getUniqueId());
            try {
                fill(player);
                String before = describe(player);
                held.hold(HeldPlayerState.INVENTORY, HOLDER, "", p -> access.setInventory(p, ""), HoldOptions.memory(), viewer);
                access.setInventory(viewer, "");
                String during = describe(player);
                boolean released = held.release(HeldPlayerState.INVENTORY, HOLDER, viewer);
                String after = describe(player);
                return Verdict.of(during.isEmpty() && released && after.equals(before), "during=[" + during + "] released=" + released
                        + " before=[" + before + "] after=[" + after + "]");
            } finally {
                player.getInventory().setContents(kept);
                player.getInventory().setArmorContents(armor);
            }
        });
    }

    static ConformanceCase write(ConformanceContext ctx) {
        return PlayerCases.of("held-inventory-write", ctx, player -> {
            FPlayer viewer = FCBukkitUtil.adapt(player);
            IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
            fill(player);
            String before = describe(player);
            HeldPlayerState.of(player.getUniqueId()).hold(HeldPlayerState.INVENTORY, HOLDER, "", p -> access.setInventory(p, ""),
                    HoldOptions.persistent().onQuit(QuitPolicy.keepFor(Duration.ofHours(1))), viewer);
            access.setInventory(viewer, "");
            Files.write(tokenFile(ctx).toPath(), before.getBytes(StandardCharsets.UTF_8));
            return Verdict.of(describe(player).isEmpty(), "held [" + before + "] during=[" + describe(player) + "]");
        });
    }

    static ConformanceCase read(ConformanceContext ctx) {
        return PlayerCases.later("held-inventory-read", ctx, player -> {
            File token = tokenFile(ctx);
            if (!token.isFile()) {
                return CompletableFuture.completedFuture(Verdict.fail("no " + TOKEN_FILE + " - the write half never ran here"));
            }
            String expected = new String(Files.readAllBytes(token.toPath()), StandardCharsets.UTF_8);
            CompletableFuture<Verdict> answer = new CompletableFuture<>();
            poll(player, expected, GIVE_BACK_POLLS, answer);
            return answer;
        });
    }

    private static void poll(Player player, String expected, int left, CompletableFuture<Verdict> answer) {
        String now = describe(player);
        if (now.equals(expected) || left == 0) {
            answer.complete(Verdict.of(now.equals(expected), "expected=[" + expected + "] after-join=[" + now + "]"));
            return;
        }
        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("ECoreConformance"),
                () -> poll(player, expected, left - 1, answer), POLL_TICKS);
    }

    private static File tokenFile(ConformanceContext ctx) {
        return new File(ctx.getDataFolder(), TOKEN_FILE);
    }

    private static void fill(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItem(0, named(Material.IRON_SWORD, 1, "ECC held sword", "first lore line", "second lore line"));
        inventory.setItem(9, new ItemStack(Material.COBBLESTONE, 7));
        inventory.setChestplate(named(Material.IRON_CHESTPLATE, 1, "ECC held chest", "worn"));
        if (MCVersion.isHigherEquals(MCDetailedVersion.v1_9_R1)) {
            inventory.setItem(40, named(Material.GOLD_INGOT, 3, "ECC held off hand", "off hand")); //by index: no getItemInOffHand before 1.9
        }
    }

    private static ItemStack named(Material material, int amount, String name, String... lore) {
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    /** Every slot with an item, in slot order: what comes back has to read the same. */
    private static String describe(Player player) {
        PlayerInventory inventory = player.getInventory();
        List<String> slots = new ArrayList<>();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            slots.addAll(line("" + slot, contents[slot]));
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int slot = 0; slot < armor.length; slot++) {
            slots.addAll(line("armor" + slot, armor[slot]));
        }
        return String.join(" ", slots);
    }

    private static List<String> line(String slot, ItemStack stack) {
        List<String> line = new ArrayList<>();
        if (stack != null && stack.getType() != Material.AIR) {
            ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
            line.add(slot + ":" + stack.getType() + "x" + stack.getAmount() + (meta == null ? "" : ":" + meta.getDisplayName() + ":" + meta.getLore()));
        }
        return line;
    }
}
