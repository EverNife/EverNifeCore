package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.DeliveryCases;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.conformance.hytale.HyWorld;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryDestinations;
import br.com.finalcraft.evernifecore.logic.delivery.InventoryDestination;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * The pending deliveries on a Hytale server, the pair the Bukkit suite runs: {@code give-item} of stone reaches the
 * synthetic player's hotbar and storage as the server reads them, and with every slot full of stone the same effect
 * leaves one delivery behind; the core's inventory readers answer what the server holds. Every case runs on the
 * player's world thread, where the inventory is touched.
 */
public final class HyDeliveriesSuite implements ConformanceSuite {

    private static final String ASSET = "Rock_Stone";
    private static final String ITEM = "hytale:" + ASSET;
    /** More than every slot holds, so adding it leaves each slot full. */
    private static final int OVERFLOW = 100_000;

    private final ECPluginData plugin;

    public HyDeliveriesSuite(ECPluginData plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "deliveries";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                onWorld("inventory-fits", ctx, player -> () -> DeliveryCases.inventoryFits(plugin, player, ITEM, 10, inventoryOf(player))),
                onWorld("inventory-full-goes-pending", ctx, player -> () -> DeliveryCases.inventoryFullGoesPending(plugin, player, ITEM,
                        inventoryOf(player))),
                onWorld("inventory-readers", ctx, player -> () -> CompletableFuture.completedFuture(inventoryReaders(player))));
    }

    /**
     * Five stone in the first hotbar slot, made the active one: {@code getHeld} reads them, {@code getContents} lists them
     * and {@code roomFor} has room, which is gone once every slot is full. The slot and the active index go back after.
     */
    private static Verdict inventoryReaders(FPlayer player) {
        PlayerRef ref = HyWorld.player(player);
        Ref<EntityStore> entity = entityOf(ref);
        InventoryComponent.Hotbar hotbar = entity.getStore().getComponent(entity, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return Verdict.fail(ref.getUsername() + " has no hotbar component");
        }
        byte activeBefore = hotbar.getActiveSlot();
        ItemStack slotBefore = hotbar.getInventory().getItemStack((short) 0);
        InventoryDestination inventory = DeliveryDestinations.requireInventory();
        ItemSpec stone = ItemSpec.of(ITEM, 1);
        try {
            hotbar.getInventory().setItemStackForSlot((short) 0, new ItemStack(ASSET, 5));
            hotbar.setActiveSlot((byte) 0, entity, entity.getStore());
            ItemSpec held = inventory.getHeld(player);
            boolean listed = false;
            for (ItemSpec stack : inventory.getContents(player)) {
                listed |= ITEM.equalsIgnoreCase(stack.getMaterialId()) && stack.getAmount() == 5;
            }
            int room = inventory.roomFor(player, stone);
            Runnable restore = inventoryOf(player).fill();
            int roomWhenFull;
            try {
                roomWhenFull = inventory.roomFor(player, stone);
            } finally {
                restore.run();
            }
            boolean heldRight = held != null && ITEM.equalsIgnoreCase(held.getMaterialId()) && held.getAmount() == 5;
            return Verdict.of(heldRight && listed && room > 0 && roomWhenFull == 0, "getHeld=" + held + " getContents-lists-5=" + listed
                    + " roomFor=" + room + " roomFor-when-full=" + roomWhenFull);
        } finally {
            if (ItemStack.isEmpty(slotBefore)) {
                hotbar.getInventory().removeItemStackFromSlot((short) 0);
            } else {
                hotbar.getInventory().setItemStackForSlot((short) 0, slotBefore);
            }
            hotbar.setActiveSlot(activeBefore, entity, entity.getStore());
        }
    }

    private interface PlayerTask {
        Callable<CompletableFuture<Verdict>> on(FPlayer player);
    }

    private static ConformanceCase onWorld(String id, ConformanceContext ctx, PlayerTask task) {
        return HyRegionsSuite.withPlayer(id, ctx, player -> HyWorld.on(HyWorld.worldOf(HyWorld.player(player)), task.on(player))
                .thenCompose(answer -> answer));
    }

    /** The player's hotbar and storage, the container the core's inventory destination adds to; on the world thread. */
    private static DeliveryCases.NativeInventory inventoryOf(FPlayer player) {
        PlayerRef ref = HyWorld.player(player);
        return new DeliveryCases.NativeInventory() {
            @Override
            public int count() {
                int[] held = {0};
                container(ref).forEach((slot, stack) -> {
                    if (!ItemStack.isEmpty(stack) && ASSET.equalsIgnoreCase(stack.getItemId())) {
                        held[0] += stack.getQuantity();
                    }
                });
                return held[0];
            }

            @Override
            public Runnable fill() {
                CombinedItemContainer container = container(ref);
                ItemStack[] saved = new ItemStack[container.getCapacity()];
                for (short slot = 0; slot < saved.length; slot++) {
                    saved[slot] = container.getItemStack(slot);
                }
                container.addItemStack(new ItemStack(ASSET, OVERFLOW));
                return () -> {
                    container.clear();
                    for (short slot = 0; slot < saved.length; slot++) {
                        if (!ItemStack.isEmpty(saved[slot])) {
                            container.setItemStackForSlot(slot, saved[slot]);
                        }
                    }
                };
            }
        };
    }

    private static CombinedItemContainer container(PlayerRef player) {
        Ref<EntityStore> entity = entityOf(player);
        return InventoryComponent.getCombined(entity.getStore(), entity, InventoryComponent.HOTBAR_FIRST);
    }

    private static Ref<EntityStore> entityOf(PlayerRef player) {
        Ref<EntityStore> entity = player.getReference();
        if (entity == null || !entity.isValid()) {
            throw new IllegalStateException(player.getUsername() + " has no entity in the world");
        }
        return entity;
    }
}
