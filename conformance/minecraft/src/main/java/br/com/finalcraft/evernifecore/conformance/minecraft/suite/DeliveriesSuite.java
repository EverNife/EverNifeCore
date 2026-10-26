package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.DeliveryCases;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;
import java.util.List;

/**
 * The pending deliveries on a Bukkit server: a stack of diamonds given by {@code give-item} is in the bot's inventory as
 * Bukkit reads it, and with the 36 main slots full of diamonds the same effect leaves one delivery behind. Adding an
 * item and building a stack from an id changed between 1.7.10 and 1.21; the material name did not.
 */
public final class DeliveriesSuite implements ConformanceSuite {

    private static final String ITEM = "minecraft:diamond";
    private static final int MAIN_SLOTS = 36;

    private final ECPluginData plugin;

    public DeliveriesSuite(ECPluginData plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "deliveries";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                PlayerCases.later("inventory-fits", ctx, player -> PlayerCases.within(
                        DeliveryCases.inventoryFits(plugin, ctx.getPlayerOrNull(), ITEM, 64, inventoryOf(player)), 15_000)),
                PlayerCases.later("inventory-full-goes-pending", ctx, player -> PlayerCases.within(
                        DeliveryCases.inventoryFullGoesPending(plugin, ctx.getPlayerOrNull(), ITEM, inventoryOf(player)), 15_000)));
    }

    private static DeliveryCases.NativeInventory inventoryOf(Player player) {
        return new DeliveryCases.NativeInventory() {
            @Override
            public int count() {
                int held = 0;
                for (ItemStack stack : player.getInventory().getContents()) {
                    if (stack != null && stack.getType() == Material.DIAMOND) {
                        held += stack.getAmount();
                    }
                }
                return held;
            }

            @Override
            public Runnable fill() {
                PlayerInventory inventory = player.getInventory();
                ItemStack[] saved = inventory.getContents().clone();
                for (int slot = 0; slot < MAIN_SLOTS; slot++) {
                    inventory.setItem(slot, new ItemStack(Material.DIAMOND, Material.DIAMOND.getMaxStackSize()));
                }
                return () -> inventory.setContents(saved);
            }
        };
    }
}
