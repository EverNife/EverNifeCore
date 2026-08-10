package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synthetic clicks and drags, built the way a server builds them and delivered to the framework's
 * listener.
 *
 * <p>Slots are addressed the way the protocol addresses them: {@code raw}. Raw slot {@code n} of a
 * 27-slot screen is inside the screen, raw slot {@code 30} is in the player's own inventory, and the
 * two do <b>not</b> share a numbering - which is the whole reason the framework is not allowed to
 * read {@code getSlot()}. {@link #clickPlayerInventory(PlayerDouble, int, ClickType, InventoryAction)}
 * is the one exception: it takes a slot of the player's own inventory, in that inventory's own
 * numbering, and converts it - so a test can aim at the exact collision without doing the arithmetic
 * itself.</p>
 */
public final class ClickSimulator {

    private final GuiEventBus events;

    ClickSimulator(GuiEventBus events) {
        this.events = events;
    }

    /** A click on the screen itself, at a raw (0-based) slot of the gui. */
    public InventoryClickEvent click(PlayerDouble player, int rawSlot, ClickType clickType, InventoryAction action) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenView(),
                InventoryType.SlotType.CONTAINER, rawSlot, clickType, action);
        events.getListener().onInventoryClick(event);
        return event;
    }

    /**
     * A number-key click: the same gesture as {@link #click}, plus the hotbar slot the key names.
     *
     * <p>{@code hotbarButton} is an index into the player's own hotbar, {@code 0..8}, which is what the
     * protocol sends and what {@code getHotbarButton()} answers - it is not a slot of either
     * container.</p>
     */
    public InventoryClickEvent clickWithHotbarKey(PlayerDouble player, int rawSlot, ClickType clickType,
                                                  InventoryAction action, int hotbarButton) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenView(),
                InventoryType.SlotType.CONTAINER, rawSlot, clickType, action, hotbarButton);
        events.getListener().onInventoryClick(event);
        return event;
    }

    /** The everyday case: a plain left click that picks nothing up. */
    public InventoryClickEvent leftClick(PlayerDouble player, int rawSlot) {
        return click(player, rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    /**
     * A click on the player's own inventory. {@code playerSlot} is the index that inventory itself uses
     * - {@code 0..8} the hotbar, {@code 9..35} the three rows above it - and it is converted to the raw
     * slot the protocol sends, which is what {@code getSlot()} on the event answers back.
     *
     * <p>The two numberings do not agree, which is exactly why this converts: the player's inventory is
     * sent main-rows-first while it numbers the hotbar first, so its slot {@code 9} rides in the raw
     * slot right after the screen.</p>
     */
    public InventoryClickEvent clickPlayerInventory(PlayerDouble player, int playerSlot, ClickType clickType,
                                                    InventoryAction action) {
        int rawSlot = rawSlotOfPlayerSlot(player, playerSlot);
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenView(),
                InventoryType.SlotType.CONTAINER, rawSlot, clickType, action);
        events.getListener().onInventoryClick(event);
        return event;
    }

    /** The raw slot the protocol sends for a slot of the player's own inventory. */
    public static int rawSlotOfPlayerSlot(PlayerDouble player, int playerSlot) {
        int belowTheScreen = playerSlot >= 9 ? playerSlot - 9 : playerSlot + 27;
        return player.getOpenView().getTopInventory().getSize() + belowTheScreen;
    }

    /**
     * A drag that leaves one item in each of {@code rawSlots}, taken off the stack being dragged.
     *
     * <p>The event carries what each slot ENDS with, so a slot already holding the same item reads its
     * own amount plus one. A slot holding something else reads one: that is what a client that believed
     * the slot was free would send, and refusing it is the framework's job.</p>
     */
    public InventoryDragEvent drag(PlayerDouble player, ItemStack dragged, int... rawSlots) {
        if (dragged.getAmount() < rawSlots.length) {
            throw new IllegalArgumentException("A drag over " + rawSlots.length + " slots leaves one item"
                    + " in each of them, and this stack holds " + dragged.getAmount() + ". Drag a stack"
                    + " that covers them, or name fewer slots - no client sends a slot it has nothing"
                    + " left to put in.");
        }
        InventoryView view = player.getOpenView();
        Map<Integer, ItemStack> ending = new LinkedHashMap<>();
        for (int rawSlot : rawSlots) {
            ItemStack held = view.getItem(rawSlot);
            ItemStack result = dragged.clone();
            result.setAmount(1 + (held != null && held.isSimilar(dragged) ? held.getAmount() : 0));
            ending.put(rawSlot, result);
        }

        int leftOver = dragged.getAmount() - rawSlots.length;
        ItemStack cursor = null;
        if (leftOver > 0) {
            cursor = dragged.clone();
            cursor.setAmount(leftOver);
        }
        InventoryDragEvent event = new InventoryDragEvent(view, cursor, dragged, false, ending);
        events.getListener().onInventoryDrag(event);
        return event;
    }

    /**
     * An even drag: the stack is divided between the slots of {@code rawSlots} that can take it, and
     * what is left stays on the cursor.
     *
     * <p>The map a server hands the event holds what each slot would END with, not what it receives -
     * {@code getNewItems()} on a slot already holding two of the same item reads four, not two - so a
     * test that means to divide a stack has to build it that way.</p>
     *
     * <p>A slot already holding something ELSE is left out of the event entirely, and the share is
     * divided between the rest. A client never drags a stack into a slot that cannot merge with it, and
     * an event that said it did would be saying the item in the way had turned into the dragged one.</p>
     */
    public InventoryDragEvent dragEvenly(PlayerDouble player, ItemStack dragged, int... rawSlots) {
        InventoryView view = player.getOpenView();
        List<Integer> accepting = new ArrayList<>();
        for (int rawSlot : rawSlots) {
            ItemStack held = view.getItem(rawSlot);
            if (held == null || held.getType() == Material.AIR || held.isSimilar(dragged)) {
                accepting.add(rawSlot);
            }
        }

        int share = accepting.isEmpty() ? 0 : dragged.getAmount() / accepting.size();
        Map<Integer, ItemStack> ending = new LinkedHashMap<>();
        for (int rawSlot : accepting) {
            ItemStack held = view.getItem(rawSlot);
            ItemStack result = dragged.clone();
            result.setAmount(share + (held == null ? 0 : held.getAmount()));
            ending.put(rawSlot, result);
        }

        int leftOver = dragged.getAmount() - share * accepting.size();
        ItemStack cursor = null;
        if (leftOver > 0) {
            cursor = dragged.clone();
            cursor.setAmount(leftOver);
        }
        InventoryDragEvent event = new InventoryDragEvent(view, cursor, dragged, false, ending);
        events.getListener().onInventoryDrag(event);
        return event;
    }

}
