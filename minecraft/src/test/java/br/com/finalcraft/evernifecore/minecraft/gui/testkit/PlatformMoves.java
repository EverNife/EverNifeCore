package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The half of a click no test has: the server actually moving the item.
 *
 * <p>The framework never moves an item itself except in the gestures it takes apart - it says yes or no
 * and reads the result back afterwards - so a test that only fires events proves nothing about the
 * count. This is the other side: what a server does to the two containers and the cursor once every
 * listener has had its say, and it runs only for a click nobody cancelled.</p>
 *
 * <p><b>Every move here is a transfer, drags included.</b> Units are taken off one place and put on
 * another in the same statement, and a stack that leaves the world leaves through {@code ground}, which
 * the caller counts. That is on purpose and it is what makes the anti-duplication property mean
 * something: this class cannot create or destroy an item even if it is wrong about what a gesture does,
 * so any change in the total is the framework's doing and not the fake platform's.</p>
 */
public final class PlatformMoves {

    private PlatformMoves() {

    }

    /**
     * Applies {@code event} the way a server would, unless the framework cancelled it.
     *
     * @param ground where a dropped stack goes - the world, as far as the count is concerned
     */
    public static void applyClick(PlayerDouble player, InventoryClickEvent event, List<ItemStack> ground) {
        if (event.isCancelled()) {
            return;
        }
        InventoryView view = player.getOpenView();
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return;
        }
        Slot slot = at(view, rawSlot);
        Slot cursor = cursorOf(player);

