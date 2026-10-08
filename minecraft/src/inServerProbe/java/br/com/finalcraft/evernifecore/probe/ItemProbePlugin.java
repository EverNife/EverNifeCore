package br.com.finalcraft.evernifecore.probe;

import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.NbtDoor;
import br.com.finalcraft.evernifecore.minecraft.region.selection.McSelectionItems;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.region.selection.ItemSpec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checks, inside a real server, that what the core writes into an item's tag is still there on the copy the
 * player's inventory keeps. Every result is one {@code [ECPROBE-ITEMS]} line, to the sender and the log.
 *
 * <p>{@code /ecprobe items <player>} hands the player three items and reads each one back from the inventory;
 * {@code /ecprobe nbtutil <player>} runs every {@code FCNBTUtil} method against this server; the click listener
 * reports every left or right click with whether the held item reads as a selection tool.</p>
 */
public final class ItemProbePlugin extends JavaPlugin implements Listener {

    private static final String PREFIX = "[ECPROBE-ITEMS] ";
    private static final String NBT_KEY = "ecprobe";
    private static final String NBT_PREFIX = "[ECPROBE-NBTUTIL] ";

    private final McSelectionItems selectionItems = new McSelectionItems();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 2 || !(args[0].equalsIgnoreCase("items") || args[0].equalsIgnoreCase("nbtutil"))) {
            sender.sendMessage("/ecprobe items|nbtutil <player>");
            return true;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            report(sender, "FAIL player " + args[1] + " is not online");
            return true;
        }
        if (args[0].equalsIgnoreCase("nbtutil")) {
            List<String> lines = new NbtUtilProbe(getDataFolder()).run(player);
            int failed = 0;
            for (String line : lines) {
                sender.sendMessage(NBT_PREFIX + line);
                getLogger().info(NBT_PREFIX + line);
                failed += line.startsWith("FAIL") ? 1 : 0;
            }
            report(sender, (failed == 0 ? "PASS" : "FAIL") + " nbtutil summary " + (lines.size() - failed) + "/" + lines.size()
                    + " on " + MCDetailedVersion.getCurrent() + " " + Bukkit.getVersion());
            return true;
        }
        List<String> results = new ArrayList<>();
        results.add(wand(player));
        results.add(nbtLine(player));
        results.add(unbreakable(player));
        int failed = 0;
        for (String result : results) {
            report(sender, result);
            if (result.startsWith("FAIL")) {
                failed++;
            }
        }
        report(sender, (failed == 0 ? "PASS" : "FAIL") + " summary " + (results.size() - failed) + "/" + results.size()
                + " on " + MCDetailedVersion.getCurrent() + " " + Bukkit.getVersion());
        return true;
    }

    private String wand(Player player) {
        List<ItemStack> before = snapshot(player);
        selectionItems.give(FCBukkitUtil.adapt(player), ItemSpec.DEFAULT_TOOL);
        ItemStack held = given(player, before);
        if (held == null) {
            return "FAIL wand no item reached the inventory";
        }
        return (selectionItems.isSelectionTool(held) ? "PASS" : "FAIL") + " wand marked=" + selectionItems.isSelectionTool(held)
                + " type=" + held.getType() + " class=" + held.getClass().getSimpleName();
    }

    private String nbtLine(Player player) {
        ItemStack built = ItemEngine.get().transform(new ItemStack(Material.STONE),
                Collections.singletonList("nbt:{" + NBT_KEY + ":42}"));
        int onBuilt = readInt(built, NBT_KEY);
        List<ItemStack> before = snapshot(player);
        player.getInventory().addItem(built);
        ItemStack held = given(player, before);
        int onHeld = held == null ? -1 : readInt(held, NBT_KEY);
        return (onBuilt == 42 && onHeld == 42 ? "PASS" : "FAIL") + " nbt-line built=" + onBuilt + " held=" + onHeld;
    }

    private String unbreakable(Player player) {
        ItemStack built = FCItemFactory.from(Material.DIAMOND_SWORD).setUnbreakable().build();
        List<ItemStack> before = snapshot(player);
        player.getInventory().addItem(built);
        ItemStack held = given(player, before);
        boolean onBuilt = isUnbreakable(built);
        boolean onHeld = held != null && isUnbreakable(held);
        return (onBuilt && onHeld ? "PASS" : "FAIL") + " unbreakable built=" + onBuilt + " held=" + onHeld;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        getLogger().info(PREFIX + "click action=" + event.getAction() + " item=" + (item == null ? "none" : item.getType())
                + " selectionTool=" + selectionItems.isSelectionTool(item));
    }

    private void report(CommandSender sender, String line) {
        sender.sendMessage(PREFIX + line);
        getLogger().info(PREFIX + line);
    }
}
