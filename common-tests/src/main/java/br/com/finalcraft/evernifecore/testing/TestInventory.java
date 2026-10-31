package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.item.ItemMatcher;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.delivery.InventoryDestination;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every player's inventory in memory, as {@code evernifecore:inventory} while the world that installed it lives
 * ({@code Platforms.withInventory(slots)}): {@code slots} slots per player, {@value #MAX_STACK} items of one id per
 * slot. Adding fills the slots of that id first, then empty ones, and hands back what did not fit. Thread-safe.
 */
public final class TestInventory implements InventoryDestination {

    public static final int MAX_STACK = 64;

    private final int slots;
    private final Map<UUID, List<ItemSpec>> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> held = new ConcurrentHashMap<>();

    public TestInventory(int slots) {
        if (slots <= 0) {
            throw new IllegalArgumentException("An inventory has at least 1 slot, not " + slots + ".");
        }
        this.slots = slots;
    }

    public int getSlots() {
        return slots;
    }

    @Override
    public @Nullable ItemSpec deliverItem(ExecutionContext ctx, ItemSpec item) {
        if (ctx.getPlayer() == null) {
            return item;
        }
        int left = add(ctx.getPlayer(), item);
        return left == 0 ? null : item.withAmount(left);
    }

    /** Adds {@code item} to {@code player}'s inventory; how many did not fit. */
    public synchronized int add(FPlayer player, ItemSpec item) {
        List<ItemSpec> contents = of(player);
        int remaining = item.getAmount();
        for (int i = 0; i < contents.size() && remaining > 0; i++) {
            ItemSpec stack = contents.get(i);
            if (stacks(stack, item) && stack.getAmount() < MAX_STACK) {
                int moved = Math.min(remaining, MAX_STACK - stack.getAmount());
                contents.set(i, stack.withAmount(stack.getAmount() + moved));
                remaining -= moved;
            }
        }
        while (remaining > 0 && contents.size() < slots) {
            int moved = Math.min(remaining, MAX_STACK);
            contents.add(item.withAmount(moved));
            remaining -= moved;
        }
        return remaining;
    }

    /** Fills every free slot of {@code player} with full stacks of {@code materialId}, as a full inventory. */
    public synchronized void fill(FPlayer player, String materialId) {
        List<ItemSpec> contents = of(player);
        while (contents.size() < slots) {
            contents.add(ItemSpec.of(materialId, MAX_STACK));
        }
    }

    @Override
    public synchronized int count(FPlayer player, ItemMatcher matcher) {
        int held = 0;
        for (ItemSpec stack : of(player)) {
            if (matcher.matches(stack)) {
                held += stack.getAmount();
            }
        }
        return held;
    }

    @Override
    public synchronized boolean take(FPlayer player, ItemMatcher matcher, int amount) {
        if (count(player, matcher) < amount) {
            return false;
        }
        List<ItemSpec> contents = of(player);
        int missing = amount;
        for (int i = contents.size() - 1; i >= 0 && missing > 0; i--) {
            ItemSpec stack = contents.get(i);
            if (!matcher.matches(stack)) {
                continue;
            }
            int taken = Math.min(missing, stack.getAmount());
            missing -= taken;
            if (taken == stack.getAmount()) {
                contents.remove(i);
            } else {
                contents.set(i, stack.withAmount(stack.getAmount() - taken));
            }
        }
        return true;
    }

    /** A copy of {@code player}'s stacks, in slot order. */
    @Override
    public synchronized List<ItemSpec> getContents(FPlayer player) {
        return Collections.unmodifiableList(new ArrayList<>(of(player)));
    }

    /** The stack at the held slot ({@link #hold}, the first one by default); null when that slot is empty. */
    @Override
    public synchronized @Nullable ItemSpec getHeld(FPlayer player) {
        List<ItemSpec> contents = of(player);
        int slot = held.getOrDefault(player.getUniqueId(), 0);
        return slot < contents.size() ? contents.get(slot) : null;
    }

    @Override
    public synchronized int roomFor(FPlayer player, ItemSpec item) {
        List<ItemSpec> contents = of(player);
        int room = (slots - contents.size()) * MAX_STACK;
        for (ItemSpec stack : contents) {
            if (stacks(stack, item)) {
                room += MAX_STACK - stack.getAmount();
            }
        }
        return room;
    }

    /** Makes the stack at {@code slot} (in {@link #getContents} order) the one {@code player} holds. */
    public synchronized void hold(FPlayer player, int slot) {
        held.put(player.getUniqueId(), slot);
    }

    /** Puts {@code item} in a new slot of its own, apart from equal stacks - a second stack of the same item. */
    public synchronized void addSeparately(FPlayer player, ItemSpec item) {
        List<ItemSpec> contents = of(player);
        if (contents.size() >= slots) {
            throw new IllegalStateException("The inventory of " + player.getName() + " is full: " + slots + " slots.");
        }
        contents.add(item);
    }

    private static boolean stacks(ItemSpec stack, ItemSpec item) {
        return stack.getMaterialId().equalsIgnoreCase(item.getMaterialId()) && stack.getDisplayName().equals(item.getDisplayName())
                && stack.getLore().equals(item.getLore());
    }

    public synchronized void clear(FPlayer player) {
        byPlayer.remove(player.getUniqueId());
        held.remove(player.getUniqueId());
    }

    private List<ItemSpec> of(FPlayer player) {
        return byPlayer.computeIfAbsent(player.getUniqueId(), uuid -> new ArrayList<>());
    }
}
