package br.com.finalcraft.evernifecore.hytale.delivery;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.hytale.action.HyActionContexts;
import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.delivery.InventoryDestination;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The player's inventory on Hytale, {@code evernifecore:inventory}: hotbar first, then the storage. An item is
 * reached by its {@code hytale:} id (the native item of the spec when it carries one); another platform's id cannot
 * be handed out here and stays pending. Any thread: off the player's world thread it waits for that thread.
 */
public final class HyInventoryDestination implements InventoryDestination {

    private static final String PREFIX = HyActionContexts.NAMESPACE + ":";

    @Override
    public @Nullable ItemSpec deliverItem(ExecutionContext ctx, ItemSpec item) {
        FPlayer player = ctx.getPlayer();
        if (!(player instanceof HytaleFPlayer)) {
            return item;
        }
        ItemStack stack;
        if (item.getNativeItem() != null) {
            stack = item.getNativeItem().getItemStack();
            stack = stack.withQuantity(item.getAmount());
        } else if (item.getMaterialId().regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            stack = new ItemStack(item.getMaterialId().substring(PREFIX.length()), item.getAmount());
        } else {
            EverNifeCore.getLog().warning(item + " for " + player.getName() + " is not a hytale: item, so it stays pending. Name it"
                    + " hytale:<item id> in the plugin's file.");
            return item;
        }
        ItemStack toAdd = stack;
        Integer left = onWorldThread((HytaleFPlayer) player, item.getAmount(), container -> {
            ItemStackTransaction added = container.addItemStack(toAdd);
            ItemStack remainder = added.getRemainder();
            return remainder == null || ItemStack.isEmpty(remainder) ? 0 : remainder.getQuantity();
        });
        return left == 0 ? null : item.withAmount(left);
    }

    @Override
    public int count(FPlayer player, ItemMatcher matcher) {
        if (!(player instanceof HytaleFPlayer)) {
            return 0;
        }
        return onWorldThread((HytaleFPlayer) player, 0, container -> {
            int[] held = {0};
            container.forEach((slot, stack) -> {
                if (matches(stack, matcher)) {
                    held[0] += stack.getQuantity();
                }
            });
            return held[0];
        });
    }

    @Override
    public boolean take(FPlayer player, ItemMatcher matcher, int amount) {
        if (!(player instanceof HytaleFPlayer)) {
            return false;
        }
        return onWorldThread((HytaleFPlayer) player, false, container -> {
            List<short[]> slots = new ArrayList<>();
            int[] held = {0};
            container.forEach((slot, stack) -> {
                if (matches(stack, matcher)) {
                    held[0] += stack.getQuantity();
                    slots.add(new short[]{slot, (short) stack.getQuantity()});
                }
            });
            if (held[0] < amount) {
                return false;
            }
            int missing = amount;
            for (short[] slot : slots) {
                if (missing == 0) {
                    break;
                }
                int taken = Math.min(missing, slot[1]);
                container.removeItemStackFromSlot(slot[0], taken);
                missing -= taken;
            }
            return true;
        });
    }

    private static boolean matches(@Nullable ItemStack stack, ItemMatcher matcher) {
        return !ItemStack.isEmpty(stack) && matcher.matches(describe(stack));
    }

    /** A Hytale stack as the agnostic description a matcher reads: its {@code hytale:} id and quantity, no text of its own. */
    private static ItemSpec describe(ItemStack stack) {
        return ItemSpec.of(PREFIX + stack.getItemId(), stack.getQuantity());
    }

    @Override
    public List<ItemSpec> getContents(FPlayer player) {
        if (!(player instanceof HytaleFPlayer)) {
            return Collections.emptyList();
        }
        return onWorldThread((HytaleFPlayer) player, Collections.<ItemSpec>emptyList(), container -> {
            List<ItemSpec> stacks = new ArrayList<>();
            container.forEach((slot, stack) -> {
                if (!ItemStack.isEmpty(stack)) {
                    stacks.add(describe(stack));
                }
            });
            return stacks;
        });
    }

    @Override
    public @Nullable ItemSpec getHeld(FPlayer player) {
        if (!(player instanceof HytaleFPlayer)) {
            return null;
        }
        HytaleFPlayer hytale = (HytaleFPlayer) player;
        World world = hytale.getWorld();
        if (world == null) {
            return null;
        }
        Supplier<ItemSpec> task = () -> {
            Ref<EntityStore> entity = hytale.getPlayerRef() == null ? null : hytale.getPlayerRef().getReference();
            ItemStack inHand = entity == null || !entity.isValid() ? null : InventoryComponent.getItemInHand(entity.getStore(), entity);
            return ItemStack.isEmpty(inHand) ? null : describe(inHand);
        };
        return EverNifeCore.getPlatform().isWorldThread(world.getName()) ? task.get() : CompletableFuture.supplyAsync(task, world).join();
    }

    @Override
    public int roomFor(FPlayer player, ItemSpec item) {
        if (!(player instanceof HytaleFPlayer) || !item.getMaterialId().regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return 0;
        }
        ItemStack template = item.getNativeItem() != null ? item.getNativeItem().getItemStack().withQuantity(1)
                : new ItemStack(item.getMaterialId().substring(PREFIX.length()), 1);
        return onWorldThread((HytaleFPlayer) player, 0, container -> {
            int max = Math.max(1, template.getItem().getMaxStack());
            int room = 0;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (ItemStack.isEmpty(stack)) {
                    room += max;
                } else if (stack.isStackableWith(template)) {
                    room += Math.max(0, max - stack.getQuantity());
                }
            }
            return room;
        });
    }

    /** {@code work} over the player's combined inventory, on the player's world thread; {@code absent} when the player has left. */
    private static <T> T onWorldThread(HytaleFPlayer player, T absent, Function<CombinedItemContainer, T> work) {
        World world = player.getWorld();
        if (world == null) {
            return absent;
        }
        Supplier<T> task = () -> {
            Ref<EntityStore> entity = player.getPlayerRef() == null ? null : player.getPlayerRef().getReference();
            if (entity == null || !entity.isValid()) {
                return absent;
            }
            return work.apply(InventoryComponent.getCombined(entity.getStore(), entity, InventoryComponent.HOTBAR_FIRST));
        };
        if (EverNifeCore.getPlatform().isWorldThread(world.getName())) {
            return task.get();
        }
        return CompletableFuture.supplyAsync(task, world).join();
    }
}