        switch (event.getAction().name()) {
            case "PICKUP_ALL":
            case "PICKUP_SOME":
                transfer(slot, cursor, amountOf(slot.get()));
                break;
            case "PICKUP_HALF":
                transfer(slot, cursor, (amountOf(slot.get()) + 1) / 2);
                break;
            case "PICKUP_ONE":
                transfer(slot, cursor, 1);
                break;
            case "PLACE_ALL":
            case "PLACE_SOME":
                transfer(cursor, slot, amountOf(cursor.get()));
                break;
            case "PLACE_ONE":
                transfer(cursor, slot, 1);
                break;
            case "SWAP_WITH_CURSOR":
                swap(slot, cursor);
                break;
            case "HOTBAR_SWAP":
                swap(slot, at(player.getPlayerInventory(), event.getHotbarButton()));
                break;
            case "MOVE_TO_OTHER_INVENTORY":
                moveToOtherInventory(player, view, rawSlot, slot);
                break;
            case "DROP_ONE_SLOT":
                drop(slot, 1, ground);
                break;
            case "DROP_ALL_SLOT":
                drop(slot, amountOf(slot.get()), ground);
                break;
            case "DROP_ONE_CURSOR":
                drop(cursor, 1, ground);
                break;
            case "DROP_ALL_CURSOR":
                drop(cursor, amountOf(cursor.get()), ground);
                break;
            default:
                //NOTHING, CLONE_STACK on a non-creative player, and everything a server would refuse itself
                break;
        }
    }

    /**
     * Applies a drag the way a server would: every slot takes what the event says it ends with, off the
     * cursor, and what the cursor still holds afterwards is what is left.
     *
     * <p>Off the cursor is the whole point. The event only ever <em>says</em> what each slot will end
     * with, and a cursor that cannot cover it fills the slots it reaches and no more - so an event
     * asking for more than the player is dragging cannot mint the difference here.</p>
     *
     * <p>A cancelled drag is left alone, and the framework has already written the share it accepted -
     * that path is the one that divides a gesture the platform can only take or leave whole.</p>
     */
    public static void applyDrag(PlayerDouble player, InventoryDragEvent event) {
        if (event.isCancelled()) {
            return;
        }
        InventoryView view = player.getOpenView();
        Slot cursor = cursorOf(player);
        for (Map.Entry<Integer, ItemStack> planned : event.getNewItems().entrySet()) {
            Slot slot = at(view, planned.getKey());
            transfer(cursor, slot, amountOf(planned.getValue()) - amountOf(slot.get()));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Transfers
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Moves up to {@code amount} units from {@code from} to {@code to}, as far as one stack of the item
     * goes.
     *
     * @return how many units moved
     */
    private static int transfer(Slot from, Slot to, int amount) {
        ItemStack source = from.get();
        if (isEmpty(source) || amount <= 0) {
            return 0;
        }
        ItemStack target = to.get();
        int held = amountOf(target);
        if (held > 0 && !target.isSimilar(source)) {
            return 0;
        }
        int moved = Math.min(Math.min(amount, source.getAmount()), maxStackSizeOf(source) - held);
        if (moved <= 0) {
            return 0;
        }
        to.set(sized(source, held + moved));
        from.set(sized(source, source.getAmount() - moved));
        return moved;
    }

    private static void swap(Slot one, Slot other) {
        ItemStack held = one.get();
        one.set(other.get());
        other.set(held);
    }

    private static void drop(Slot from, int amount, List<ItemStack> ground) {
        ItemStack source = from.get();
        if (isEmpty(source) || amount <= 0) {
            return;
        }
        int dropped = Math.min(amount, source.getAmount());
        ground.add(sized(source, dropped));
        from.set(sized(source, source.getAmount() - dropped));
    }

    /**
     * A shift-click that the framework left to the platform: the stack goes to the other container, as
     * much of it as fits, and what does not fit stays where it was.
     */
    private static void moveToOtherInventory(PlayerDouble player, InventoryView view, int rawSlot, Slot slot) {
        int topSize = view.getTopInventory().getSize();
        List<Slot> destination = new ArrayList<>();
        if (rawSlot < topSize) {
            SurfaceDouble own = player.getPlayerInventory();
            for (int index = 0; index < own.getSize(); index++) {
                destination.add(at(own, index));
            }
        } else {
            for (int slotInView = 0; slotInView < topSize; slotInView++) {
                destination.add(at(view, slotInView));
            }
        }
        //top up what is already there before opening a new stack, the order vanilla itself uses
        for (Slot target : destination) {
            if (!isEmpty(target.get())) {
                transfer(slot, target, amountOf(slot.get()));
            }
        }
        for (Slot target : destination) {
            if (isEmpty(target.get())) {
                transfer(slot, target, amountOf(slot.get()));
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The three places an item can be
    // -----------------------------------------------------------------------------------------------------------------

    /** One place a stack can sit, as something that can be read and written. */
    private interface Slot {

        ItemStack get();

        void set(ItemStack item);

    }

    /** A raw slot of the open window, which is a slot of whichever of the two containers it lands in. */
    private static Slot at(InventoryView view, int rawSlot) {
        return new Slot() {
            @Override
            public ItemStack get() {
                return view.getItem(rawSlot);
            }

            @Override
            public void set(ItemStack item) {
                view.setItem(rawSlot, item);
            }
        };
    }

    /** A slot of a container addressed by its own index, which is how a hotbar button names one. */
    private static Slot at(SurfaceDouble inventory, int index) {
        return new Slot() {
            @Override
            public ItemStack get() {
                return index < 0 || index >= inventory.getSize() ? null : inventory.getItem(index);
            }

            @Override
            public void set(ItemStack item) {
                if (index >= 0 && index < inventory.getSize()) {
                    inventory.placeWithoutRecording(index, item);
                }
            }
        };
    }

    private static Slot cursorOf(PlayerDouble player) {
        return new Slot() {
            @Override
            public ItemStack get() {
                return player.getCursor();
            }

            @Override
            public void set(ItemStack item) {
                player.holding(item);
            }
        };
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Stacks
    // -----------------------------------------------------------------------------------------------------------------

    private static int maxStackSizeOf(ItemStack item) {
        try {
            int max = item.getMaxStackSize();
            if (max > 0) {
                return max;
            }
        } catch (Throwable unanswerable) {
            //a server that cannot measure an item still lets a player move it
        }
        return 64;
    }

    private static int amountOf(ItemStack item) {
        return isEmpty(item) ? 0 : item.getAmount();
    }

    private static ItemStack sized(ItemStack item, int amount) {
        if (isEmpty(item) || amount <= 0) {
            return null;
        }
        ItemStack sized = item.clone();
        sized.setAmount(amount);
        return sized;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getAmount() <= 0 || item.getType() == Material.AIR;
    }

}
